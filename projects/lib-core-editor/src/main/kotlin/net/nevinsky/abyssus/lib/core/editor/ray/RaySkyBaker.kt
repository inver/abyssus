/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.util.GlStateScope
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.CubeFaceAxes
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.CubeFaceImage
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.EquirectCubeMapping
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RaySkySnapshot

/**
 * Renders a built [SkyRenderer] into a [RaySkySnapshot]: six 90 degree faces drawn with the sky's own shader into an offscreen
 * framebuffer, then resampled into the equirectangular layout ray backends sample. This is the only way to get a
 * procedural sky, which is arbitrary asset GLSL, into a ray texture. GL thread only, inside `GdxRuntime.withContext`;
 * the caller's framebuffer, viewport, depth test and face culling are restored. The result holds display values (not HDR): the sky as
 * the view shows it, with its clouds at [bake]'s `timeSeconds` when asked, never the linear lighting radiance. Every bake
 * gets the next [RaySkySnapshot.revision].
 */
class RaySkyBaker(private val faceSize: Int = 512) {
    private var bakes = 0L

    private class Face(val direction: Vector3, val up: Vector3) {
        val right: Vector3 = Vector3(direction).crs(up).nor()
        val vertical: Vector3 = Vector3(right).crs(direction).nor()
    }

    private val faces = listOf(
        Face(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f)), Face(Vector3(-1f, 0f, 0f), Vector3(0f, 1f, 0f)),
        Face(Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f)), Face(Vector3(0f, -1f, 0f), Vector3(0f, 0f, -1f)),
        Face(Vector3(0f, 0f, 1f), Vector3(0f, 1f, 0f)), Face(Vector3(0f, 0f, -1f), Vector3(0f, 1f, 0f)),
    )

    /**
     * Bakes [sky] for the sun toward [sun]; with [clouds] it draws the sky's clouds as they are at [timeSeconds] (the
     * view's sky clock), without it the bare atmosphere.
     */
    fun bake(sky: SkyRenderer, sun: Vector3, timeSeconds: Double = 0.0, clouds: Boolean = false): RaySkySnapshot {
        val state = GlStateScope(listOf(GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE))
        val buffer = FrameBuffer(Pixmap.Format.RGBA8888, faceSize, faceSize, false)
        val images = ArrayList<ByteArray>(6)
        try {
            val camera = PerspectiveCamera(90f, faceSize.toFloat(), faceSize.toFloat())
            val sunDirection = Vector3(sun.x, sun.y, sun.z)
            for (face in faces) {
                camera.position.setZero(); camera.direction.set(face.direction); camera.up.set(face.up)
                camera.near = .1f; camera.far = 100f; camera.update()
                buffer.begin()
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); Gdx.gl.glDepthMask(false); Gdx.gl.glDisable(GL20.GL_CULL_FACE)
                sky.draw(camera, SkyFrame(sunDirection, timeSeconds, clouds = clouds, bake = true))
                Gdx.gl.glDepthMask(true)
                val pixels = BufferUtils.newByteBuffer(faceSize * faceSize * 4)
                Gdx.gl.glReadPixels(0, 0, faceSize, faceSize, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels)
                buffer.end()
                images += ByteArray(faceSize * faceSize * 4).also { pixels.get(it) }
            }
        } finally {
            buffer.dispose()
            state.restore()
        }
        return equirect(images, ++bakes)
    }

    private val mapping by lazy(LazyThreadSafetyMode.NONE) {
        EquirectCubeMapping(WIDTH, HEIGHT, faces.map { CubeFaceAxes(it.direction, it.right, it.vertical) })
    }

    /**
     * Equirectangular resampling, bilinear within each face: centre column faces -Z and the top row is +Y, as the raster
     * sky does. 8-bit RGBA, because the bake is a display sample and a float image this size would cost 33 MB a bake.
     */
    private fun equirect(images: List<ByteArray>, revision: Long): RaySkySnapshot =
        RaySkySnapshot(WIDTH, HEIGHT, mapping.resample(images.map { CubeFaceImage(faceSize, faceSize, it) }), hdr = false, revision = revision)
}

// Twice the old 1024x512, so a cloud's edge spans several texels at the editor's field of view.
private const val WIDTH = 2048
private const val HEIGHT = WIDTH / 2
