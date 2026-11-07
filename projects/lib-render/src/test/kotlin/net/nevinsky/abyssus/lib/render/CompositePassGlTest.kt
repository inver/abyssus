/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import net.nevinsky.abyssus.lib.gdx.shader.apply
import net.nevinsky.abyssus.lib.render.post.CompositeInputs
import net.nevinsky.abyssus.lib.render.post.CompositePass
import net.nevinsky.abyssus.lib.render.post.HdrFrameTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The linear HDR frame and its final pass on a real context (opt-in GL; OpenSpec `control-line-post-processing`,
 * "Frames are finished by one post pass", and `render-output-transform`, "Final pass equals per-shader transform"). A
 * host framebuffer holds a display-referred "sky"; the left half of the HDR frame is covered by a surface of known
 * linear light, the right half by nothing.
 */
class CompositePassGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val size = 64
    private val sky = floatArrayOf(0.3f, 0.5f, 0.8f)

    private fun value(name: Int): Int = BufferUtils.newIntBuffer(16).also { Gdx.gl.glGetIntegerv(name, it) }.get(0)

    private fun pixel(x: Int, y: Int): IntArray {
        val out = BufferUtils.newByteBuffer(4)
        Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, out)
        return IntArray(3) { out.get(it).toInt() and 255 }
    }

    /**
     * Draws a frame whose left half is a surface of [light] (linear, alpha 1) and whose right half is empty over the
     * sky, through [inputs]; [check] reads the host framebuffer afterwards.
     */
    private fun frame(light: Float, inputs: CompositeInputs, check: () -> Unit) {
        val host = FrameBuffer(Pixmap.Format.RGBA8888, size, size, false)
        val target = HdrFrameTarget()
        val composite = CompositePass()
        try {
            host.bind()
            val hostHandle = value(GL20.GL_FRAMEBUFFER_BINDING)
            Gdx.gl.glViewport(0, 0, size, size)
            Gdx.gl.glClearColor(sky[0], sky[1], sky[2], 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            assertTrue("no HDR frame on this context", target.ensure(size, size))
            target.bind()
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
            Gdx.gl.glScissor(0, 0, size / 2, size)
            Gdx.gl.glClearColor(light, light, light, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
            target.resolve(hostHandle)
            assertEquals(hostHandle, value(GL20.GL_FRAMEBUFFER_BINDING))
            composite.draw(target.colorTexture, size, size, inputs)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            check()
        } finally {
            composite.dispose()
            target.dispose()
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
            host.dispose()
        }
    }

    @Test
    fun aCoveredPixelIsTheTransformOfItsLightAndTheSkyStaysAsItIs() = TestGl.run {
        // 16 is far above 1: the frame holds HDR light, not a clamped colour
        for (light in listOf(0.05f, 0.18f, 0.5f, 16f)) for (exposure in listOf(1f, 0.5f)) {
            val output = OutputTransform(exposure)
            frame(light, CompositeInputs(output)) {
                val want = output.apply(floatArrayOf(light, light, light)).map { Math.round(it * 255f) }
                val got = pixel(size / 4, size / 2)
                for (c in 0..2) assertEquals("light $light exposure $exposure", want[c].toFloat(), got[c].toFloat(), 2f)
                val background = pixel(3 * size / 4, size / 2)
                for (c in 0..2) {
                    assertEquals("the sky changed", Math.round(sky[c] * 255f).toFloat(), background[c].toFloat(), 1f)
                }
            }
        }
    }

    /** A 1 x 1 R8 texture of [value]: an ambient occlusion result that is the same everywhere. */
    private fun occlusion(value: Int): Int {
        val handle = Gdx.gl.glGenTexture()
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, handle)
        val texel = BufferUtils.newByteBuffer(1).put(value.toByte()).flip()
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1)
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, 0x8229, 1, 1, 0, 0x1903, GL20.GL_UNSIGNED_BYTE, texel) // R8, RED
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4)
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST)
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST)
        return handle
    }

    /**
     * Ambient occlusion darkens lit surfaces but not bright emissive ones (OpenSpec `control-line-post-processing`,
     * "Lamps are not occluded").
     */
    @Test
    fun occlusionDarkensSurfacesButNotLamps() = TestGl.run {
        val half = occlusion(128)
        try {
            val output = OutputTransform(1f)
            frame(0.5f, CompositeInputs(output, ambientOcclusion = half, aoStrength = 1f)) {
                val got = pixel(size / 4, size / 2)[0]
                val plain = Math.round(output.apply(floatArrayOf(0.5f, 0.5f, 0.5f))[0] * 255f)
                assertTrue("an occluded surface is not darker: $got vs $plain", got < plain - 20)
            }
            frame(16f, CompositeInputs(output, ambientOcclusion = half, aoStrength = 1f)) {
                val got = pixel(size / 4, size / 2)
                val want = output.apply(floatArrayOf(16f, 16f, 16f)).map { Math.round(it * 255f) }
                for (c in 0..2) assertEquals("a lamp was occluded", want[c].toFloat(), got[c].toFloat(), 2f)
            }
        } finally {
            Gdx.gl.glDeleteTexture(half)
        }
    }

    @Test
    fun theVignetteDarkensTheCornersOnly() = TestGl.run {
        // a wholly covered frame: draw it twice, the right half being empty, and compare the left half's corner and
        // middle
        frame(0.5f, CompositeInputs(OutputTransform(1f), vignette = 1f)) {
            val corner = pixel(0, 0)[0]
            val middle = pixel(size / 2 - 1, size / 2)[0]
            val plain = Math.round(OutputTransform(1f).apply(floatArrayOf(0.5f, 0.5f, 0.5f))[0] * 255f)
            assertTrue("the corner is not darker: $corner vs $middle", corner < middle - 10)
            assertEquals("the middle changed", plain.toFloat(), middle.toFloat(), 3f)
        }
    }

    @Test
    fun theCallersStateIsRestoredAndTheFrameResizes() = TestGl.run {
        val host = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
        val target = HdrFrameTarget()
        val composite = CompositePass()
        try {
            host.bind()
            val hostHandle = value(GL20.GL_FRAMEBUFFER_BINDING)
            assertTrue(target.ensure(size, size))
            assertEquals("ensure moved the binding", hostHandle, value(GL20.GL_FRAMEBUFFER_BINDING))
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            target.bind()
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            target.resolve(hostHandle)
            composite.draw(target.colorTexture, size, size, CompositeInputs(OutputTransform(1f)))
            assertTrue(Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST))
            assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND))
            assertEquals(GL20.GL_SRC_ALPHA, value(GL20.GL_BLEND_SRC_RGB))
            assertEquals(GL20.GL_ONE, value(GL20.GL_BLEND_DST_RGB))
            assertEquals(0, value(GL20.GL_CURRENT_PROGRAM))

            // a new size makes the buffers again; the same size keeps them
            assertTrue(target.ensure(40, 24))
            assertEquals(40, target.width)
            assertEquals(24, target.height)
            val texture = target.colorTexture
            assertTrue(target.ensure(40, 24))
            assertEquals(texture, target.colorTexture)
            target.bind()
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            target.resolve(hostHandle)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDisable(GL20.GL_BLEND)
            composite.dispose()
            target.dispose()
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
            host.dispose()
        }
    }
}
