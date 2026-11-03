/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.editor.brush.ByteGrid
import net.nevinsky.abyssus.lib.core.editor.brush.ChangeTracker
import net.nevinsky.abyssus.lib.core.editor.brush.GridRect
import net.nevinsky.abyssus.lib.core.editor.brush.emptyGridRect

/** The layer slots of a terrain: 0 is the Base layer, 1 to 4 are the splat map's channels R, G, B and A. */
const val SPLAT_SLOTS: Int = 5

/** The side in texels of the tiles a draft tracks changes by. */
const val SPLAT_TILE: Int = 16

/**
 * The weights a splat stroke edits in memory: [resolution] by [resolution] texels of non-premultiplied RGBA over a
 * terrain [size] world units wide, row `r` of the image being grid row `r`. A stroke moves texels through [touch];
 * [revision] moves with every change and [changed] is the rectangle changed since the draft was made, which is what a
 * view copies into its own texture when it sees a new revision.
 *
 * Pure data with no platform state; the plugin keeps one per splat texture that is being painted.
 */
class SplatDraft(val resolution: Int, val size: Int, val pixels: ByteArray = ByteArray(resolution * resolution * 4)) {
    init {
        require(pixels.size == resolution * resolution * 4) { "a $resolution by $resolution map holds ${resolution * resolution * 4} bytes" }
    }

    /** The weights as the brush engine reads them: four channels per texel. */
    val grid = ByteGrid(resolution, size, 4, pixels)

    private val tracker = ChangeTracker()

    val revision: Int get() = tracker.revision

    private val tilesPerSide = (resolution + SPLAT_TILE - 1) / SPLAT_TILE

    /** The [revision] at which each tile last changed (0: never); a view asks what changed since the revision it last saw. */
    private val tileRevision = IntArray(tilesPerSide * tilesPerSide)

    /** Everything changed since the draft was made; a commit writes files only when the bytes differ from the file. */
    val changed: GridRect get() = tracker.changed

    /** Records that the texel [gx],[gz] changed. */
    fun touch(gx: Int, gz: Int) {
        tileRevision[(gz / SPLAT_TILE) * tilesPerSide + gx / SPLAT_TILE] = tracker.mark(GridRect(gx, gz, gx, gz))
    }

    /**
     * The rectangle of texels in tiles that changed after [since] (a [revision] a view saw earlier); empty when none did.
     * Unlike [changed], which only grows, it stays small while a drag moves, so a view re-sends just what is new.
     */
    fun changedSince(since: Int): GridRect {
        var rect = emptyGridRect()
        for (i in tileRevision.indices) {
            if (tileRevision[i] <= since) continue
            val x = (i % tilesPerSide) * SPLAT_TILE
            val z = (i / tilesPerSide) * SPLAT_TILE
            rect = rect.union(GridRect(x, z, minOf(x + SPLAT_TILE, resolution) - 1, minOf(z + SPLAT_TILE, resolution) - 1))
        }
        return rect
    }

    /**
     * Sets the layer of [slot] over the whole map: channel `slot - 1` to 255 and the others to 0, and for the Base slot
     * all four to 0. Returns whether a byte changed; the whole map is then [changed].
     */
    fun fill(slot: Int): Boolean {
        require(slot in 0 until SPLAT_SLOTS) { "slot $slot" }
        var any = false
        for (i in pixels.indices) {
            val value: Byte = if (slot != 0 && i % 4 == slot - 1) -1 else 0
            if (pixels[i] != value) {
                pixels[i] = value
                any = true
            }
        }
        if (any) {
            tileRevision.fill(tracker.mark(GridRect(0, 0, resolution - 1, resolution - 1)))
        }
        return any
    }
}
