/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.editor.content.Vec3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.sky.RAY_SKY_MAX_WIDTH
import net.nevinsky.abyssus.lib.core.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.lib.core.assets.sky.Sky
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a built [Sky] into a [RaySkySnapshot]: six 90 degree faces drawn with the sky's own shader into an offscreen
 * framebuffer, then resampled into the equirectangular layout ray backends sample. This is the only way to get a
 * procedural sky, which is arbitrary asset GLSL, into a ray texture. GL thread only, inside `GdxRuntime.withContext`;
 * the caller's framebuffer, viewport and depth state are restored. The result holds display values (not HDR).
 */
class RaySkyBaker(private val faceSize: Int = 256) {
    private class Face(val direction: Vector3, val up: Vector3) {
        val right: Vector3 = Vector3(direction).crs(up).nor()
        val vertical: Vector3 = Vector3(right).crs(direction).nor()
    }

    private val faces = listOf(
        Face(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f)), Face(Vector3(-1f, 0f, 0f), Vector3(0f, 1f, 0f)),
        Face(Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f)), Face(Vector3(0f, -1f, 0f), Vector3(0f, 0f, -1f)),
        Face(Vector3(0f, 0f, 1f), Vector3(0f, 1f, 0f)), Face(Vector3(0f, 0f, -1f), Vector3(0f, 1f, 0f)),
    )

    fun bake(sky: Sky, sun: Vec3): RaySkySnapshot {
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
                sky.draw(camera, sunDirection)
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
        return equirect(images)
    }

    /** Equirectangular resampling: centre column faces -Z and the top row is +Y, as the raster sky does. */
    private fun equirect(images: List<ByteArray>): RaySkySnapshot {
        val width = RAY_SKY_MAX_WIDTH
        val height = width / 2
        val out = FloatArray(width * height * 4)
        for (y in 0 until height) {
            val phi = (y + .5f) / height * PI.toFloat()
            for (x in 0 until width) {
                val theta = ((x + .5f) / width - .5f) * 2f * PI.toFloat()
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
                val px = ((u + 1f) / 2f * faceSize).toInt().coerceIn(0, faceSize - 1)
                val py = ((v + 1f) / 2f * faceSize).toInt()
                    .coerceIn(0, faceSize - 1) // row 0 is the bottom, as glReadPixels returns
                val source = (py * faceSize + px) * 4
                val i = (y * width + x) * 4
                out[i] = (images[index][source].toInt() and 255) / 255f
                out[i + 1] = (images[index][source + 1].toInt() and 255) / 255f
                out[i + 2] = (images[index][source + 2].toInt() and 255) / 255f
                out[i + 3] = 1f
            }
        }
        return RaySkySnapshot(width, height, out, hdr = false)
    }
}
