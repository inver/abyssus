/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.lib.render.post.BloomPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lwjgl.opengl.GL32C.*

/**
 * Bloom on a real context (opt-in GL; OpenSpec `control-line-post-processing`, "Bright emissive blooms"): a small
 * bright square spreads its light around it, far pixels get almost none, and the bloom holds the square's light.
 */
class BloomPassGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val size = 128

    /** A linear RGBA16F texture of [size] x [size], black but for a [side] x [side] square of [light] in the middle. */
    private fun source(light: Float, side: Int): Int {
        val texels = FloatArray(size * size * 4)
        for (y in 0 until size) for (x in 0 until size) {
            val i = (y * size + x) * 4
            val lit = x in (size - side) / 2 until (size + side) / 2 && y in (size - side) / 2 until (size + side) / 2
            val v = if (lit) light else 0f
            texels[i] = v
            texels[i + 1] = v
            texels[i + 2] = v
            texels[i + 3] = 1f
        }
        val handle = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, handle)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, size, size, 0, GL_RGBA, GL_FLOAT, texels)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        return handle
    }

    /** The red channel of [texture] (of [w] x [h]). */
    private fun red(texture: Int, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h * 4)
        glBindTexture(GL_TEXTURE_2D, texture)
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_FLOAT, out)
        return FloatArray(w * h) { out[it * 4] }
    }

    @Test
    fun aBrightSquareSpreadsItsLightAndKeepsIt() = TestGl.run {
        val light = 50f
        val side = 8
        val input = source(light, side)
        val bloom = BloomPass()
        val framebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        try {
            val result = bloom.render(input, size, size)
            assertEquals(GL_NO_ERROR, glGetError())
            assertEquals("the framebuffer was not restored", framebuffer, glGetInteger(GL_FRAMEBUFFER_BINDING))
            val (w, h) = bloom.levels.first()
            val values = red(result, w, h)
            fun at(x: Int, y: Int) = values[y * w + x]
            val middle = w / 2
            // beside the square (the square spans 4 texels of the half-size bloom around the middle)
            val beside = at(middle + 5, middle)
            val far = at(2, 2)
            assertTrue("no light beside the square: $beside", beside > 0.05f)
            assertTrue("far pixels are lit as much as near ones: $far vs $beside", far < beside * 0.1f)
            // the Karis average keeps a sharp spike from dominating: it is spread, not amplified
            val sent = light * side * side
            val kept = values.sum() * (size * size).toFloat() / (w * h)
            assertTrue("bloom energy $kept above the source's $sent", kept <= sent * 1.05f)
        } finally {
            bloom.dispose()
            glDeleteTextures(input)
        }
    }

    @Test
    fun anEvenFrameBloomsToItsOwnLight() = TestGl.run {
        // the whole frame lit alike: the bloom holds the same light everywhere (the chain neither gains nor loses it)
        val input = source(1.5f, size)
        val bloom = BloomPass()
        try {
            val result = bloom.render(input, size, size)
            val (w, h) = bloom.levels.first()
            val values = red(result, w, h)
            val worst = values.maxOf { kotlin.math.abs(it - 1.5f) }
            assertTrue("an even frame bloomed unevenly or lost light: worst $worst", worst < 1.5f * 0.05f)
        } finally {
            bloom.dispose()
            glDeleteTextures(input)
        }
    }
}
