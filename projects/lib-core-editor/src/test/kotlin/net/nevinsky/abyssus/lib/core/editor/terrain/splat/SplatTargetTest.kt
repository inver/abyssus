/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplatTargetTest {
    private fun draft(vararg rgba: Int): SplatDraft {
        val d = SplatDraft(2, 10)
        rgba.forEachIndexed { i, v -> d.pixels[i] = v.toByte() }
        return d
    }

    private fun px(d: SplatDraft, c: Int, texel: Int = 0) = d.pixels[texel * 4 + c].toInt() and 0xFF

    @Test
    fun `values clamp to 0 through 255 and an equal byte reports no change`() {
        val d = draft(250, 3, 0, 255)
        val target = SplatTarget(d, exclusive = false)
        assertTrue(target.apply(0, 0, floatArrayOf(20f, -20f, 0f, 20f), 0))
        assertEquals(255, px(d, 0))
        assertEquals(0, px(d, 1))
        assertEquals(255, px(d, 3))
        val revision = d.revision
        // R already at 255, G already 0: nothing differs
        assertFalse(target.apply(0, 0, floatArrayOf(20f, -20f, 0f, 20f), 0))
        assertEquals(revision, d.revision)
    }

    @Test
    fun `half way rounds away from the old value in both directions`() {
        val d = draft(100, 100, 0, 0)
        val target = SplatTarget(d, exclusive = false)
        target.apply(0, 0, floatArrayOf(2.5f, -2.5f, 0f, 0f), 0)
        assertEquals(103, px(d, 0))
        assertEquals(97, px(d, 1))
        target.apply(0, 0, floatArrayOf(0.49f, -0.49f, 0f, 0f), 0)
        assertEquals(103, px(d, 0))
        assertEquals(97, px(d, 1))
    }

    @Test
    fun `a change marks its texel in the changed rectangle`() {
        val d = SplatDraft(4, 30)
        SplatTarget(d, exclusive = false).apply(2, 1, floatArrayOf(0f, 5f, 0f, 0f), 0)
        assertEquals(net.nevinsky.abyssus.lib.core.editor.brush.GridRect(2, 1, 2, 1), d.changed)
        assertEquals(1, d.revision)
        assertEquals(5, d.pixels[(1 * 4 + 2) * 4 + 1].toInt())
    }
}
