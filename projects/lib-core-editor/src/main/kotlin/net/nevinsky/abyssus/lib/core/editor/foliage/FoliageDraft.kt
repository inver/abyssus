/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageChunkSize
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import kotlin.math.ceil
import kotlin.math.max

/**
 * The uncommitted state of one foliage asset while it is being edited: the settings [FoliageMeta] and the layer
 * masks the panel or the brush changed in memory, a [revision] the views watch, and the [dirtyChunks] those changes
 * touched (design decision 7). One draft per foliage, shared by the panel, the brush and every view; the plugin
 * keeps them in its `FoliageDrafts` service and discards a draft on Apply, stroke release, Cancel or a selection
 * change.
 *
 * Pure state over copies of the masks: it writes no file, touches no GL and no `Gdx.*`.
 */
class FoliageDraft(
    private val terrain: TerrainData,
    private val maskResolution: Int,
) {
    /** The settings being edited, or null while the stored asset's settings are in use. */
    var settings: FoliageMeta? = null
        private set

    /** The masks the edits changed, by layer id; a layer without an entry uses the stored asset's mask. */
    val masks: Map<Int, ByteArray>
        get() = maskOverrides

    /** The chunks touched since the draft was made: what the views re-scatter when [revision] moves past theirs. */
    val dirtyChunks: Set<FoliageChunk>
        get() = chunks

    /** Moves on every stamp, settings edit and revert. */
    val revision: Int
        get() = revisionCounter

    private var maskOverrides: MutableMap<Int, ByteArray> = LinkedHashMap()
    private var revisionCounter = 0
    private val chunks = LinkedHashSet<FoliageChunk>()

    /** The masks as they stood at the last [beginStroke], and the chunks the open stroke has touched. */
    private var strokeMasks: Map<Int, ByteArray>? = null
    private var strokeChunks = LinkedHashSet<FoliageChunk>()

    /** The draft mask for [layerId] the brush may edit in place: a copy of the asset's [stored] the first time. */
    fun editableMask(layerId: Int, stored: ByteArray): ByteArray {
        require(stored.size == maskResolution * maskResolution) { "a $maskResolution by $maskResolution mask" }
        return maskOverrides.getOrPut(layerId) { stored.copyOf() }
    }

    /** The draft settings [meta], written after each valid panel edit; a settings change touches every chunk. */
    fun editSettings(meta: FoliageMeta) {
        settings = meta
        val chunkSize = foliageChunkSize(terrain.size)
        val across = max(1, ceil(terrain.size.toDouble() / chunkSize).toInt())
        for (j in 0 until across) for (i in 0 until across) chunks += FoliageChunk(i, j)
        revisionCounter++
    }

    /** The press of a brush stroke: remembers the masks so [revertStroke] can put them back byte for byte. */
    fun beginStroke() {
        strokeMasks = maskOverrides.mapValues { (_, mask) -> mask.copyOf() }
        strokeChunks = LinkedHashSet()
    }

    /** A brush stamp: the rectangle [rect] it changed in the mask of [layerId]; an empty rectangle is a no-op. */
    fun stamped(layerId: Int, rect: MaskRect) {
        if (rect.isEmpty) return
        val touched = chunksOf(rect)
        chunks += touched
        strokeChunks += touched
        revisionCounter++
    }

    /** The masks as they were at the last [beginStroke], and the stroke's chunks marked dirty again to rebuild them. */
    fun revertStroke() {
        val atPress = strokeMasks ?: return
        maskOverrides = LinkedHashMap(atPress.mapValues { (_, mask) -> mask.copyOf() })
        chunks += strokeChunks
        strokeChunks = LinkedHashSet()
        strokeMasks = null
        revisionCounter++
    }

    /** The stroke was released and its edits are kept: a later [revertStroke] must not undo it any more. */
    fun endStroke() {
        strokeMasks = null
        strokeChunks = LinkedHashSet()
    }

    /** The chunks covering the brush rectangle [rect]: its texel centres in terrain-local units, chunked like the scatter. */
    private fun chunksOf(rect: MaskRect): Set<FoliageChunk> {
        val chunkSize = foliageChunkSize(terrain.size)
        val across = max(1, ceil(terrain.size.toDouble() / chunkSize).toInt())
        val cell = terrain.size.toFloat() / (maskResolution - 1)
        val i0 = (rect.minX * cell / chunkSize).toInt().coerceIn(0, across - 1)
        val i1 = (rect.maxX * cell / chunkSize).toInt().coerceIn(0, across - 1)
        val j0 = (rect.minZ * cell / chunkSize).toInt().coerceIn(0, across - 1)
        val j1 = (rect.maxZ * cell / chunkSize).toInt().coerceIn(0, across - 1)
        val result = LinkedHashSet<FoliageChunk>()
        for (j in j0..j1) for (i in i0..i1) result += FoliageChunk(i, j)
        return result
    }
}
