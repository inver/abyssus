/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.brush.BrushGrid
import net.nevinsky.abyssus.lib.core.editor.brush.BrushStamp
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTarget
import net.nevinsky.abyssus.lib.core.editor.brush.BrushTool

/**
 * A read-only view of the heights a stamp works on: [resolution] points over a terrain [size] world units wide, row
 * after row (z-major). Nothing changes [heights] while a stamp computes its changes, so a tool that reads neighbours
 * gives the same result whatever order it visits vertices in.
 */
class SculptGrid(override val resolution: Int, override val size: Int, val heights: FloatArray) : BrushGrid {
    /** The distance between two grid points, in terrain-local units. */
    override val cell: Float = size.toFloat() / (resolution - 1)

    // shares [heights]: the grid samples what the stroke changes
    private val terrain = TerrainData(resolution, heights, size, 1f)

    fun at(x: Int, z: Int): Float = heights[z * resolution + x]

    override fun sampleAt(x: Float, z: Float): Float = heightAt(x, z)

    /** The height at terrain-local [x],[z], bilinear, with the position clamped onto the terrain. */
    fun heightAt(x: Float, z: Float): Float = terrain.clampedHeightAt(x, z)
}

/**
 * One stamp of a sculpting stroke: the [SculptGrid] before the stamp, the footprint it covers, the terrain-local centre
 * and the grid distances in world units, the height under the point where the stroke began (`strokeStart`, what Flatten
 * moves toward) and whether the brush is `reversed` (Shift).
 */
typealias SculptStamp = BrushStamp<SculptGrid>

/**
 * What one sculpting tool does to the heights under a stamp (Strategy): [changes] writes, for footprint vertex `i`, the
 * amount to add to that vertex into `out[i]`. The stroke applies every change after the tool has computed all of them.
 * Each tool is one implementation with its own typed options; `reach`, `smoothFalloff` and `singleStamp` are those of
 * [BrushTool], where a Stamp covers its own size and a click is one stamp.
 */
sealed interface SculptTool : BrushTool<SculptGrid>

/** The [SculptDraft] as what a stroke changes: each change moves a vertex of the draft, and a zero change moves none. */
class SculptTarget(private val draft: SculptDraft, size: Int) : BrushTarget<SculptGrid> {
    override val grid = SculptGrid(draft.resolution, size, draft.composed)

    override fun apply(gx: Int, gz: Int, changes: FloatArray, offset: Int): Boolean {
        val change = changes[offset]
        if (change == 0f) return false
        draft.add(gx, gz, change)
        return true
    }
}
