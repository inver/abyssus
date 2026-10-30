/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsCounterTest {
    private val second = 1_000_000_000L

    /** Frames of [frames] equal steps ending at [end], the window having started at [start]. */
    private fun run(counter: FpsCounter, start: Long, end: Long, frames: Int) {
        counter.frameStarted(start)
        for (i in 1..frames) counter.frameCompleted(start + (end - start) * i / frames)
    }

    @Test fun beforeAnySampleThereIsNoValue() {
        assertNull(FpsCounter().display)
    }

    @Test fun sixtyFramesInASecondShowSixty() {
        val c = FpsCounter()
        run(c, 5 * second, 6 * second, 60)
        assertEquals(60, c.display)
    }

    @Test fun aStalledTwoSecondWindowOfTenFramesShowsFive() {
        val c = FpsCounter()
        run(c, 0, 2 * second, 10)
        assertEquals(5, c.display)
    }

    @Test fun theValueRoundsToTheNearestInteger() {
        val a = FpsCounter()
        run(a, 0, second, 59)
        assertEquals(59, a.display)
        val b = FpsCounter()
        b.frameStarted(0)
        repeat(41) { b.frameCompleted((it + 1) * (1_100_000_000L / 41)) } // 41 frames in 1.1 s = 37.27
        assertEquals(37, b.display)
    }

    @Test fun nothingIsPublishedBeforeAFullSecond() {
        val c = FpsCounter()
        c.frameStarted(0)
        for (i in 1..30) c.frameCompleted(i * (second / 100))
        assertNull(c.display)
    }

    @Test fun aWindowThatEndsStartsTheNextFromItsEnd() {
        val c = FpsCounter()
        run(c, 0, second, 60)
        assertEquals(60, c.display)
        for (i in 1..30) c.frameCompleted(second + i * (second / 15))
        assertEquals("the next window is 2 s long with 30 frames", 15, c.display)
    }

    @Test fun resetForgetsTheValueAndTheWindow() {
        val c = FpsCounter()
        run(c, 0, second, 60)
        c.reset()
        assertNull(c.display)
        // frames that complete without a started window are not counted
        c.frameCompleted(10 * second)
        assertNull(c.display)
    }

    @Test fun aTenSecondPauseThenReEnableStartsFresh() {
        val c = FpsCounter()
        run(c, 0, second, 60)
        c.reset()
        c.frameStarted(11 * second)
        c.frameCompleted(11 * second + second / 60)
        assertNull("the paused time is not part of any sample", c.display)
        for (i in 2..61) c.frameCompleted(11 * second + i * (second / 60)) // 61 frames in just over a second
        assertEquals(60, c.display)
    }

    @Test fun aClockThatStandsStillOrGoesBackNeverPublishes() {
        val c = FpsCounter()
        c.frameStarted(5 * second)
        c.frameCompleted(5 * second)
        c.frameCompleted(4 * second)
        assertNull(c.display)
        for (i in 1..61) c.frameCompleted(4 * second + i * (second / 60))
        assertEquals(60, c.display)
    }

    @Test fun countersAreIndependent() {
        val a = FpsCounter()
        val b = FpsCounter()
        run(a, 0, second, 60)
        run(b, 0, second, 30)
        assertEquals(60, a.display)
        assertEquals(30, b.display)
    }
}
