/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.shadows

import net.nevinsky.abyssus.editor.scene.LightSet

enum class ShadowLightKind { DIRECTIONAL, POINT, SPOT }

data class ShadowTile(val index: Int, val x: Int, val y: Int, val size: Int = 1024, val atlasSize: Int = 4096)

data class ShadowAllocation(val entityId: String, val kind: ShadowLightKind, val tiles: List<ShadowTile>)

/** Bounded atlas packing. Small light sets use larger tiles; the grid grows only when more views are needed. */
class ShadowLayout {
    private var previous = emptyMap<String, ShadowAllocation>()
    private var grid = 0

    fun allocate(lights: LightSet): List<ShadowAllocation> {
        val candidates = buildList {
            lights.directional.sortedBy { it.entityId }.take(1).forEach { add(it.entityId to ShadowLightKind.DIRECTIONAL) }
            lights.point.sortedBy { it.entityId }.take(2).forEach { add(it.entityId to ShadowLightKind.POINT) }
            lights.spot.sortedBy { it.entityId }.take(3).forEach { add(it.entityId to ShadowLightKind.SPOT) }
        }
        if (candidates.isEmpty()) { previous = emptyMap(); grid = 0; return emptyList() }
        val count = candidates.sumOf { tileCount(it.second) }
        grid = maxOf(grid, kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt())
        val used = HashSet<Int>()
        val indices = HashMap<String, List<Int>>()
        candidates.forEach { (id, kind) ->
            val old = previous[id]?.takeIf { it.kind == kind }
            if (old != null && old.tiles.size == tileCount(kind) && old.tiles.none { it.index in used }) {
                indices[id] = old.tiles.map { it.index }
                used.addAll(old.tiles.map { it.index })
            }
        }
        candidates.forEach { (id, kind) ->
            if (id !in indices) {
                val count = tileCount(kind)
                val free = (0 until ATLAS_TILES).filterNot(used::contains).take(count)
                if (free.size == count) { indices[id] = free; used.addAll(free) }
            }
        }
        val result = candidates.mapNotNull { (id, kind) ->
            val allocated = indices[id] ?: return@mapNotNull null
            ShadowAllocation(id, kind, allocated.map(::tile))
        }
        previous = result.associateBy { it.entityId }
        return result
    }

    private fun tileCount(kind: ShadowLightKind) = when (kind) {
        ShadowLightKind.DIRECTIONAL, ShadowLightKind.SPOT -> 1
        ShadowLightKind.POINT -> 6
    }

    private fun tile(index: Int): ShadowTile {
        val size = ATLAS_SIZE / grid
        return ShadowTile(index, (index % grid) * size, (index / grid) * size, size)
    }

    companion object {
        const val ATLAS_SIZE = 4096
        const val TILE_SIZE = 1024
        const val GRID = 4
        const val ATLAS_TILES = GRID * GRID
    }
}
