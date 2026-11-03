/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.brush

import kotlin.math.roundToInt

/**
 * A grid of unsigned bytes, [channels] per cell, row after row (z-major) over a terrain [size] world units wide: the
 * foliage density mask (one channel) and, later, a splat map. [values] is the array a stroke edits in place; a brush
 * that is handed a new array per call sets it before stamping.
 */
class ByteGrid(
    override val resolution: Int,
    override val size: Int,
    val channels: Int = 1,
    var values: ByteArray = ByteArray(resolution * resolution * channels),
) : BrushGrid {
    init {
        require(resolution >= 2) { "a byte grid needs at least 2 points" }
        require(channels >= 1) { "a byte grid needs at least 1 channel" }
    }

    override val cell: Float = size.toFloat() / (resolution - 1)

    /** The unsigned value of [channel] in the cell [gx],[gz]. */
    fun at(gx: Int, gz: Int, channel: Int = 0): Int = values[(gz * resolution + gx) * channels + channel].toInt() and 0xFF

    /** The first channel of the cell nearest to terrain-local [x],[z], clamped onto the grid. */
    override fun sampleAt(x: Float, z: Float): Float {
        val gx = (x / cell).roundToInt().coerceIn(0, resolution - 1)
        val gz = (z / cell).roundToInt().coerceIn(0, resolution - 1)
        return at(gx, gz).toFloat()
    }
}
