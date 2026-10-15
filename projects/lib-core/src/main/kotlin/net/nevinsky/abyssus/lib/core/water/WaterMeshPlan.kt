/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The grid of one water surface: [cellsX] by [cellsZ] quads over its extent, with a fixed ceiling on the vertex count
 * ([MAX_WATER_VERTICES], so 16-bit indices suffice and the cost of any surface is bounded). The density follows the wave
 * length (about [CELLS_PER_WAVELENGTH] cells per wave) within that ceiling; a very large sea gets coarser cells and the
 * shader's fragment normals carry the small ripples. Pure.
 */
class WaterMeshPlan internal constructor(val cellsX: Int, val cellsZ: Int, val width: Double, val length: Double) {
    /** The longest side of a cell in world units. */
    val cellSize: Double get() = kotlin.math.max(width / cellsX, length / cellsZ)

    val vertexCount: Int get() = (cellsX + 1) * (cellsZ + 1)
    val indexCount: Int get() = cellsX * cellsZ * 6

    /** The grid's vertices as `x, z` pairs relative to the surface centre, row after row along X. */
    fun vertices(): FloatArray {
        val out = FloatArray(vertexCount * 2)
        var i = 0
        for (zi in 0..cellsZ) for (xi in 0..cellsX) {
            out[i++] = ((xi.toDouble() / cellsX - 0.5) * width).toFloat()
            out[i++] = ((zi.toDouble() / cellsZ - 0.5) * length).toFloat()
        }
        return out
    }

    /** Two triangles per cell, counter-clockwise seen from above (+Y). */
    fun indices(): ShortArray {
        val out = ShortArray(indexCount)
        var i = 0
        val row = cellsX + 1
        for (zi in 0 until cellsZ) for (xi in 0 until cellsX) {
            val a = zi * row + xi
            val b = a + 1
            val c = a + row
            val d = c + 1
            out[i++] = a.toShort(); out[i++] = c.toShort(); out[i++] = b.toShort()
            out[i++] = b.toShort(); out[i++] = c.toShort(); out[i++] = d.toShort()
        }
        return out
    }

}

/** Plans [WaterMeshPlan]s; stateless. */
class WaterMeshPlanner {
    fun plan(width: Double, length: Double, wavelength: Double): WaterMeshPlan {
        require(width > 0.0 && length > 0.0 && width.isFinite() && length.isFinite()) { "a surface has a positive finite extent" }
        val cell = max((if (wavelength.isFinite() && wavelength > 0.0) wavelength else 1.0) / CELLS_PER_WAVELENGTH, MIN_CELL)
        var nx = ceil(width / cell).coerceIn(1.0, MAX_CELLS_PER_SIDE.toDouble())
        var nz = ceil(length / cell).coerceIn(1.0, MAX_CELLS_PER_SIDE.toDouble())
        val vertices = (nx + 1) * (nz + 1)
        if (vertices > MAX_WATER_VERTICES) {
            val shrink = sqrt(MAX_WATER_VERTICES / vertices)
            nx = Math.floor(nx * shrink).coerceAtLeast(1.0)
            nz = Math.floor(nz * shrink).coerceAtLeast(1.0)
            // flooring can leave one side needing a last trim to stay under the ceiling
            while ((nx + 1) * (nz + 1) > MAX_WATER_VERTICES) if (nx >= nz) nx-- else nz--
        }
        return WaterMeshPlan(nx.toInt(), nz.toInt(), width, length)
    }
}

/** No surface gets more vertices than this (a 255 by 255 grid), which also keeps 16-bit indices valid. */
const val MAX_WATER_VERTICES = 65025

/** Cells across one wavelength. */
const val CELLS_PER_WAVELENGTH = 3.0

private const val MAX_CELLS_PER_SIDE = 254
private const val MIN_CELL = 0.05
