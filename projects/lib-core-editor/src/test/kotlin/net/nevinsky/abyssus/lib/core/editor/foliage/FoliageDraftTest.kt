/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageChunkSize
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class FoliageDraftTest {
    private val resolution = 128
    private val size = 1600
    private val cell = size.toFloat() / (resolution - 1)
    private val chunkSize = foliageChunkSize(size)
    private val across = ceil(size / chunkSize.toDouble()).toInt()

    private fun terrain(): TerrainData = TerrainData(3, FloatArray(9), size, 1f)

    private fun mask(value: Int): ByteArray = ByteArray(resolution * resolution) { value.toByte() }

    /** The chunks a mask rectangle [rect] touches: its texel centres in terrain-local units, chunked like the scatter. */
    private fun covering(rect: MaskRect): Set<FoliageChunk> {
        val i0 = (rect.minX * cell / chunkSize).toInt()
        val i1 = (rect.maxX * cell / chunkSize).toInt()
        val j0 = (rect.minZ * cell / chunkSize).toInt()
        val j1 = (rect.maxZ * cell / chunkSize).toInt()
        return (j0..j1).flatMap { j -> (i0..i1).map { i -> FoliageChunk(i, j) } }.toSet()
    }

    @Test
    fun stampMarksOnlyTheChunksItOverlaps() {
        val draft = FoliageDraft(terrain(), resolution)
        draft.editableMask(1, mask(0))
        val rect = MaskRect(10, 20, 30, 40)
        draft.stamped(1, rect)
        assertEquals(covering(rect), draft.dirtyChunks)
        // the chunks beside the rectangle stay clean
        val i0 = (rect.minX * cell / chunkSize).toInt()
        val j0 = (rect.minZ * cell / chunkSize).toInt()
        assertFalse(FoliageChunk(i0 - 1, j0) in draft.dirtyChunks)
        assertFalse(FoliageChunk(i0, j0 - 1) in draft.dirtyChunks)
        assertTrue(draft.revision > 0)
    }

    @Test
    fun anEmptyRectangleChangesNothing() {
        val draft = FoliageDraft(terrain(), resolution)
        draft.editableMask(1, mask(0))
        val revision = draft.revision
        draft.stamped(1, emptyMaskRect())
        assertEquals(revision, draft.revision)
        assertTrue(draft.dirtyChunks.isEmpty())
    }

    @Test
    fun revertRestoresTheMasksByteForByte() {
        val stored = ByteArray(resolution * resolution) { (it % 251).toByte() }
        val draft = FoliageDraft(terrain(), resolution)
        val painted = draft.editableMask(1, stored)
        val atPress = painted.copyOf()
        draft.beginStroke()
        for (index in 1000 until 2000) painted[index] = (painted[index].toInt() + 40).toByte()
        draft.stamped(1, MaskRect(3, 3, 8, 8))
        // a layer first edited during the stroke disappears again on revert
        draft.editableMask(2, stored)
        draft.stamped(2, MaskRect(1, 1, 2, 2))
        val revision = draft.revision
        draft.revertStroke()
        assertArrayEquals(atPress, draft.masks[1])
        assertNull(draft.masks[2])
        // the stroke's chunks are dirty again so the views rebuild what the stroke touched
        assertTrue(FoliageChunk(0, 0) in draft.dirtyChunks)
        assertTrue(draft.revision > revision)
    }

    @Test
    fun revertWithoutAPressAndAfterAReleaseDoNothing() {
        val draft = FoliageDraft(terrain(), resolution)
        val stored = mask(7)
        val painted = draft.editableMask(1, stored)
        draft.beginStroke()
        painted[5] = 99
        draft.stamped(1, MaskRect(0, 0, 5, 5))
        draft.endStroke()
        draft.revertStroke()
        // the released stroke is kept
        assertEquals(99, painted[5].toInt() and 0xFF)

        val other = FoliageDraft(terrain(), resolution)
        other.editableMask(1, stored)
        other.revertStroke()
        assertArrayEquals(stored, other.masks[1])
    }

    @Test
    fun aBrushStrokeDirtiesTheChunksItPaintsAndReverts() {
        val terrain = terrain()
        val draft = FoliageDraft(terrain, resolution)
        val stored = ByteArray(resolution * resolution)
        val painted = draft.editableMask(1, stored)
        draft.beginStroke()
        val rect = FoliageBrush(terrain, resolution, Matrix4())
            .stroke(painted, Vector3(100f, 0f, 200f), Vector3(400f, 0f, 200f), 40f, 1f, FoliageBrushMode.Paint)
        assertFalse(rect.isEmpty)
        draft.stamped(1, rect)
        assertEquals(covering(rect), draft.dirtyChunks)
        draft.revertStroke()
        assertArrayEquals(stored, draft.masks[1])
    }

    @Test
    fun editingTheSettingsMarksEveryChunkAndMovesTheRevision() {
        val draft = FoliageDraft(terrain(), resolution)
        val revision = draft.revision
        draft.editSettings(FoliageMeta(terrain = "terrain_foliage", maskResolution = resolution, layers = emptyList()))
        assertTrue(draft.revision > revision)
        assertEquals(across * across, draft.dirtyChunks.size)
        assertTrue(FoliageChunk(across - 1, across - 1) in draft.dirtyChunks)
        assertEquals(0, draft.settings?.layers?.size)
    }
}
