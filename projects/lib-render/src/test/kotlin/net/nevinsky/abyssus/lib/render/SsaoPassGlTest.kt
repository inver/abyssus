/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.render.post.SsaoPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lwjgl.opengl.GL32C.*

/**
 * Ambient occlusion on a real context (opt-in GL; OpenSpec `control-line-post-processing`, "Ambient occlusion darkens
 * creases"), from the depth of a floor (y = 0) meeting a wall (z = -1), worked out per pixel rather than drawn: the
 * floor beside the wall is darker than the open floor, which stays open.
 */
class SsaoPassGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val w = 256
    private val h = 192
    // made inside the GL run: its frustum update needs libGDX's natives
    private val camera by lazy {
        PerspectiveCamera(60f, w.toFloat(), h.toFloat()).apply {
            position.set(0f, 1.2f, 2.5f)
            lookAt(0f, 0.3f, -1f)
            near = 0.1f
            far = 50f
            update()
        }
    }

    /** The window depth of the floor-and-wall room at each pixel (rows from the bottom), as a depth texture. */
    private fun roomDepth(): Int {
        val depth = FloatArray(w * h)
        for (row in 0 until h) for (x in 0 until w) {
            // the ray through the pixel's centre, from the inverse of the camera's matrix (its own pick ray reads the
            // window's height)
            val ndcX = (x + 0.5f) / w * 2f - 1f
            val ndcY = (row + 0.5f) / h * 2f - 1f
            val origin = Vector3(ndcX, ndcY, -1f).prj(camera.invProjectionView)
            val direction = Vector3(ndcX, ndcY, 1f).prj(camera.invProjectionView).sub(origin).nor()
            val ray = com.badlogic.gdx.math.collision.Ray(origin, direction)
            var t = Float.MAX_VALUE
            if (ray.direction.y < 0f) t = minOf(t, -ray.origin.y / ray.direction.y)
            if (ray.direction.z < 0f) t = minOf(t, (-1f - ray.origin.z) / ray.direction.z)
            depth[row * w + x] =
                if (t == Float.MAX_VALUE) {
                    1f
                } else {
                    val hit = Vector3(ray.origin).mulAdd(ray.direction, t)
                    Vector3(hit).prj(camera.combined).z * 0.5f + 0.5f
                }
        }
        val handle = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, handle)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, w, h, 0, GL_DEPTH_COMPONENT, GL_FLOAT, depth)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        return handle
    }

    /** The openness at the world point [p], read from the half-size result [ao]. */
    private fun openness(values: FloatArray, p: Vector3): Float {
        val ndc = Vector3(p).prj(camera.combined)
        val x = ((ndc.x * 0.5f + 0.5f) * (w / 2)).toInt().coerceIn(0, w / 2 - 1)
        val y = ((ndc.y * 0.5f + 0.5f) * (h / 2)).toInt().coerceIn(0, h / 2 - 1)
        return values[y * (w / 2) + x]
    }

    @Test
    fun theFloorBesideTheWallIsDarkerThanTheOpenFloor() = TestGl.run {
        val depth = roomDepth()
        val ssao = SsaoPass()
        val framebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        try {
            val ao = ssao.render(depth, camera.projection, w, h)
            assertEquals(GL_NO_ERROR, glGetError())
            assertEquals(framebuffer, glGetInteger(GL_FRAMEBUFFER_BINDING))
            val values = FloatArray((w / 2) * (h / 2))
            glBindTexture(GL_TEXTURE_2D, ao)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_FLOAT, values)
            val corner = openness(values, Vector3(0f, 0.04f, -0.92f))
            val open = openness(values, Vector3(0f, 0f, 1f))
            val wall = openness(values, Vector3(0f, 1.2f, -1f))
            println("ssao: corner $corner, open floor $open, open wall $wall")
            // open, flat surfaces stay open (OpenSpec "Corner darker than open floor")
            assertTrue("the open floor is occluded: $open", open > 0.98f)
            assertTrue("the open wall is occluded: $wall", wall > 0.98f)
            assertTrue("the corner is not darker: $corner vs $open", corner < open - 0.12f)
        } finally {
            ssao.dispose()
            glDeleteTextures(depth)
        }
    }
}
