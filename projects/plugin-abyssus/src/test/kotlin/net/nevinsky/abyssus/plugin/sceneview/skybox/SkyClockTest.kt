/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import org.junit.Assert.assertEquals
import org.junit.Test

class SkyClockTest {
    @Test
    fun advancesByTheFrameDelta() {
        val clock = SkyClock()
        repeat(60) { clock.advance(1f / 60f) }
        assertEquals(1.0, clock.seconds, 1e-5)
        assertEquals(1f / 60f, clock.lastStep, 0f)
    }

    @Test
    fun aLongGapAdvancesAQuarterSecond() {
        val clock = SkyClock()
        clock.advance(30f)
        assertEquals(0.25, clock.seconds, 1e-9)
        clock.advance(Float.NaN)
        clock.advance(-1f)
        assertEquals(0.25, clock.seconds, 1e-9)
    }

    @Test
    fun aFixedClockHoldsItsTime() {
        val clock = SkyClock()
        clock.fix(120.0)
        clock.advance(0.1f)
        assertEquals(120.0, clock.seconds, 0.0)
        assertEquals(0f, clock.lastStep, 0f)
        clock.release()
        clock.advance(0.1f)
        assertEquals(120.1, clock.seconds, 1e-6)
    }
}
