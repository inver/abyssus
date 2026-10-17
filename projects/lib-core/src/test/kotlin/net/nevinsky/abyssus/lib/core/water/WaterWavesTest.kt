/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class WaterWavesTest {
    private val lake = WaterWaves(0.08, 4.0, 0.5)
    private val sea = WaterWaves(0.6, 24.0, 2.0)

    @Test fun zeroAmplitudeIsFlatWithAnUpNormal() {
        val flat = WaterWaves(0.0, 10.0, 3.0)
        for (t in listOf(0.0, 1.5, 1000.0)) {
            assertEquals(0.0, flat.height(3.0, -7.0, t), 0.0)
            val n = flat.normal(3.0, -7.0, t)
            assertEquals(0f, n.x, 0f); assertEquals(1f, n.y, 0f); assertEquals(0f, n.z, 0f)
        }
    }

    @Test fun theHeightNeverExceedsTheAmplitude() {
        for (w in listOf(lake, sea)) {
            var max = 0.0
            for (i in 0 until 2000) {
                val h = w.height(i * 0.37, i * -0.21, i * 0.13)
                max = maxOf(max, abs(h))
            }
            assertTrue("${w.amplitude} vs $max", max <= w.amplitude + 1e-9)
            assertTrue("the waves actually move", max > w.amplitude * 0.3)
        }
    }

    @Test fun theWaveSharesAddUpToTheAmplitude() {
        assertEquals(sea.amplitude, sea.waves.sumOf { it.amplitude }, 1e-12)
    }

    @Test fun normalsAreFiniteUnitVectorsEvenForExtremeSettings() {
        for (w in listOf(lake, sea, WaterWaves(1e6, 0.01, 1e6), WaterWaves(Double.NaN, Double.NaN, Double.NaN), WaterWaves(5.0, -3.0, 2.0))) {
            for (i in 0 until 100) {
                val n = w.normal(i * 13.7, i * -9.1, i * 3.3)
                assertTrue(n.x.isFinite() && n.y.isFinite() && n.z.isFinite())
                val length = sqrt(n.x * n.x + n.y * n.y + n.z * n.z)
                assertEquals(1f, length, 1e-4f)
                assertTrue(n.y > 0f)
            }
        }
    }

    @Test fun theNormalFollowsTheSlopeNumerically() {
        val h = 1e-4
        for (i in 0 until 50) {
            val x = i * 2.3
            val z = i * -1.7
            val t = i * 0.31
            val (sx, sz) = sea.slope(x, z, t)
            val numericX = (sea.height(x + h, z, t) - sea.height(x - h, z, t)) / (2 * h)
            val numericZ = (sea.height(x, z + h, t) - sea.height(x, z - h, t)) / (2 * h)
            assertEquals(numericX, sx, 1e-5)
            assertEquals(numericZ, sz, 1e-5)
        }
    }

    @Test fun thePhaseIsFixedInTheWorldNotTheCamera() {
        // the same world position and time always give the same height, whatever else has been asked in between
        val first = sea.height(12.5, -3.25, 4.0)
        sea.height(500.0, 500.0, 99.0)
        assertEquals(first, sea.height(12.5, -3.25, 4.0), 0.0)
        assertEquals(first, WaterWaves(0.6, 24.0, 2.0).height(12.5, -3.25, 4.0), 0.0)
    }

    @Test fun theSurfaceMovesWithTimeOnlyWhenTheSpeedIsPositive() {
        assertTrue(abs(sea.height(1.0, 2.0, 0.0) - sea.height(1.0, 2.0, 2.0)) > 1e-4)
        val still = WaterWaves(0.6, 24.0, 0.0)
        assertEquals(still.height(1.0, 2.0, 0.0), still.height(1.0, 2.0, 50.0), 1e-12)
    }

    @Test fun reducedPhasesAgreeWithTheDirectHeightEvenAfterALongTime() {
        val t = 86_400.0 * 30
        val phases = sea.phases(t)
        val x = 800.0
        val z = -450.0
        var h = 0.0
        for ((i, w) in sea.waves.withIndex()) h += w.amplitude * kotlin.math.sin(w.k * (w.dirX * x + w.dirZ * z) + phases[i])
        assertEquals(sea.height(x, z, t), h, 5e-3)
        assertTrue(phases.all { it in 0f..(2 * Math.PI).toFloat() })
    }

    @Test fun theUniformTableHasEveryWavesDirectionNumberAndAmplitude() {
        val table = sea.uniform()
        assertEquals(WAVES * 4, table.size)
        for ((i, w) in sea.waves.withIndex()) {
            assertEquals(w.dirX.toFloat(), table[i * 4], 0f)
            assertEquals(w.dirZ.toFloat(), table[i * 4 + 1], 0f)
            assertEquals(w.k.toFloat(), table[i * 4 + 2], 0f)
            assertEquals(w.amplitude.toFloat(), table[i * 4 + 3], 0f)
        }
    }

    @Test fun seaWavesAreLongerAndTallerThanLakeRipples() {
        assertTrue(sea.waves[0].k < lake.waves[0].k)
        assertTrue(sea.maxSlope() > 0.0 && lake.maxSlope() > 0.0)
    }

    @Test fun meshCellsFollowTheWavelengthWithinTheVertexCeiling() {
        val planner = WaterMeshPlanner()
        val lakePlan = planner.plan(100.0, 100.0, 4.0)
        assertEquals(76 * 76, lakePlan.vertexCount)
        val seaPlan = planner.plan(2000.0, 2000.0, 24.0)
        assertTrue(seaPlan.vertexCount <= MAX_WATER_VERTICES)
        assertTrue(seaPlan.cellsX >= 200)
    }

    @Test fun nothingGetsMoreVerticesThanTheCeilingWhateverTheShape() {
        val planner = WaterMeshPlanner()
        for ((w, l, wl) in listOf(Triple(1e6, 1e6, 0.01), Triple(5000.0, 10.0, 0.5), Triple(10.0, 5000.0, 0.5), Triple(0.01, 0.01, 100.0), Triple(3e3, 3e3, Double.NaN))) {
            val plan = planner.plan(w, l, wl)
            assertTrue(plan.vertexCount <= MAX_WATER_VERTICES)
            assertTrue(plan.cellsX >= 1 && plan.cellsZ >= 1)
            assertTrue(plan.vertexCount * 2 == plan.vertices().size)
            assertTrue(plan.indices().all { (it.toInt() and 0xFFFF) < plan.vertexCount })
        }
    }

    @Test fun meshTrianglesFaceUpAndCoverTheExtent() {
        val plan = WaterMeshPlanner().plan(40.0, 20.0, 8.0)
        val v = plan.vertices()
        val xs = (0 until plan.vertexCount).map { v[it * 2] }
        val zs = (0 until plan.vertexCount).map { v[it * 2 + 1] }
        assertEquals(-20f, xs.min(), 1e-4f); assertEquals(20f, xs.max(), 1e-4f)
        assertEquals(-10f, zs.min(), 1e-4f); assertEquals(10f, zs.max(), 1e-4f)
        val idx = plan.indices()
        for (t in 0 until idx.size / 3) {
            val (a, b, c) = listOf(idx[t * 3].toInt() and 0xFFFF, idx[t * 3 + 1].toInt() and 0xFFFF, idx[t * 3 + 2].toInt() and 0xFFFF)
            // counter-clockwise seen from +Y: the cross product of the edges points up
            val e1x = v[b * 2] - v[a * 2]; val e1z = v[b * 2 + 1] - v[a * 2 + 1]
            val e2x = v[c * 2] - v[a * 2]; val e2z = v[c * 2 + 1] - v[a * 2 + 1]
            val upward = e1z * e2x - e1x * e2z
            assertTrue("triangle $t faces up", upward > 0f)
        }
    }
}
