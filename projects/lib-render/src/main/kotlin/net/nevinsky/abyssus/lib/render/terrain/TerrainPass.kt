/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render.terrain

import net.nevinsky.abyssus.lib.core.util.TERRAIN_SHADOW_TEXTURE_UNIT
import net.nevinsky.abyssus.lib.core.util.TERRAIN_IRRADIANCE_TEXTURE_UNIT
import net.nevinsky.abyssus.lib.core.util.TERRAIN_IRRADIANCE_TO_TEXTURE_UNIT
import net.nevinsky.abyssus.lib.core.util.TERRAIN_SUN_VISIBILITY_TEXTURE_UNIT

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import net.nevinsky.abyssus.lib.core.util.newSolidTexture
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.light.LightProfile
import net.nevinsky.abyssus.lib.render.light.packLights

/** The classpath folder of the terrain programs this module bundles. */
const val RENDER_SHADER_ROOT = "/shader/render"

/**
 * Which terrain program a host draws with. The two differ in what they light with and how they fog and shadow, so each is
 * a program of its own with its own uniform binding; what they share is [TerrainPass].
 */
enum class TerrainVariant(internal val program: String, val profile: LightProfile) {
    /** Several lights, an optional HDR sky and the shadow atlas; fog is a quadratic ramp. */
    EDITOR_MULTI_LIGHT("terrain_editor", LightProfile.EDITOR_MULTI_LIGHT),

    /** One sun with one shadow map; fog is `1 - exp(-(d * density)^gradient)`. */
    GAME_SINGLE_SUN("terrain_game", LightProfile.GAME_SINGLE_SUN),
}

/**
 * Draws terrains with one of the [TerrainVariant] programs: the program, the camera, the frame's ambient light and fog, the
 * variant's lighting, then each terrain's world transform and its splat layers ([net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh.draw]).
 *
 * It creates and disposes its program and, unless the host lends one as [blank], the 1x1 white texture that stands in for
 * a layer a terrain does not have; a lent texture and every texture and map in the inputs stay the host's. Needs a current
 * GL context; call it on the thread that owns that context.
 */
class TerrainPass(shaders: ShaderStorage, val variant: TerrainVariant, blank: Texture? = null) : Disposable {
    private val program: ShaderProgram =
        shaders.withResources(RENDER_SHADER_ROOT, TerrainPass::class.java).program(variant.program)
    private val ownedBlank: Texture? = if (blank == null) newSolidTexture() else null
    private val blank: Texture = blank ?: ownedBlank!!
    private val binding: Binding = when (variant) {
        TerrainVariant.EDITOR_MULTI_LIGHT -> EditorBinding()
        TerrainVariant.GAME_SINGLE_SUN -> GameBinding()
    }

    /** True when [dispose] released the white texture this pass made (false for a lent one). */
    val ownsBlank: Boolean get() = ownedBlank != null

    /** Draws [terrains] seen as [frame] says, lit by [lighting], which must be the variant's own kind. */
    fun draw(frame: FrameInputs, lighting: TerrainLighting, terrains: Collection<TerrainDraw>) {
        if (terrains.isEmpty()) return
        require(frame.profile == variant.profile) { "$variant lights with ${variant.profile}, not ${frame.profile}" }
        program.bind()
        program.setUniformMatrix("u_projViewTrans", frame.viewProjection)
        binding.bindFrame(program, frame, lighting)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        for (terrain in terrains) {
            program.setUniformMatrix("u_worldTrans", terrain.world)
            binding.bindTerrain(program, lighting, terrain)
            terrain.mesh.draw(program, blank)
        }
    }

    override fun dispose() {
        program.dispose()
        ownedBlank?.dispose()
    }

    /** What differs per variant: the frame's uniforms and what is bound for each terrain. */
    private interface Binding {
        fun bindFrame(program: ShaderProgram, frame: FrameInputs, lighting: TerrainLighting)
        fun bindTerrain(program: ShaderProgram, lighting: TerrainLighting, terrain: TerrainDraw) = Unit
    }

