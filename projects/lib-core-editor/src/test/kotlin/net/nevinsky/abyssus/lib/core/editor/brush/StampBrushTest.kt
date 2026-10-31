/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampBrushTest {
    private val resolution = 33
    private val size = 64 // a grid point every 2 world units

    /** A grid whose value at a point tells where it was sampled, so the stroke start can be told from any other. */
    private class FakeGrid(override val resolution: Int, override val size: Int) : BrushGrid {
        override val cell = size.toFloat() / (resolution - 1)
        override fun sampleAt(x: Float, z: Float) = x * 10f + z
    }

    /** Counts how often each cell was applied; cells whose x is in [ignored] report no change. */
    private class CountingTarget(res: Int, size: Int, private val ignored: (Int) -> Boolean = { false }) : BrushTarget<FakeGrid> {
        override val grid = FakeGrid(res, size)
        val hits = IntArray(res * res)
        override fun apply(gx: Int, gz: Int, changes: FloatArray, offset: Int): Boolean {
            if (ignored(gx)) return false
            hits[gz * grid.resolution + gx]++
            return true
        }
    }

    private class RecordingTool(
        private val reachFactor: Float = 1f,
        override val singleStamp: Boolean = false,
    ) : BrushTool<FakeGrid> {
        val starts = ArrayList<Float>()
        var lastReversed = false
        override fun changes(stamp: BrushStamp<FakeGrid>, out: FloatArray) {
            starts += stamp.strokeStart
            lastReversed = stamp.reversed
            for (i in 0 until stamp.footprint.count) out[i] = stamp.footprint.weight[i]
        }

        override fun reach(radius: Float) = radius * reachFactor
    }

    private fun brush(target: CountingTarget) = StampBrush(target, Matrix4())

    @Test
    fun `a stroke split into several drags stamps exactly like one drag`() {
        val a = Vector3(10f, 0f, 30f)
        val b = Vector3(30f, 0f, 30f)
        val c = Vector3(50f, 0f, 30f)
        val whole = CountingTarget(resolution, size)
        brush(whole).stroke(a, c, 6f, 1f, RecordingTool(), false)
        val split = CountingTarget(resolution, size)
        val brush = brush(split)
        brush.stroke(a, b, 6f, 1f, RecordingTool(), false)
        brush.stroke(b, c, 6f, 1f, RecordingTool(), false)
        assertArrayEquals(whole.hits, split.hits)
        assertTrue(whole.hits.any { it > 0 })
    }

    @Test
    fun `a single-stamp tool stamps once at the press however far the drag goes`() {
        val target = CountingTarget(resolution, size)
        val tool = RecordingTool(singleStamp = true)
        val brush = brush(target)
        brush.stroke(Vector3(10f, 0f, 30f), Vector3(50f, 0f, 30f), 6f, 1f, tool, false)
        brush.stroke(Vector3(50f, 0f, 30f), Vector3(10f, 0f, 30f), 6f, 1f, tool, false)
        assertEquals(1, tool.starts.size)
        assertTrue("a cell was applied twice", target.hits.all { it <= 1 })
        assertTrue(target.hits[15 * resolution + 5] == 1) // around the press at x = 10 (grid 5)
        assertEquals(0, target.hits[15 * resolution + 25]) // nothing near the far end
    }

    @Test
    fun `reach widens the footprint beyond the brush radius`() {
        val plain = CountingTarget(resolution, size)
        brush(plain).stroke(Vector3(32f, 0f, 32f), Vector3(32f, 0f, 32f), 6f, 1f, RecordingTool(), false)
        val wide = CountingTarget(resolution, size)
        brush(wide).stroke(Vector3(32f, 0f, 32f), Vector3(32f, 0f, 32f), 6f, 1f, RecordingTool(reachFactor = 2f), false)
        assertTrue(wide.hits.count { it > 0 } > plain.hits.count { it > 0 })
        // 6 world units at 2 per cell: nothing at 4 cells (8 units) in the plain stamp, something in the wide one
        assertEquals(0, plain.hits[16 * resolution + 20])
        assertEquals(1, wide.hits[16 * resolution + 20])
    }

    @Test
    fun `the stroke start is sampled once, at the first stamp`() {
        val tool = RecordingTool()
        brush(CountingTarget(resolution, size)).stroke(Vector3(10f, 0f, 30f), Vector3(50f, 0f, 30f), 6f, 1f, tool, false)
        assertTrue(tool.starts.size > 2)
        assertEquals(1, tool.starts.toSet().size)
        assertEquals(10f * 10f + 30f, tool.starts[0], 1e-4f)
    }

    @Test
    fun `the dirty rectangle spans only the cells that reported a change`() {
        val target = CountingTarget(resolution, size) { it < 16 } // the left half reports no change
        val rect = brush(target).stroke(Vector3(32f, 0f, 32f), Vector3(32f, 0f, 32f), 8f, 1f, RecordingTool(), false)
        assertTrue(!rect.isEmpty)
        assertEquals(16, rect.minX)
        var maxX = -1
        for (z in 0 until resolution) for (x in 0 until resolution) {
            if (target.hits[z * resolution + x] > 0) {
                assertTrue(x in rect.minX..rect.maxX && z in rect.minZ..rect.maxZ)
                maxX = maxOf(maxX, x)
            }
        }
        assertEquals(maxX, rect.maxX)
    }

    @Test
    fun `no strength or no radius stamps nothing and reports an empty rectangle`() {
        val target = CountingTarget(resolution, size)
        val at = Vector3(32f, 0f, 32f)
        assertTrue(brush(target).stroke(at, at, 6f, 0f, RecordingTool(), false).isEmpty)
        assertTrue(brush(target).stroke(at, at, 0f, 1f, RecordingTool(), false).isEmpty)
        assertTrue(target.hits.all { it == 0 })
    }

    @Test
    fun `the reversed flag reaches the tool`() {
        val tool = RecordingTool()
        val at = Vector3(32f, 0f, 32f)
        brush(CountingTarget(resolution, size)).stroke(at, at, 6f, 1f, tool, true)
        assertTrue(tool.lastReversed)
    }
}
