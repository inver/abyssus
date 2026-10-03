/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.assets.loading.SceneAssets
import net.nevinsky.abyssus.assets.sky.PreparedSky
import net.nevinsky.abyssus.assets.sky.Sky
import net.nevinsky.abyssus.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.assets.sky.hdr.HdrSky
import net.nevinsky.abyssus.sceneview.skybox.SunDirection
import java.io.File

/**
 * The scene's skybox (a cube of faces, a procedural sky or an HDR sky), drawn first with depth testing and writing off,
 * following the camera's orientation but not its position, so everything else is always in front of it. Call only on
 * the GL thread with the context current.
 */
class SceneSkybox(private val assets: SceneAssets<PreparedSky, Sky>) : Disposable {
    val isLoading: Boolean get() = assets.isLoading

    private val sunDirection = Vector3()

    /**
     * Draws the skybox named [name] if it is loaded (and starts loading it); nothing for null. A procedural sky is lit
     * by the sun toward [sun].
     */
    fun draw(camera: Camera, name: String?, projectDir: File?, sun: Vec3 = SunDirection.DEFAULT) {
        assets.update(projectDir, setOfNotNull(name))
        val sky = name?.let(assets::get) ?: return

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        sky.draw(camera, sunDirection.set(sun.x, sun.y, sun.z))
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    /** The lighting environment of the HDR sky [name] once it is built; null for any other sky or while it builds. */
    fun environment(name: String?): HdrEnvironment? = (name?.let(assets::get) as? HdrSky)?.environment

    override fun dispose() {
        assets.dispose()
    }
}