    private inner class EditorBinding : Binding {
        private val normalMatrix = Matrix3()
        private val identity = Matrix4()
        private val atlasBindings = ShadowAtlasBindings()

        override fun bindFrame(program: ShaderProgram, frame: FrameInputs, lighting: TerrainLighting) {
            val editor = lighting as? TerrainLighting.Editor
                ?: throw IllegalArgumentException("the editor terrain needs TerrainLighting.Editor")
            program.setUniformf("u_cameraPos", frame.cameraPosition)
            program.setUniformf("u_ambient", frame.ambient[0], frame.ambient[1], frame.ambient[2])
            // always on its own unit: a samplerCube left on unit 0 would share it with a 2D splat texture, which GL forbids
            program.setUniformi("u_irradiance", IRRADIANCE_UNIT)
            program.setUniformi("u_hasSky", if (editor.irradiance != null) 1 else 0)
            editor.irradiance?.bind(IRRADIANCE_UNIT)

            val fading = editor.irradiance != null && editor.fadeIrradiance != null && editor.fade > 0f
            program.setUniformi("u_irradianceTo", IRRADIANCE_TO_UNIT)
            program.setUniformf("u_skyFade", if (fading) editor.fade.coerceAtMost(1f) else 0f)
            if (fading) {
                editor.fadeIrradiance.bind(IRRADIANCE_TO_UNIT)
            }
            val fog = frame.fog
            program.setUniformf("u_fogColor", fog?.r ?: 0f, fog?.g ?: 0f, fog?.b ?: 0f)
            program.setUniformf("u_fogK", fog?.quadraticCoefficient ?: 0f)
            val clip = editor.clipPlane
            program.setUniformi("u_clipEnabled", if (clip != null) 1 else 0)
            if (clip != null) program.setUniformf("u_clipPlane", clip[0], clip[1], clip[2], clip[3])
            bindLights(program, frame)
            bindSunVisibility(program, editor)
            (editor.atlas?.atlas ?: blank).bind(SHADOW_UNIT)
            fun record(id: String) = editor.atlas?.records?.firstOrNull { it.lightId == id }
            val lights = frame.lights
            atlasBindings.bind(
                program, editor.atlas, lights.directional.map { record(it.entityId) },
                lights.point.map { record(it.entityId) }, lights.spot.map { record(it.entityId) }, SHADOW_UNIT
            )
        }

        override fun bindTerrain(program: ShaderProgram, lighting: TerrainLighting, terrain: TerrainDraw) {
            program.setUniformMatrix("u_normalMatrix", normalMatrix.set(terrain.world).inv().transpose())
        }

        private fun bindSunVisibility(program: ShaderProgram, editor: TerrainLighting.Editor) {
            val visibility = editor.sunVisibility
            program.setUniformi("u_dirVisMap", SUN_VISIBILITY_UNIT)
            program.setUniformMatrix("u_dirVisWorldToMap", visibility?.worldToMap ?: identity)
            program.setUniformf("u_dirVisParams", visibility?.edgeFade ?: 0.1f, visibility?.outside ?: 1f)
            program.setUniformi("u_dirVisIndex", visibility?.lightIndex ?: -1)
            visibility?.texture?.bind(SUN_VISIBILITY_UNIT)
        }

        private fun bindLights(program: ShaderProgram, frame: FrameInputs) {
            val a = packLights(frame.lights, MAX)
            program.setUniformi("u_numDirectional", a.directionalCount)
            program.setUniform3fv("u_dirDirection", a.directionalDirection, 0, MAX * 3)
            program.setUniform3fv("u_dirColor", a.directionalColor, 0, MAX * 3)
            program.setUniformi("u_numPoint", a.pointCount)
            program.setUniform3fv("u_pointPosition", a.pointPosition, 0, MAX * 3)
            program.setUniform3fv("u_pointColor", a.pointColor, 0, MAX * 3)
            program.setUniform1fv("u_pointRange", a.pointRange, 0, MAX)
            program.setUniformi("u_numSpot", a.spotCount)
            program.setUniform3fv("u_spotPosition", a.spotPosition, 0, MAX * 3)
            program.setUniform3fv("u_spotDirection", a.spotDirection, 0, MAX * 3)
            program.setUniform3fv("u_spotColor", a.spotColor, 0, MAX * 3)
            program.setUniform1fv("u_spotRange", a.spotRange, 0, MAX)
            program.setUniform1fv("u_spotOuter", a.spotOuter, 0, MAX)
            program.setUniform1fv("u_spotInner", a.spotInner, 0, MAX)
        }
    }

    private inner class GameBinding : Binding {
        private val outsideShadow = Matrix4().setTranslation(OUTSIDE_SHADOW_OFFSET, 0f, 0f)

        override fun bindFrame(program: ShaderProgram, frame: FrameInputs, lighting: TerrainLighting) {
            val sun = lighting as? TerrainLighting.Sun
                ?: throw IllegalArgumentException("the game terrain needs TerrainLighting.Sun")
            program.setUniformf("u_cameraPosition", frame.cameraPosition)
            val fog = frame.fog
            program.setUniformf("u_fogDensity", fog?.density ?: 0f)
            program.setUniformf("u_fogGradient", fog?.gradient ?: 1f)
            program.setUniformf("u_fogColor", fog?.r ?: 0f, fog?.g ?: 0f, fog?.b ?: 0f)
            program.setUniformf("u_lightDirection", sun.towardSun)
            program.setUniformf("u_lightColor", sun.sunR, sun.sunG, sun.sunB)
            program.setUniformf("u_ambient", frame.ambient[0], frame.ambient[1], frame.ambient[2])
            val shadow = sun.shadow
            // without a map, every point lies outside the shadow's clip volume, which the program reads as lit
            program.setUniformMatrix("u_shadowMatrix", shadow?.matrix ?: outsideShadow)
            program.setUniformf("u_shadowTexel", shadow?.texelSize ?: 1f)
            program.setUniformf("u_shadowBias", shadow?.bias ?: 0f)
            program.setUniformi("u_shadowTexture", SHADOW_UNIT)
        }

        override fun bindTerrain(program: ShaderProgram, lighting: TerrainLighting, terrain: TerrainDraw) {
            // the splat layers take the units before the map; bind it again for each terrain, as the layers' binds move the active unit
            ((lighting as TerrainLighting.Sun).shadow?.texture ?: blank).bind(SHADOW_UNIT)
        }
    }


}

internal val MAX = LightProfile.EDITOR_MULTI_LIGHT.let { maxOf(it.maxDirectional, it.maxPoint, it.maxSpot) }
internal const val SHADOW_UNIT = TERRAIN_SHADOW_TEXTURE_UNIT // after the splat units (0 to 5), before the HDR cube
private const val IRRADIANCE_UNIT = TERRAIN_IRRADIANCE_TEXTURE_UNIT
private const val IRRADIANCE_TO_UNIT =
    TERRAIN_IRRADIANCE_TO_TEXTURE_UNIT // the cube a sky light fades to; bound only while fading
private const val SUN_VISIBILITY_UNIT =
    TERRAIN_SUN_VISIBILITY_TEXTURE_UNIT // a directional light's visibility map (cloud shadows); bound only when set
private const val OUTSIDE_SHADOW_OFFSET = 10f // past the clip volume's edge at 1
