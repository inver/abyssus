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
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RaySkySnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a built [SkyRenderer] into a [RaySkySnapshot]: six 90 degree faces drawn with the sky's own shader into an offscreen
 * framebuffer, then resampled into the equirectangular layout ray backends sample. This is the only way to get a
 * procedural sky, which is arbitrary asset GLSL, into a ray texture. GL thread only, inside `GdxRuntime.withContext`;
 * the caller's framebuffer, viewport and depth state are restored. The result holds display values (not HDR): the sky as
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
        val viewport = BufferUtils.newIntBuffer(16).also { Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, it) }
        val bound = BufferUtils.newIntBuffer(16).also { Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, it) }
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
                Gdx.gl.glDepthMask(true); Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
                val pixels = BufferUtils.newByteBuffer(faceSize * faceSize * 4)
                Gdx.gl.glReadPixels(0, 0, faceSize, faceSize, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels)
                buffer.end()
                images += ByteArray(faceSize * faceSize * 4).also { pixels.get(it) }
            }
        } finally {
            buffer.dispose()
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, bound.get(0))
            Gdx.gl.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3))
        }
        return equirect(images, ++bakes)
    }

    // Which face and where on it every output pixel reads depends only on the face size, so it is worked out once, not per bake.
    private val faceOfPixel by lazy(LazyThreadSafetyMode.NONE) { ByteArray(WIDTH * HEIGHT) }
    private val sourceX by lazy(LazyThreadSafetyMode.NONE) { FloatArray(WIDTH * HEIGHT) }
    private val sourceY by lazy(LazyThreadSafetyMode.NONE) { FloatArray(WIDTH * HEIGHT) }
    private val mapped by lazy(LazyThreadSafetyMode.NONE) {
        java.util.stream.IntStream.range(0, HEIGHT).parallel().forEach { y ->
            val phi = (y + .5f) / HEIGHT * PI.toFloat()
            for (x in 0 until WIDTH) {
                val theta = ((x + .5f) / WIDTH - .5f) * 2f * PI.toFloat()
                val dx = sin(phi) * sin(theta);
                val dy = cos(phi);
                val dz = -sin(phi) * cos(theta)
                val index = when {
                    abs(dx) >= abs(dy) && abs(dx) >= abs(dz) -> if (dx > 0) 0 else 1
                    abs(dy) >= abs(dz) -> if (dy > 0) 2 else 3
                    else -> if (dz > 0) 4 else 5
                }
                val face = faces[index]
                val forward = dx * face.direction.x + dy * face.direction.y + dz * face.direction.z
                val u = (dx * face.right.x + dy * face.right.y + dz * face.right.z) / forward
                val v = (dx * face.vertical.x + dy * face.vertical.y + dz * face.vertical.z) / forward
                // texel centres sit at +.5, so a position between two centres blends them; row 0 is the bottom, as glReadPixels returns
                faceOfPixel[y * WIDTH + x] = index.toByte()
                sourceX[y * WIDTH + x] = ((u + 1f) / 2f * faceSize - .5f).coerceIn(0f, faceSize - 1f)
                sourceY[y * WIDTH + x] = ((v + 1f) / 2f * faceSize - .5f).coerceIn(0f, faceSize - 1f)
            }
        }
        true
    }

    /**
     * Equirectangular resampling, bilinear within each face: centre column faces -Z and the top row is +Y, as the raster
     * sky does. 8-bit RGBA, because the bake is a display sample and a float image this size would cost 33 MB a bake.
     */
    private fun equirect(images: List<ByteArray>, revision: Long): RaySkySnapshot {
        check(mapped)
        val out = ByteArray(WIDTH * HEIGHT * 4)
        java.util.stream.IntStream.range(0, HEIGHT).parallel().forEach { y ->
            for (x in 0 until WIDTH) {
                val pixel = y * WIDTH + x
                val image = images[faceOfPixel[pixel].toInt()]
                val fx = sourceX[pixel]
                val fy = sourceY[pixel]
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val x1 = minOf(x0 + 1, faceSize - 1)
                val y1 = minOf(y0 + 1, faceSize - 1)
                val wx = fx - x0
                val wy = fy - y0
                val a = (y0 * faceSize + x0) * 4
                val b = (y0 * faceSize + x1) * 4
                val c = (y1 * faceSize + x0) * 4
                val d = (y1 * faceSize + x1) * 4
                val i = pixel * 4
                for (channel in 0 until 3) {
                    val top = (image[a + channel].toInt() and 255) * (1f - wx) + (image[b + channel].toInt() and 255) * wx
                    val bottom = (image[c + channel].toInt() and 255) * (1f - wx) + (image[d + channel].toInt() and 255) * wx
                    out[i + channel] = (top * (1f - wy) + bottom * wy + .5f).toInt().toByte()
                }
                out[i + 3] = 255.toByte()
            }
        }
        return RaySkySnapshot(WIDTH, HEIGHT, out, hdr = false, revision = revision)
    }
}

// Twice the old 1024x512, so a cloud's edge spans several texels at the editor's field of view.
private const val WIDTH = 2048
private const val HEIGHT = WIDTH / 2
