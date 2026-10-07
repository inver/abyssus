/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudFrameBudgetTest {
    private val budget = CloudFrameBudget()

    /** Feeds [seconds] of frames of [interval]; the frame numbers (from 1) at which the budget triggered. */
    private fun run(interval: Float, seconds: Float, volumetric: Boolean = true): List<Int> =
        (1..(seconds / interval).toInt()).filter { budget.frame(interval, volumetric) }

    @Test
    fun twoSecondsOverBudgetTrigger() {
        assertEquals("20 frames per second: the 40th frame completes two seconds", listOf(40), run(0.05f, 2.2f))
    }

    @Test
    fun withinBudgetNeverTriggers() {
        assertEquals(emptyList<Int>(), run(0.02f, 30f))
        assertEquals(emptyList<Int>(), run(0.033f, 30f))
    }

    @Test
    fun aFastFrameBreaksTheRun() {
        assertEquals(emptyList<Int>(), run(0.05f, 1.5f))
        assertFalse(budget.frame(0.016f, true))
        assertEquals(emptyList<Int>(), run(0.05f, 1.5f))
    }

    @Test
    fun gapsOverAQuarterSecondAreIgnored() {
        assertEquals(emptyList<Int>(), run(0.3f, 30f))
        // a gap neither counts nor breaks a run of slow frames
        assertEquals(emptyList<Int>(), run(0.05f, 1.5f))
        assertFalse(budget.frame(5f, true))
        assertEquals(listOf(10), run(0.05f, 0.6f))
    }

    @Test
    fun onlyVolumetricFramesCount() {
        assertEquals(emptyList<Int>(), run(0.05f, 10f, volumetric = false))
        assertEquals(emptyList<Int>(), run(0.05f, 1.5f))
        assertFalse(budget.frame(0.05f, false))
        assertEquals(emptyList<Int>(), run(0.05f, 1.5f))
    }

    @Test
    fun itCountsAgainAfterTriggering() {
        assertEquals(listOf(40, 80), run(0.05f, 4.2f))
    }
}
