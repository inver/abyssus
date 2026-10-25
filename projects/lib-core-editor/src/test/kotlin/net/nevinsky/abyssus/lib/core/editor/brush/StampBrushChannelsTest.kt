/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The multi-channel path of the engine, with a splat-like fake: four byte weights per cell that always add up to 255.
 * Painting one channel up takes the weight from the other three, which only a target that sees all channels of a cell
 * at once can do. Test sources only; no splat brush ships with the engine.
 */
class StampBrushChannelsTest {
    private val resolution = 33
    private val size = 64
    private val channels = 4

    private class Weights(override val resolution: Int, override val size: Int, val values: IntArray) : BrushGrid {
        override val cell = size.toFloat() / (resolution - 1)
        override fun sampleAt(x: Float, z: Float) = values[0].toFloat()
    }

    /** Adds the changes to the four weights, clamps at zero and scales the result back to a sum of exactly 255. */
    private class RenormalizingTarget(res: Int, size: Int) : BrushTarget<Weights> {
        override val grid = Weights(res, size, IntArray(res * res * 4) { if (it % 4 == 0) 255 else 0 })
        val offsets = ArrayList<Int>()
        var spare = 0

        override fun apply(gx: Int, gz: Int, changes: FloatArray, offset: Int): Boolean {
            offsets += offset
            spare = changes.size - (offset + 4)
            val base = (gz * grid.resolution + gx) * 4
            val next = FloatArray(4) { maxOf(0f, grid.values[base + it] + changes[offset + it]) }
            val total = next.sum()
            if (total <= 0f) return false
            val scaled = IntArray(4) { Math.round(next[it] * 255f / total) }
            scaled[scaled.indices.maxBy { scaled[it] }] += 255 - scaled.sum()
            var changed = false
            for (c in 0 until 4) {
                if (grid.values[base + c] != scaled[c]) changed = true
                grid.values[base + c] = scaled[c]
            }
            return changed
        }
    }

    /** Paints [channel] up by the weight times [rate] and shares the same amount out of the other three. */
    private class PaintChannel(private val channel: Int, private val rate: Float) : BrushTool<Weights> {
        override val channels = 4
        override fun changes(stamp: BrushStamp<Weights>, out: FloatArray) {
            val f = stamp.footprint
            for (i in 0 until f.count) {
                val amount = f.weight[i] * rate
                for (c in 0 until 4) out[i * 4 + c] = if (c == channel) amount else -amount / 3f
            }
        }
    }

    private fun sum(target: RenormalizingTarget, gx: Int, gz: Int): Int {
        val base = (gz * resolution + gx) * 4
        return (0 until 4).sumOf { target.grid.values[base + it] }
    }

    @Test
    fun `the target receives every channel of a cell at once`() {
        val target = RenormalizingTarget(resolution, size)
        val at = Vector3(32f, 0f, 32f)
        StampBrush(target, Matrix4()).stroke(at, at, 8f, 1f, PaintChannel(2, 80f), false)
        assertTrue(target.offsets.isNotEmpty())
        assertTrue("an offset was not a cell start: ${target.offsets}", target.offsets.all { it % channels == 0 })
        assertTrue("the buffer ended before the last cell's fourth value", target.spare >= 0)
    }

    @Test
    fun `the weights of every cell add up to 255 after every stamp`() {
        val target = RenormalizingTarget(resolution, size)
        val brush = StampBrush(target, Matrix4())
        var from = Vector3(10f, 0f, 20f)
        for (step in 1..6) {
            val to = Vector3(10f + step * 7f, 0f, 20f + step * 3f)
            brush.stroke(from, to, 8f, 1f, PaintChannel(step % 4, 60f), false)
            from = to
            for (z in 0 until resolution) for (x in 0 until resolution) {
                assertEquals("after step $step at $x/$z", 255, sum(target, x, z))
            }
        }
    }

    @Test
    fun `painting a channel raises it under the centre and takes the weight from the others`() {
        val target = RenormalizingTarget(resolution, size)
        val at = Vector3(32f, 0f, 32f)
        val rect = StampBrush(target, Matrix4()).stroke(at, at, 8f, 1f, PaintChannel(2, 200f), false)
        val centre = (16 * resolution + 16) * 4
        assertTrue(target.grid.values[centre + 2] > 0)
        assertTrue(target.grid.values[centre] < 255)
        assertFalse(rect.isEmpty)
        // a cell far from the stamp keeps the starting weights and is outside the dirty rectangle
        assertEquals(255, target.grid.values[0])
        assertFalse(0 in rect.minX..rect.maxX && 0 in rect.minZ..rect.maxZ)
    }
}
