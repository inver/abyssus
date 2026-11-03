/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

/**
 * A read-only view of the grid a stamp works on: [resolution] points over a terrain [size] world units wide, row after
 * row (z-major). Nothing changes the grid while a stamp computes its changes, so a tool that reads neighbours gives the
 * same result whatever order it visits cells in.
 */
interface BrushGrid {
    val resolution: Int
    val size: Int

    /** The distance between two grid points, in terrain-local units. */
    val cell: Float

    /** The value of the first channel at terrain-local [x],[z], bilinear and clamped onto the grid. */
    fun sampleAt(x: Float, z: Float): Float
}

/**
 * One stamp of a stroke: the [grid] before the stamp, the [footprint] it covers, the terrain-local centre
 * ([centreX], [centreZ]), the grid distances in world units ([cellWorldX], [cellWorldZ]), the brush [strength], the
 * grid value under the point where the stroke began ([strokeStart], what Flatten moves toward) and whether the brush is
 * [reversed] (Shift, or the mode's erase).
 */
class BrushStamp<G : BrushGrid>(
    val grid: G,
    val footprint: Footprint,
    val centreX: Float,
    val centreZ: Float,
    val cellWorldX: Float,
    val cellWorldZ: Float,
    val strength: Float,
    val strokeStart: Float,
    val reversed: Boolean,
)

/**
 * What one brush tool does to the grid under a stamp (Strategy): [changes] writes, for footprint cell `i` and channel
 * `c`, the amount to add into `out[i * channels + c]`, which starts at zero. The stroke applies every change after the
 * tool has computed all of them, and the [BrushTarget] decides what a change means (an add, a rounded and clamped step,
 * a renormalization).
 */
interface BrushTool<G : BrushGrid> {
    /** How many values the tool writes per footprint cell. */
    val channels: Int get() = 1

    fun changes(stamp: BrushStamp<G>, out: FloatArray)

    /** The world radius the stamp's footprint covers for the brush [radius]; a stamp of an image covers its own size. */
    fun reach(radius: Float): Float = radius

    /** Whether a cell's weight falls smoothly from the centre to the radius, or is the plain strength. */
    val smoothFalloff: Boolean get() = true

    /** Whether a stroke stamps once at the press, however far it is dragged ("one click is one stamp"). */
    val singleStamp: Boolean get() = false
}

/** What a stroke changes: the [grid] it reads as it was, and the application of one cell's changes. */
interface BrushTarget<G : BrushGrid> {
    val grid: G

    /**
     * Applies the changes of the cell at [gx],[gz], read from `changes[offset]` onward (one per channel of the tool);
     * true when the stored value changed, which is what the stroke's dirty rectangle is made of.
     */
    fun apply(gx: Int, gz: Int, changes: FloatArray, offset: Int): Boolean
}
