/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatDraft
import net.nevinsky.abyssus.plugin.splat.SplatDrafts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplatViewSyncTest {
    private val resolution = 64
    private val drafts = SplatDrafts()
    private val reloads = mutableListOf<Set<String>>()
    private fun sync() = SplatViewSync(drafts) { reloads += it }

    /** A view's terrain: its own texture identity and the rectangles it was sent. */
    private class View(override val splatAsset: String? = "splat", var texture: Any? = Any()) : SplatTarget {
        override val key: Any = Any()
        override val textureKey: Any? get() = texture
        val uploads = mutableListOf<GridRect>()
        override fun upload(draft: SplatDraft, rect: GridRect): Boolean {
            if (texture == null) return false
            uploads += rect
            return true
        }
    }

    @Test
    fun `two views follow one stroke`() {
        val a = View()
        val b = View()
        val syncA = sync()
        val syncB = sync()
        val d = drafts.open("splat") { SplatDraft(resolution, 100) }
        d.touch(20, 20)
        syncA.apply(listOf(a))
        syncB.apply(listOf(b))
        // a view that first sees the draft takes the whole map once
        assertEquals(listOf(GridRect(0, 0, 63, 63)), a.uploads)
        assertEquals(listOf(GridRect(0, 0, 63, 63)), b.uploads)
        d.touch(40, 41)
        syncA.apply(listOf(a))
        // then only what changed since: the 16 texel tile around 40,41
        assertEquals(GridRect(32, 32, 47, 47), a.uploads.last())
        syncA.apply(listOf(a))
        assertEquals(2, a.uploads.size)
    }

    @Test
    fun `a view opened mid stroke gets the whole map`() {
        val d = drafts.open("splat") { SplatDraft(resolution, 100) }
        d.touch(1, 1)
        d.touch(60, 60)
        val late = View()
        sync().apply(listOf(late))
        assertEquals(listOf(GridRect(0, 0, 63, 63)), late.uploads)
    }

    @Test
    fun `two terrains sharing a map both follow`() {
        val one = View("splat")
        val two = View("splat")
        val s = sync()
        val d = drafts.open("splat") { SplatDraft(resolution, 100) }
        d.touch(5, 5)
        s.apply(listOf(one, two))
        assertEquals(1, one.uploads.size)
        assertEquals(1, two.uploads.size)
        val other = View("other")
        s.apply(listOf(other))
        assertTrue(other.uploads.isEmpty())
    }

    @Test
    fun `a rebuilt texture takes the whole map again`() {
        val v = View()
        val s = sync()
        val d = drafts.open("splat") { SplatDraft(resolution, 100) }
        d.touch(5, 5)
        s.apply(listOf(v))
        v.texture = Any()
        d.touch(6, 6)
        s.apply(listOf(v))
        assertEquals(GridRect(0, 0, 63, 63), v.uploads.last())
    }

    @Test
    fun `a texture that is not built yet is tried again`() {
        val v = View(texture = null)
        val s = sync()
        val d = drafts.open("splat") { SplatDraft(resolution, 100) }
        d.touch(5, 5)
        s.apply(listOf(v))
        assertTrue(v.uploads.isEmpty())
        v.texture = Any()
        s.apply(listOf(v))
        assertEquals(1, v.uploads.size)
    }

    @Test
    fun `discarding the draft reloads the texture of the views that followed it`() {
        val v = View()
        val s = sync()
        drafts.open("splat") { SplatDraft(resolution, 100) }.touch(2, 2)
        s.apply(listOf(v))
        drafts.discard("splat")
        s.apply(listOf(v))
        assertEquals(listOf(setOf("splat")), reloads)
        s.apply(listOf(v))
        assertEquals(1, reloads.size)
    }
}
