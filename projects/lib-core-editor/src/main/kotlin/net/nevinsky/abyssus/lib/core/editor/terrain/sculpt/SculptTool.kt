/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

/**
 * A read-only view of the heights a stamp works on: [resolution] points over a terrain [size] world units wide, row
 * after row (z-major). Nothing changes [heights] while a stamp computes its changes, so a tool that reads neighbours
 * gives the same result whatever order it visits vertices in.
 */
class SculptGrid(val resolution: Int, val size: Int, val heights: FloatArray) {
    /** The distance between two grid points, in terrain-local units. */
    val cell: Float = size.toFloat() / (resolution - 1)

    fun at(x: Int, z: Int): Float = heights[z * resolution + x]

    /** The height at terrain-local [x],[z], bilinear, with the position clamped onto the terrain. */
    fun heightAt(x: Float, z: Float): Float {
        val fx = (x / cell).coerceIn(0f, (resolution - 1).toFloat())
        val fz = (z / cell).coerceIn(0f, (resolution - 1).toFloat())
        val gx = minOf(fx.toInt(), resolution - 2)
        val gz = minOf(fz.toInt(), resolution - 2)
        val tx = fx - gx
        val tz = fz - gz
        return (at(gx, gz) * (1 - tx) + at(gx + 1, gz) * tx) * (1 - tz) + (at(gx, gz + 1) * (1 - tx) + at(gx + 1, gz + 1) * tx) * tz
    }
}

private const val INITIAL = 256

/** The grid vertices within one stamp's radius, each with its brush [weight]: strength times the smooth falloff. */
class SculptFootprint {
    var count: Int = 0
        private set
    var gx: IntArray = IntArray(INITIAL)
        private set
    var gz: IntArray = IntArray(INITIAL)
        private set
    var weight: FloatArray = FloatArray(INITIAL)
        private set

    fun clear() {
        count = 0
    }

    fun add(x: Int, z: Int, w: Float) {
        if (count == gx.size) {
            gx = gx.copyOf(count * 2)
            gz = gz.copyOf(count * 2)
            weight = weight.copyOf(count * 2)
        }
        gx[count] = x
        gz[count] = z
        weight[count] = w
        count++
    }
}

/**
 * One stamp of a stroke: the [grid] before the stamp, the [footprint] it covers, the terrain-local centre
 * ([centreX], [centreZ]), the grid distances in world units ([cellWorldX], [cellWorldZ]), the height under the point
 * where the stroke began ([strokeStartHeight], what Flatten moves toward) and whether the brush is [inverted] (Shift).
 */
class SculptStamp(
    val grid: SculptGrid,
    val footprint: SculptFootprint,
    val centreX: Float,
    val centreZ: Float,
    val cellWorldX: Float,
    val cellWorldZ: Float,
    val strokeStartHeight: Float,
    val inverted: Boolean,
)

/**
 * What one sculpting tool does to the heights under a stamp (Strategy): [changes] writes, for footprint vertex `i`, the
 * amount to add to that vertex into `out[i]`. The stroke applies every change after the tool has computed all of them.
 * Each tool is one implementation with its own typed options.
 */
sealed interface SculptTool {
    fun changes(stamp: SculptStamp, out: FloatArray)

    /** The world radius the stamp's footprint covers for the strip's brush [radius]; a Stamp covers its own size. */
    fun reach(radius: Float): Float = radius

    /** Whether a vertex's weight falls smoothly from the centre to the radius, or is the plain strength. */
    val smoothFalloff: Boolean get() = true

    /** Whether a stroke stamps once at the press, however far it is dragged ("one click is one stamp"). */
    val singleStamp: Boolean get() = false
}
