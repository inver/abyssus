/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.content.Vec3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrEnvironment
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.plugin.sceneview.skybox.SunDirection
import java.io.File

/**
 * The scene's skybox (a cube of faces, a procedural sky or an HDR sky), drawn first with depth testing and writing off,
 * following the camera's orientation but not its position, so everything else is always in front of it. Call only on
 * the GL thread with the context current.
 */
class SceneSkybox(private val assets: AssetView<SkyRenderer>) : Disposable {
    val isLoading: Boolean get() = assets.isLoading

    private val sunDirection = Vector3()

    /**
     * Draws the skybox named [name] if it is loaded (and starts loading it); nothing for null. A procedural sky is lit
     * by the sun toward [sun], and its clouds drift to [timeSeconds] and are drawn with [technique] (null: the asset's).
     */
    fun draw(
        camera: Camera, name: String?, projectDir: File?, sun: Vec3 = SunDirection.DEFAULT, timeSeconds: Double = 0.0,
        technique: CloudTechnique? = null,
    ) {
        assets.update(projectDir, setOfNotNull(name))
        val sky = name?.let(assets::get) ?: return

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        sky.draw(camera, SkyFrame(sunDirection.set(sun.x, sun.y, sun.z), timeSeconds, technique))
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    /** The built sky named [name], or null while it loads or when it failed. */
    fun sky(name: String?): SkyRenderer? = name?.let(assets::get)

    /** Keeps the sky named [name] loading (and a replaced one released) without drawing it, e.g. while a ray frame is shown. */
    fun update(name: String?, projectDir: File?) = assets.update(projectDir, setOfNotNull(name))

    /**
     * The lighting environment of the HDR sky [name], or of a procedural sky with `lightsScene`, once it is built; null
     * for any other sky or while it builds.
     */
    fun environment(name: String?): HdrEnvironment? = when (val sky = name?.let(assets::get)) {
        is HdrSky -> sky.environment
        is ProceduralSky -> sky.lightingEnvironment
        else -> null
    }

    /** The environment a procedural sky's light is fading to and how far it has got; null unless it is fading. */
    fun fadingTo(name: String?): FadingEnvironment? {
        val lighting = (name?.let(assets::get) as? ProceduralSky)?.lighting ?: return null
        val next = lighting.next ?: return null
        return FadingEnvironment(next, lighting.fade)
    }

    /**
     * Moves the sky light of the procedural sky [name] one frame (nothing for any other sky): the sun toward [sun] and
     * the clouds at [timeSeconds] decide when it rebuilds, [deltaSeconds] fades a new result in.
     */
    fun updateLighting(name: String?, sun: Vec3, timeSeconds: Double, visible: Boolean, deltaSeconds: Double) {
        val sky = name?.let(assets::get) as? ProceduralSky ?: return
        sky.updateLighting(sunDirection.set(sun.x, sun.y, sun.z), timeSeconds, visible, deltaSeconds)
    }

    /** Forgets every sky without GL calls: the context they were built in is gone (see [ViewAssets.abandon]). */
    fun abandon() = assets.abandon()

    override fun dispose() {
        assets.dispose()
    }
}

/** The environment a sky light is fading to, and how far ([weight], 0 to 1) it has replaced the current one. */
class FadingEnvironment(val environment: HdrEnvironment, val weight: Float)
