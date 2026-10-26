/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OutputTransformTest {
    private val one = OutputTransform(1f)

    private fun grey(transform: OutputTransform, v: Float) = transform.apply(floatArrayOf(v, v, v))[0]

    private fun mapped(v: Float): Float = FloatArray(3) { v }.also(::toneMap)[0]

    @Test
    fun blackStaysBlackAndEverythingStaysInRange() {
        assertEquals(0f, grey(one, 0f), 0f)
        for (v in listOf(0.05f, 0.18f, 1f, 16f, 1000f)) {
            val out = grey(one, v)
            assertTrue("$v mapped to $out", out in 0f..1f)
        }
    }

    @Test
    fun theCurveIsMonotonic() {
        var last = -1f
        for (i in 0..2000) {
            val v = i / 100f
            val out = grey(one, v)
            assertTrue("$v mapped to $out after $last", out >= last)
            last = out
        }
    }

    @Test
    fun midGreyStaysNearMidGreyBeforeTheEncode() {
        // Khronos PBR Neutral takes a small offset off the dark end: 0.18 becomes 0.14, within 25% of 0.18
        assertEquals(0.18f, mapped(0.18f), 0.18f * 0.25f)
        // surface colours below the compression start are only offset, not curved
        assertEquals(0.60f - 0.04f, mapped(0.60f), 1e-6f)
    }

    @Test
    fun srgbRoundTrips() {
        for (i in 0..100) {
            val v = i / 100f
            assertEquals(v, linearToSrgb(srgbToLinear(v)), 1e-5f)
            assertEquals(v, srgbToLinear(linearToSrgb(v)), 1e-5f)
        }
        assertEquals(0.2140f, srgbToLinear(0.5f), 0.002f)
        assertEquals(1f, linearToSrgb(1f), 1e-6f)
    }

    @Test
    fun exposureScalesTheLightBeforeTheToneMap() {
        // below the shoulder the map is a fixed offset, so doubling the exposure doubles the light to within that offset
        val a = OutputTransform(1f).apply(floatArrayOf(0.2f, 0.2f, 0.2f))[0]
        val b = OutputTransform(2f).apply(floatArrayOf(0.1f, 0.1f, 0.1f))[0]
        assertEquals(a, b, 1e-6f)
    }

    @Test
    fun aBrightSunRollsOffInsteadOfClipping() {
        val sun = grey(one, 4f)
        val brighter = grey(one, 8f)
        assertTrue(sun < 1f)
        assertTrue(brighter > sun)
    }

    @Test
    fun hueSurvivesAMidToneColour() {
        val out = one.apply(floatArrayOf(0.4f, 0.2f, 0.1f))
        assertTrue(out[0] > out[1] && out[1] > out[2])
    }

    @Test
    fun anExposureThatIsNotAPositiveNumberIsRefused() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            try {
                OutputTransform(bad)
                fail("exposure $bad was accepted")
            } catch (expected: IllegalArgumentException) {
            }
        }
    }
}
