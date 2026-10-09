/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.terrain

import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.editor.scene.FogParams
import net.nevinsky.abyssus.lib.gdx.editor.scene.LightSet
import net.nevinsky.abyssus.lib.gdx.editor.content.Rgba
import net.nevinsky.abyssus.plugin.sceneview.TerrainEntity
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings

/**
 * Draws terrains: splat-blended textures with the scene's ambient, directional and point lights and fog. A small
 * program of its own, since the terrain material is not something the model shaders know.
 */
class TerrainShader(shaders: ShaderStorage) : Disposable {
    private val program = shaders.program("terrain")
    private val normalMatrix = Matrix3()
    private val atlasBindings = ShadowAtlasBindings()

    /** Bound to the sampler units of the layers a terrain does not have, so no sampler is left without a texture. */
    private val blank = Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888).also { it.setColor(1f, 1f, 1f, 1f); it.fill() }, false)

    /** With [irradiance] (a built HDR sky's irradiance cube), terrains take their ambient from it instead of [ambient]. */
    fun draw(camera: Camera, terrains: Collection<TerrainEntity>, ambient: Rgba?, fog: FogParams?, lights: LightSet, irradiance: GLTexture? = null, atlas: ShadowAtlasAttribute? = null) {
        if (terrains.isEmpty()) return
        program.bind()
        program.setUniformMatrix("u_projViewTrans", camera.combined)
        program.setUniformf("u_cameraPos", camera.position)
        val a = ambient
        program.setUniformf("u_ambient", a?.r ?: 0f, a?.g ?: 0f, a?.b ?: 0f)
        // always on its own unit: a samplerCube left on unit 0 would share it with a 2D splat texture, which GL forbids
        program.setUniformi("u_irradiance", IRRADIANCE_UNIT)
        program.setUniformi("u_hasSky", if (irradiance != null) 1 else 0)
        irradiance?.bind(IRRADIANCE_UNIT)
        program.setUniformf("u_fogColor", fog?.color?.r ?: 0f, fog?.color?.g ?: 0f, fog?.color?.b ?: 0f)
        program.setUniformf("u_fogK", fog?.shaderCoefficient ?: 0f)
        setLights(lights)
        (atlas?.atlas ?: blank).bind(SHADOW_UNIT)
        fun record(id: String) = atlas?.records?.firstOrNull { it.lightId == id }
        atlasBindings.bind(program, atlas, lights.directional.map { record(it.entityId) },
            lights.point.map { record(it.entityId) }, lights.spot.map { record(it.entityId) }, SHADOW_UNIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        for (t in terrains) {
            program.setUniformMatrix("u_worldTrans", t.world)
            program.setUniformMatrix("u_normalMatrix", normalMatrix.set(t.world).inv().transpose())
            t.terrain.draw(program, blank)
        }
    }

    private fun setLights(lights: LightSet) {
        val dirs = FloatArray(MAX * 3)
        val dirColors = FloatArray(MAX * 3)
        lights.directional.forEachIndexed { i, d ->
            dirs[i * 3] = d.direction.x; dirs[i * 3 + 1] = d.direction.y; dirs[i * 3 + 2] = d.direction.z
            dirColors[i * 3] = d.color.r; dirColors[i * 3 + 1] = d.color.g; dirColors[i * 3 + 2] = d.color.b
        }
        val pos = FloatArray(MAX * 3)
        val pointColors = FloatArray(MAX * 3)
        val pointRanges = FloatArray(MAX)
        lights.point.forEachIndexed { i, p ->
            pos[i * 3] = p.position.x; pos[i * 3 + 1] = p.position.y; pos[i * 3 + 2] = p.position.z
            pointRanges[i] = p.range
            pointColors[i * 3] = p.color.r; pointColors[i * 3 + 1] = p.color.g; pointColors[i * 3 + 2] = p.color.b
        }
        program.setUniformi("u_numDirectional", lights.directional.size)
        program.setUniform3fv("u_dirDirection", dirs, 0, MAX * 3)
        program.setUniform3fv("u_dirColor", dirColors, 0, MAX * 3)
        program.setUniformi("u_numPoint", lights.point.size)
        program.setUniform3fv("u_pointPosition", pos, 0, MAX * 3)
        program.setUniform3fv("u_pointColor", pointColors, 0, MAX * 3)
        program.setUniform1fv("u_pointRange", pointRanges, 0, MAX)
        val spotPos = FloatArray(MAX * 3)
        val spotDir = FloatArray(MAX * 3)
        val spotColors = FloatArray(MAX * 3)
        val spotRange = FloatArray(MAX)
        val outer = FloatArray(MAX)
        val inner = FloatArray(MAX)
        lights.spot.forEachIndexed { i, s ->
            spotPos[i * 3] = s.position.x; spotPos[i * 3 + 1] = s.position.y; spotPos[i * 3 + 2] = s.position.z
            spotDir[i * 3] = s.direction.x; spotDir[i * 3 + 1] = s.direction.y; spotDir[i * 3 + 2] = s.direction.z
            spotColors[i * 3] = s.color.r; spotColors[i * 3 + 1] = s.color.g; spotColors[i * 3 + 2] = s.color.b
            spotRange[i] = s.range
            outer[i] = s.cone.outerCos; inner[i] = s.cone.innerCos
        }
        program.setUniformi("u_numSpot", lights.spot.size)
        program.setUniform3fv("u_spotPosition", spotPos, 0, MAX * 3)
        program.setUniform3fv("u_spotDirection", spotDir, 0, MAX * 3)
        program.setUniform3fv("u_spotColor", spotColors, 0, MAX * 3)
        program.setUniform1fv("u_spotRange", spotRange, 0, MAX)
        program.setUniform1fv("u_spotOuter", outer, 0, MAX)
        program.setUniform1fv("u_spotInner", inner, 0, MAX)
    }

    override fun dispose() {
        program.dispose()
        blank.dispose()
    }

    private companion object {
        const val MAX = 5 // at least MAX_POINT and MAX_DIRECTIONAL; the array size in terrain.frag
        const val IRRADIANCE_UNIT = 7 // after the splat units (0 to assets.terrain.SPLAT_UNIT)
        const val SHADOW_UNIT = 6 // 2D atlas between splat (0..5) and HDR cube (7)
    }
}
