/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData

/**
 * The chunk grid of one layer's bake: [chunksX] by [chunksZ] boxes of [chunkSize] world units in terrain-local space,
 * in the bake's z-major order. Each box spans the heights of its own part of the terrain and reaches [pad] past them,
 * so a model standing on the surface does not leave its chunk's box and get culled with it.
 */
class FoliageChunkGrid(
    val chunksX: Int,
    val chunksZ: Int,
    val chunkSize: Float,
    val pad: Float,
    val boxes: List<BoundingBox>,
) {
    init {
        require(boxes.size == chunksX * chunksZ) { "a ${chunksX}x$chunksZ grid needs ${chunksX * chunksZ} boxes" }
    }

    /** The index of chunk ([i], [j]), the order the bake stores its chunks in. */
    fun index(i: Int, j: Int): Int = j * chunksX + i
}

/**
 * The grid of [layer] over [terrain]: chunk (i, j) covers terrain-local x in `[i * chunkSize, (i + 1) * chunkSize)`
 * and z likewise, both clamped to the terrain, with the heights found there, grown by [pad]. A bake of another chunk
 * size or layer dimensions gives another grid; the caller replaces it wholesale (see `FoliageDrawable.setBake`).
 */
fun foliageChunkGrid(
    layer: FoliageLayerBake,
    chunkSize: Float,
    terrain: TerrainData,
    pad: Float = 0f,
): FoliageChunkGrid {
    require(chunkSize > 0f) { "a positive chunk size" }
    require(layer.chunksX > 0 && layer.chunksZ > 0) { "a non-empty chunk grid" }
    val heights = terrain.heights
    val resolution = terrain.resolution
    val lowest = heights.min()
    val highest = heights.max()
    val mins = FloatArray(layer.chunksX * layer.chunksZ) { highest }
    val maxs = FloatArray(layer.chunksX * layer.chunksZ) { lowest }
    val cell = terrain.size.toFloat() / (resolution - 1)
    for (z in 0 until resolution) {
        for (x in 0 until resolution) {
            val i = minOf(((x * cell) / chunkSize).toInt(), layer.chunksX - 1)
            val j = minOf(((z * cell) / chunkSize).toInt(), layer.chunksZ - 1)
            if (i < 0 || j < 0) continue // a terrain of no extent
            val index = layer.index(i, j)
            val height = heights[z * resolution + x]
            mins[index] = minOf(mins[index], height)
            maxs[index] = maxOf(maxs[index], height)
        }
    }
    val size = terrain.size.toFloat()
    val boxes = ArrayList<BoundingBox>(mins.size)
    for (index in mins.indices) {
        // a chunk no vertex falls in spans the whole terrain instead of nothing
        if (mins[index] > maxs[index]) {
            mins[index] = lowest
            maxs[index] = highest
        }
        val x0 = (index % layer.chunksX) * chunkSize
        val z0 = (index / layer.chunksX) * chunkSize
        boxes += BoundingBox(
            Vector3(x0 - pad, mins[index] - pad, z0 - pad),
            Vector3(
                minOf(x0 + chunkSize, size) + pad,
                maxs[index] + pad,
                minOf(z0 + chunkSize, size) + pad,
            ),
        )
    }
    return FoliageChunkGrid(layer.chunksX, layer.chunksZ, chunkSize, pad, boxes)
}

/**
 * The chunks of [grid] that a camera at [camera] sees, filling [out] z-major: those whose box, moved by [world],
 * meets a plane of the camera's frustum and, for a [FoliageLayerKind.DETAIL] layer, whose nearest point lies within
 * [drawDistance] of the camera. An [FoliageLayerKind.OBJECT] layer draws out to the far plane, so its [drawDistance]
 * is not read. Returns [out], which must hold one entry per chunk.
 */
fun visibleFoliageChunks(
    grid: FoliageChunkGrid,
    world: Matrix4,
    kind: FoliageLayerKind,
    drawDistance: Float,
    camera: Camera,
    out: BooleanArray,
): BooleanArray {
    require(out.size == grid.boxes.size) { "a ${grid.boxes.size}-chunk visibility" }
    val distance2 = if (kind == FoliageLayerKind.DETAIL) drawDistance * drawDistance else -1f
    val world2 = BoundingBox()
    val cornerPoint = Vector3()
    val nearest = Vector3()
    for (index in grid.boxes.indices) {
        val local = grid.boxes[index]
        world2.inf()
        for (bit in 0 until 8) { // the eight corners of the box, moved into world space
            world2.ext(
                cornerPoint.set(
                    if (bit and 1 == 0) local.min.x else local.max.x,
                    if (bit and 2 == 0) local.min.y else local.max.y,
                    if (bit and 4 == 0) local.min.z else local.max.z,
                ).mul(world)
            )
        }
        if (!camera.frustum.boundsInFrustum(world2)) {
            out[index] = false
            continue
        }
        if (distance2 < 0f) {
            out[index] = true
            continue
        }
        nearest.set(
            camera.position.x.coerceIn(world2.min.x, world2.max.x),
            camera.position.y.coerceIn(world2.min.y, world2.max.y),
            camera.position.z.coerceIn(world2.min.z, world2.max.z),
        )
        out[index] = nearest.dst2(camera.position) <= distance2
    }
    return out
}
