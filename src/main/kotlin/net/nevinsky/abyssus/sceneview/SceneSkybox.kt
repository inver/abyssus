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

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import java.io.File
import java.util.concurrent.Executor

/**
 * The scene's skybox, drawn first with depth testing and writing off, following the camera's orientation but not its
 * position, so everything else is always in front of it. Call only on the GL thread with the context current.
 */
class SceneSkybox(executor: Executor, loader: AssetLoader<PreparedSkybox, SkyboxCube>) : Disposable {
    private val assets = SceneAssets(executor, loader)

    val isLoading: Boolean get() = assets.isLoading

    private val program = Shaders.load("skybox")
    private val viewProj = Matrix4()

    /** Draws the skybox named [name] if it is loaded (and starts loading it); nothing for null. */
    fun draw(camera: Camera, name: String?, projectDir: File?) {
        assets.update(projectDir, setOfNotNull(name))
        val cube = name?.let(assets::get) ?: return

        viewProj.set(camera.view)
        viewProj.setTranslation(0f, 0f, 0f)
        viewProj.mulLeft(camera.projection)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        program.bind()
        program.setUniformMatrix("u_viewProj", viewProj)
        cube.draw(program)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        assets.dispose()
        program.dispose()
    }
}
