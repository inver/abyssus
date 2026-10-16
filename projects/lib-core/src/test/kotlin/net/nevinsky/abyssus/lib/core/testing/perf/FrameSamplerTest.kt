/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSamplerTest {
    @Test
    fun percentilesAreNearestRankInMilliseconds() {
        val sampler = FrameSampler(4)
        sampler.start()
        for (i in 1..100) sampler.record(i * 1_000_000L)
        val stats = sampler.stats()
        assertEquals(100, stats.frames)
        assertEquals(50.0, stats.p50Ms, 0.0)
        assertEquals(95.0, stats.p95Ms, 0.0)
        assertEquals(99.0, stats.p99Ms, 0.0)
    }

    @Test
    fun allocationOfTheRenderThreadIsCountedPerFrame() {
        val sampler = FrameSampler()
        sampler.start()
        var keep: Any? = null
        repeat(50) { keep = ByteArray(10_000); sampler.record(1_000_000L) }
        assertTrue(keep != null)
        assertTrue("allocated ${sampler.stats().allocatedBytesPerFrame}", sampler.stats().allocatedBytesPerFrame >= 10_000)
    }

    @Test
    fun combiningSamplesKeepsEachP95AndSumsGc() {
        val a = PerfStats(10, 1.0, 2.0, 3.0, emptyList(), 100.0, 1, 4)
        val b = PerfStats(30, 1.0, 4.0, 5.0, emptyList(), 200.0, 2, 6)
        val c = FrameSampler.combine(listOf(a, b))
        assertEquals(listOf(2.0, 4.0), c.samplesP95Ms)
        assertEquals(40, c.frames)
        assertEquals(3, c.gcCount)
        assertEquals(10, c.gcMillis)
        assertEquals(175.0, c.allocatedBytesPerFrame, 1e-9)
    }
}
