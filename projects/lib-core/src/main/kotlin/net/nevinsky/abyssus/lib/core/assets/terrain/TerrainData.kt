/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt


/** Floats per terrain vertex: position, normal and uv. */
const val TERRAIN_FLOATS_PER_VERTEX = 8

/** The largest terrain resolution: the mesh uses 16-bit indices. */
const val MAX_TERRAIN_RESOLUTION = 255

/**
 * A terrain's height field: [resolution] x [resolution] heights, stretched over [size] x [size] world units, with
 * textures repeating [uv] times. The file is native `terrain.data`: big-endian floats, row after row (z-major).
 */
class TerrainData(val resolution: Int, val heights: FloatArray, val size: Int, val uv: Float) {
    init {
        require(resolution >= 2) { "terrain needs at least 2x2 heights" }
        require(heights.size == resolution * resolution) { "height count ${heights.size} is not $resolution squared" }
        require(resolution <= MAX_TERRAIN_RESOLUTION) { "terrain resolution $resolution is above the supported $MAX_TERRAIN_RESOLUTION" }
    }

    /** Interleaved position (3), normal (3), texture coordinate (2) per vertex, in the terrain mesh. */
    fun vertices(): FloatArray {
        val out = FloatArray(resolution * resolution * TERRAIN_FLOATS_PER_VERTEX)
        for (z in 0 until resolution) vertexRow(z, 0, resolution - 1, out, z * resolution * TERRAIN_FLOATS_PER_VERTEX)
        return out
    }

    /**
     * Writes the interleaved vertices of row [z], columns [fromX] through [toX], into [out] from index [at]. The mesh
     * is built from whole rows of this, and a changed rectangle is re-sent as the rows of it.
     */
    fun vertexRow(z: Int, fromX: Int, toX: Int, out: FloatArray, at: Int) {
        var i = at
        for (x in fromX..toX) {
            val dx = x.toFloat() / (resolution - 1)
            val dz = z.toFloat() / (resolution - 1)
            out[i] = dx * size
            out[i + 1] = heights[z * resolution + x]
            out[i + 2] = dz * size
            normalAt(x, z, out, i + 3)
            out[i + 6] = dx * uv
            out[i + 7] = dz * uv
            i += TERRAIN_FLOATS_PER_VERTEX
        }
    }

    /**
     * Copies the heights of the rectangle [minX]..[maxX] by [minZ]..[maxZ] (both inclusive) of [source], a grid of the same
     * resolution, over this terrain's heights, in place: the array this terrain was built with is the one that changes,
     * so everything holding it sees the new heights.
     */
    fun copyHeights(source: FloatArray, minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        require(source.size == heights.size) { "source has ${source.size} heights, not ${heights.size}" }
        require(minX in 0..maxX && maxX < resolution && minZ in 0..maxZ && maxZ < resolution) { "rectangle outside the terrain" }
        for (z in minZ..maxZ) System.arraycopy(source, z * resolution + minX, heights, z * resolution + minX, maxX - minX + 1)
    }

    /** Central differences, clamped at the edges. */
    private fun normalAt(x: Int, z: Int, out: FloatArray, at: Int) {
        val xp = minOf(x + 1, resolution - 1)
        val zp = minOf(z + 1, resolution - 1)
        val xm = maxOf(x - 1, 0)
        val zm = maxOf(z - 1, 0)
        val nx = heights[z * resolution + xm] - heights[z * resolution + xp]
        val nz = heights[zm * resolution + x] - heights[zp * resolution + x]
        val len = sqrt(nx * nx + 4f + nz * nz)
        out[at] = nx / len
        out[at + 1] = 2f / len
        out[at + 2] = nz / len
    }

    fun indices(): IntArray {
        val cells = resolution - 1
        val out = IntArray(cells * cells * 6)
        var i = 0
        for (z in 0 until cells) for (x in 0 until cells) {
            val c00 = z * resolution + x
            val c10 = c00 + 1
            val c01 = c00 + resolution
            val c11 = c10 + resolution
            out[i++] = c11
            out[i++] = c10
            out[i++] = c00
            out[i++] = c00
            out[i++] = c01
            out[i++] = c11
        }
        return out
    }

    /** Height at terrain-local [x],[z] (world units from the terrain's corner), bilinear; null outside the terrain. */
    fun heightAt(x: Float, z: Float): Float? {
        if (x < 0f || z < 0f || x > size || z > size) return null
        val cell = size.toFloat() / (resolution - 1)
        val gx = minOf((x / cell).toInt(), resolution - 2)
        val gz = minOf((z / cell).toInt(), resolution - 2)
        val fx = x / cell - gx
        val fz = z / cell - gz
        val h00 = heights[gz * resolution + gx]
        val h10 = heights[gz * resolution + gx + 1]
        val h01 = heights[(gz + 1) * resolution + gx]
        val h11 = heights[(gz + 1) * resolution + gx + 1]
        return (h00 * (1 - fx) + h10 * fx) * (1 - fz) + (h01 * (1 - fx) + h11 * fx) * fz
    }

    /**
     * The unit surface normal at terrain-local [x],[z] in [out], from the exact gradient of the bilinear patch:
     * `normalize(-dh/dx, 1, -dh/dz)`; null outside the terrain. The mesh's own normals come from central differences
     * over the grid, so the two agree to within a cell's gradient, which is the same bound the scatter's slope rules
     * work to.
     */
    fun normalAt(x: Float, z: Float, out: Vector3): Vector3? {
        if (x < 0f || z < 0f || x > size || z > size) return null
        val cell = size.toFloat() / (resolution - 1)
        val gx = minOf((x / cell).toInt(), resolution - 2)
        val gz = minOf((z / cell).toInt(), resolution - 2)
        val fx = x / cell - gx
        val fz = z / cell - gz
        val h00 = heights[gz * resolution + gx]
        val h10 = heights[gz * resolution + gx + 1]
        val h01 = heights[(gz + 1) * resolution + gx]
        val h11 = heights[(gz + 1) * resolution + gx + 1]
        val slopeX = ((h10 - h00) * (1 - fz) + (h11 - h01) * fz) / cell
        val slopeZ = ((h01 - h00) * (1 - fx) + (h11 - h10) * fx) / cell
        return out.set(-slopeX, 1f, -slopeZ).nor()
    }
}