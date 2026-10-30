/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.fog

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.RenderContext

/**
 * g3d's default shader fogs with `min(dot(d, d) * u_cameraPosition.w, 1)`, and sets `w` from the camera's far plane.
 * This shader overrides `w` so the fog follows the scene's density instead.
 */
class FogShader(renderable: Renderable, config: Config, private val fogCoefficient: () -> Float?) :
    DefaultShader(renderable, config) {

    override fun begin(camera: Camera, context: RenderContext) {
        super.begin(camera, context)
        val k = fogCoefficient() ?: return
        if (program.hasUniform(CAMERA_POSITION)) {
            program.setUniformf(CAMERA_POSITION, camera.position.x, camera.position.y, camera.position.z, k)
        }
    }

    private companion object {
        const val CAMERA_POSITION = "u_cameraPosition"
    }
}

class FogShaderProvider(private val fogCoefficient: () -> Float?) : DefaultShaderProvider() {
    override fun createShader(renderable: Renderable): Shader = FogShader(renderable, config, fogCoefficient)
}
