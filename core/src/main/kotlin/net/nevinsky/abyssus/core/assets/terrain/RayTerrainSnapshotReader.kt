/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.core.assets.terrain

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.model.RayTextureFilter
import net.nevinsky.abyssus.assets.model.RayTextureSampler
import net.nevinsky.abyssus.assets.model.RayTextureWrap
import net.nevinsky.abyssus.assets.model.copyRayImage
import java.io.File

/**
 * Reuses raster preparation's height/image reader without uploading or invalidating any GPU asset. [loaders] makes the
 * [TerrainLoader] of a project folder; it must not publish snapshots itself (no [RayTerrainSnapshots] inside).
 */
class RayTerrainSnapshotReader(
    private val loaders: (File) -> TerrainLoader,
    private val maxBytes: Long = 128L * 1024 * 1024,
) {
    init {
        require(maxBytes > 0)
    }

    fun read(files: AssetFiles, name: String): RayTerrainSnapshot? {
        val prepared = loaders(files.projectDir).prepare(name) ?: return null
        return try {
            capture(prepared.data, prepared.pixmaps)
        } finally {
            prepared.dispose()
        }
    }

    /** Copies borrowed data/images before disposal; no libGDX mutable values or GL handles escape. */
    fun capture(data: TerrainData, images: Map<String, Pixmap>): RayTerrainSnapshot {
        val cells = data.resolution.toLong() - 1
        val bytes = data.heights.size.toLong() * (1 + TERRAIN_FLOATS_PER_VERTEX) * 4 + cells * cells * 6 * 4 +
                SPLAT_FIELDS.sumOf { field -> images[field]?.let { it.width.toLong() * it.height * 4 } ?: 0L }
        require(bytes <= maxBytes) { "CPU terrain snapshot exceeds its $maxBytes byte limit" }
        val splat = images[SPLAT_MAP]?.let {
            RayTerrainTexture(
                copyRayImage(it),
                RayTextureSampler(wrapU = RayTextureWrap.CLAMP_TO_EDGE, wrapV = RayTextureWrap.CLAMP_TO_EDGE)
            )
        }
        val layerSampler = RayTextureSampler(minFilter = RayTextureFilter.MIPMAP_LINEAR_LINEAR, mipmaps = true)
        val layers = LinkedHashMap<String, RayTerrainTexture>()
        for (key in SPLAT_LAYERS) images[key]?.let { layers[key] = RayTerrainTexture(copyRayImage(it), layerSampler) }
        return RayTerrainSnapshot(
            data.resolution,
            data.size,
            data.uv,
            data.heights,
            data.vertices(),
            data.indices(),
            splat,
            layers
        )
    }
}
