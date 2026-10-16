/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.terrain

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.scene.FogParams
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.toVector3
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.frame.RenderFog
import net.nevinsky.abyssus.lib.render.light.DirectionalInput
import net.nevinsky.abyssus.lib.render.light.PointInput
import net.nevinsky.abyssus.lib.render.light.RenderLights
import net.nevinsky.abyssus.lib.render.light.SpotInput
import net.nevinsky.abyssus.lib.render.terrain.TerrainDraw
import net.nevinsky.abyssus.lib.render.terrain.TerrainLighting
import net.nevinsky.abyssus.lib.render.terrain.TerrainPass
import net.nevinsky.abyssus.lib.render.terrain.TerrainVariant
import net.nevinsky.abyssus.plugin.sceneview.TerrainEntity

/**
 * Draws terrains for the scene view: splat-blended textures with the scene's ambient, directional, point and spot lights
 * and fog. The drawing is `lib-render`'s [TerrainPass] (the editor variant); this adapts the view's own types, the camera,
 * the [LightSet] and [FogParams], to its host-neutral inputs.
 */
class TerrainShader(shaders: ShaderStorage) : Disposable {
    private val pass = TerrainPass(shaders, TerrainVariant.EDITOR_MULTI_LIGHT)

    /**
     * With [irradiance] (a built sky's irradiance cube), terrains take their ambient from it instead of [ambient].
     * While a sky light cross-fades, [fadeIrradiance] is the cube it fades to and [fade] (0 to 1) how far it has got.
     * With [sunVisibility] (the cloud shadow map), the directional light it names is multiplied by the map.
     * With a [clipPlane] `(a, b, c, d)` (a planar reflection pass), fragments with `a x + b y + c z + d < 0` are discarded.
     */
    fun draw(
        camera: Camera, terrains: Collection<TerrainEntity>, ambient: Rgba?, fog: FogParams?, lights: LightSet,
        irradiance: GLTexture? = null, atlas: ShadowAtlasAttribute? = null,
        fadeIrradiance: GLTexture? = null, fade: Float = 0f, sunVisibility: DirectionalVisibilityAttribute? = null,
        clipPlane: FloatArray? = null,
    ) {
        if (terrains.isEmpty()) return
        val frame = FrameInputs(
            camera.combined, camera.position, floatArrayOf(ambient?.r ?: 0f, ambient?.g ?: 0f, ambient?.b ?: 0f),
            fog?.let { RenderFog(it.color.r, it.color.g, it.color.b, it.density, it.gradient) },
            renderLights(lights), TerrainVariant.EDITOR_MULTI_LIGHT.profile,
        )
        pass.draw(
            frame, TerrainLighting.Editor(irradiance, atlas, fadeIrradiance, fade, sunVisibility, clipPlane),
            terrains.map { TerrainDraw(it.terrain, it.world) },
        )
    }

    override fun dispose() = pass.dispose()
}

/** The view's lights in `lib-render`'s host-neutral form. */
internal fun renderLights(lights: LightSet) = RenderLights(
    lights.directional.map { DirectionalInput(it.entityId, it.direction.toVector3(), it.color.r, it.color.g, it.color.b) },
    lights.point.map { PointInput(it.entityId, it.position.toVector3(), it.color.r, it.color.g, it.color.b, it.range) },
    lights.spot.map {
        SpotInput(it.entityId, it.position.toVector3(), it.direction.toVector3(), it.color.r, it.color.g, it.color.b, it.range, it.cone.outerCos, it.cone.innerCos)
    },
)
