/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

/**
 * A working copy of the heights under a stamp, for tools that iterate: the footprint's bounding rectangle plus a
 * one-vertex border, [work] to change and [original] to compare with. [weight] is the brush weight of a footprint
 * vertex and 0 for the border and for rectangle corners outside the circle; those are held still.
 */
class FootprintScratch(stamp: SculptStamp) {
    val footprint = stamp.footprint
    private val minX: Int
    private val minZ: Int
    val width: Int
    val height: Int
    val work: FloatArray
    val original: FloatArray
    val weight: FloatArray

    init {
        val f = stamp.footprint
        var loX = Int.MAX_VALUE
        var loZ = Int.MAX_VALUE
        var hiX = -1
        var hiZ = -1
        for (i in 0 until f.count) {
            loX = minOf(loX, f.gx[i]); hiX = maxOf(hiX, f.gx[i])
            loZ = minOf(loZ, f.gz[i]); hiZ = maxOf(hiZ, f.gz[i])
        }
        val grid = stamp.grid
        minX = maxOf(0, loX - 1)
        minZ = maxOf(0, loZ - 1)
        width = minOf(grid.resolution - 1, hiX + 1) - minX + 1
        height = minOf(grid.resolution - 1, hiZ + 1) - minZ + 1
        work = FloatArray(width * height) { grid.at(minX + it % width, minZ + it / width) }
        original = work.copyOf()
        weight = FloatArray(width * height)
        for (i in 0 until f.count) weight[indexOf(f.gx[i], f.gz[i])] = f.weight[i]
    }

    /** The index in [work] of the grid vertex [gx],[gz]. */
    fun indexOf(gx: Int, gz: Int): Int = (gz - minZ) * width + (gx - minX)
}
