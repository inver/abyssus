/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview

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
