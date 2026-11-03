/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SplatExclusiveTest {
    private fun draft(vararg rgba: Int): SplatDraft {
        val d = SplatDraft(2, 10)
        rgba.forEachIndexed { i, v -> d.pixels[i] = v.toByte() }
        return d
    }

    private fun texel(d: SplatDraft) = IntArray(4) { d.pixels[it].toInt() and 0xFF }

    @Test
    fun `a full channel painted over a full other channel takes it down`() {
        val d = draft(255, 0, 0, 0)
        SplatTarget(d, exclusive = true).apply(0, 0, floatArrayOf(0f, 40f, 0f, 0f), 0)
        assertArrayEquals(intArrayOf(215, 40, 0, 0), texel(d))
    }

    @Test
    fun `the excess is taken from the others in proportion`() {
        val d = draft(100, 100, 50, 0)
        SplatTarget(d, exclusive = true).apply(0, 0, floatArrayOf(0f, 0f, 0f, 55f), 0)
        // 100+100+50+55 = 305, excess 50 over weights 100:100:50 -> 20, 20, 10
        assertArrayEquals(intArrayOf(80, 80, 40, 55), texel(d))
    }

    @Test
    fun `largest remainders break rounding ties by channel order`() {
        val d = draft(1, 1, 1, 0)
        // raise A by 255: sum 258, excess 3 over 1,1,1 -> all taken; use a smaller excess to hit the remainder rule
        val e = draft(100, 100, 100, 0)
        SplatTarget(e, exclusive = true).apply(0, 0, floatArrayOf(0f, 0f, 0f, 100f), 0)
        // excess 145 over 300: 48.33 each -> 48 each, 1 remainder left over, all equal fractions -> first channel pays
        assertArrayEquals(intArrayOf(51, 52, 52, 100), texel(e))
        SplatTarget(d, exclusive = true).apply(0, 0, floatArrayOf(0f, 0f, 0f, 255f), 0)
        assertEquals(255, texel(d).sum())
    }

    @Test
    fun `exclusive off leaves the others alone`() {
        val d = draft(255, 0, 0, 0)
        SplatTarget(d, exclusive = false).apply(0, 0, floatArrayOf(0f, 40f, 0f, 0f), 0)
        assertArrayEquals(intArrayOf(255, 40, 0, 0), texel(d))
    }

    @Test
    fun `lowering never redistributes`() {
        val d = draft(100, 100, 0, 0)
        SplatTarget(d, exclusive = true).apply(0, 0, floatArrayOf(-30f, 0f, 0f, 0f), 0)
        assertArrayEquals(intArrayOf(70, 100, 0, 0), texel(d))
    }

    @Test
    fun `the sum never exceeds 255 over a randomized stroke sequence and the result is deterministic`() {
        fun run(): IntArray {
            val d = SplatDraft(4, 30)
            val target = SplatTarget(d, exclusive = true)
            val random = Random(42)
            val changes = FloatArray(4)
            repeat(5000) {
                val gx = random.nextInt(4)
                val gz = random.nextInt(4)
                changes.fill(0f)
                val c = random.nextInt(4)
                changes[c] = random.nextFloat() * 90f
                if (random.nextInt(4) == 0) changes[(c + 1) % 4] = -random.nextFloat() * 40f
                target.apply(gx, gz, changes, 0)
                for (t in 0 until 16) {
                    val sum = (0 until 4).sumOf { d.pixels[t * 4 + it].toInt() and 0xFF }
                    assertTrue("texel $t sums to $sum", sum <= 255)
                }
            }
            return IntArray(d.pixels.size) { d.pixels[it].toInt() and 0xFF }
        }
        assertArrayEquals(run(), run())
    }
}
