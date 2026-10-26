/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplatFillTest {
    @Test
    fun `fill G gives 0 255 0 0 everywhere`() {
        val d = SplatDraft(64, 100)
        d.pixels.indices.forEach { d.pixels[it] = 7 }
        val before = d.revision
        assertTrue(d.fill(2))
        for (t in 0 until 64 * 64) {
            assertEquals(0, d.pixels[t * 4].toInt())
            assertEquals(255, d.pixels[t * 4 + 1].toInt() and 0xFF)
            assertEquals(0, d.pixels[t * 4 + 2].toInt())
            assertEquals(0, d.pixels[t * 4 + 3].toInt())
        }
        assertEquals(before + 1, d.revision)
        assertEquals(63, d.changed.maxX)
        assertEquals(63, d.changed.maxZ)
    }

    @Test
    fun `fill base gives all zeros and fill of an equal map changes nothing`() {
        val d = SplatDraft(64, 100)
        d.fill(4)
        assertEquals(255, d.pixels[3].toInt() and 0xFF)
        assertTrue(d.fill(0))
        assertTrue(d.pixels.all { it.toInt() == 0 })
        val revision = d.revision
        assertFalse(d.fill(0))
        assertEquals(revision, d.revision)
    }
}
