/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.brush.MaskRect
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptDraft
import net.nevinsky.abyssus.plugin.terrain.SculptDrafts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SculptViewSyncTest {
    private val resolution = 6
    private val drafts = SculptDrafts()

    private class View(resolution: Int, override val assetName: String = "terrain") : SculptTarget {
        override val key: Any = Any()
        override val data = TerrainData(resolution, FloatArray(resolution * resolution), 100, 1f)
        val refreshed = mutableListOf<MaskRect>()
        override fun refresh(rect: MaskRect) { refreshed += rect }
    }

    private val reloads = mutableListOf<Set<String>>()
    private fun sync() = SculptViewSync(drafts) { reloads += it }

    private fun draft() = SculptDraft(resolution, FloatArray(36), FloatArray(36), FloatArray(36))

    @Test
    fun `two views see one stroke`() {
        val a = View(resolution)
        val b = View(resolution)
        val syncA = sync()
        val syncB = sync()
        val d = drafts.open("terrain") { draft() }
        d.add(2, 3, 5f)
        syncA.apply(listOf(a))
        syncB.apply(listOf(b))
        assertEquals(5f, a.data.heights[3 * resolution + 2], 0f)
        assertEquals(5f, b.data.heights[3 * resolution + 2], 0f)
        assertEquals(listOf(MaskRect(2, 3, 2, 3)), a.refreshed)
    }

    @Test
    fun `a view opened mid stroke gets everything changed so far`() {
        val d = drafts.open("terrain") { draft() }
        d.add(1, 1, 2f)
        d.add(4, 4, 3f)
        val late = View(resolution)
        sync().apply(listOf(late))
        assertEquals(2f, late.data.heights[1 * resolution + 1], 0f)
        assertEquals(3f, late.data.heights[4 * resolution + 4], 0f)
        assertEquals(listOf(MaskRect(1, 1, 4, 4)), late.refreshed)
    }

    @Test
    fun `a view only copies when the revision moved`() {
        val v = View(resolution)
        val s = sync()
        val d = drafts.open("terrain") { draft() }
        d.add(0, 0, 1f)
        s.apply(listOf(v))
        s.apply(listOf(v))
        assertEquals(1, v.refreshed.size)
        d.add(1, 0, 1f)
        s.apply(listOf(v))
        assertEquals(2, v.refreshed.size)
    }

    @Test
    fun `discarding the draft makes the view load the terrain again`() {
        val v = View(resolution)
        val s = sync()
        drafts.open("terrain") { draft() }.add(0, 0, 1f)
        s.apply(listOf(v))
        assertTrue(reloads.isEmpty())
        drafts.discard("terrain")
        s.apply(listOf(v))
        assertEquals(listOf(setOf("terrain")), reloads)
        s.apply(listOf(v))
        assertEquals("one reload per stroke", 1, reloads.size)
    }

    @Test
    fun `a view of another terrain is left alone`() {
        val other = View(resolution, "elsewhere")
        val s = sync()
        drafts.open("terrain") { draft() }.add(0, 0, 1f)
        s.apply(listOf(other))
        assertTrue(other.refreshed.isEmpty())
        assertTrue(reloads.isEmpty())
    }

    @Test
    fun `a draft of another resolution is not copied`() {
        val v = View(4)
        drafts.open("terrain") { draft() }.add(0, 0, 1f)
        sync().apply(listOf(v))
        assertTrue(v.refreshed.isEmpty())
    }

    @Test
    fun `the registry opens replaces and discards`() {
        assertNull(drafts.of("terrain"))
        val first = drafts.open("terrain") { draft() }
        assertSame(first, drafts.of("terrain"))
        val second = drafts.open("terrain") { draft() }
        assertSame(second, drafts.of("terrain"))
        drafts.discardAll()
        assertNull(drafts.of("terrain"))
        assertNotNull(first)
    }
}
