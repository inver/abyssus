/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SculptDraftTest {
    private val resolution = 5
    private val base = FloatArray(25) { it * 0.1f + 0.03f }

    private fun draft() = SculptDraft(resolution, base, base.copyOf(), FloatArray(25))

    @Test
    fun `compose adds each delta to its base`() {
        val delta = FloatArray(25) { 0.37f }
        val composed = composeSculpt(base, delta)
        for (i in composed.indices) assertEquals(base[i] + delta[i], composed[i], 0f)
    }

    @Test
    fun `composed equals base plus delta exactly after strokes and recompose`() {
        val d = draft()
        repeat(7) { n -> d.add(n % 5, (n * 3) % 5, 0.1f * (n + 1) / 3f) }
        d.add(2, 2, 0.7f)
        d.add(2, 2, -0.2f)
        d.recompose()
        assertArrayEquals(composeSculpt(base, d.delta), d.composed, 0f)
    }

    @Test
    fun `the base is never edited`() {
        val before = base.copyOf()
        draft().apply { add(1, 1, 3f); recompose() }
        assertArrayEquals(before, base, 0f)
    }

    @Test
    fun `the changed rectangle grows with each change`() {
        val d = draft()
        assertTrue(d.changed.isEmpty)
        d.add(1, 3, 1f)
        d.add(4, 0, 1f)
        assertEquals(GridRect(1, 0, 4, 3), d.changed)
    }

    @Test
    fun `a zero change leaves the draft untouched`() {
        val d = draft()
        d.add(2, 2, 0f)
        assertEquals(0, d.revision)
        assertTrue(d.changed.isEmpty)
    }

    @Test
    fun `revision moves with each change`() {
        val d = draft()
        d.add(0, 0, 1f)
        d.add(0, 0, 1f)
        assertEquals(2, d.revision)
    }
}
