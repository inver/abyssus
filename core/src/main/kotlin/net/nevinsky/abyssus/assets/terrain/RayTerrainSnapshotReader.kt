/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.terrain

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.assets.SPLAT_LAYERS
import net.nevinsky.abyssus.assets.SPLAT_MAP
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.model.RayTextureFilter
import net.nevinsky.abyssus.assets.model.RayTextureSampler
import net.nevinsky.abyssus.assets.model.RayTextureWrap
import net.nevinsky.abyssus.assets.model.copyRayImage

/** Reuses raster preparation's height/image reader without uploading or invalidating any GPU asset. */
class RayTerrainSnapshotReader(private val reader: TerrainDataReader, private val maxBytes: Long = 128L * 1024 * 1024) {
    init { require(maxBytes > 0) }
    fun read(files: AssetFiles, name: String): RayTerrainSnapshot? {
        val prepared = TerrainLoader(reader).prepare(files, name) ?: return null
        return try { capture(prepared.data, prepared.pixmaps) } finally { prepared.dispose() }
    }

    /** Copies borrowed data/images before disposal; no libGDX mutable values or GL handles escape. */
    fun capture(data: TerrainData, images: Map<String, Pixmap>): RayTerrainSnapshot {
        val cells = data.resolution.toLong() - 1
        val bytes = data.heights.size.toLong() * (1 + TERRAIN_FLOATS_PER_VERTEX) * 4 + cells * cells * 6 * 4 +
            images.filterKeys { it == SPLAT_MAP || it in SPLAT_LAYERS }.values.sumOf { it.width.toLong() * it.height * 4 }
        require(bytes <= maxBytes) { "CPU terrain snapshot exceeds its $maxBytes byte limit" }
        val splat = images[SPLAT_MAP]?.let {
            RayTerrainTexture(copyRayImage(it), RayTextureSampler(wrapU = RayTextureWrap.CLAMP_TO_EDGE, wrapV = RayTextureWrap.CLAMP_TO_EDGE))
        }
        val layers = SPLAT_LAYERS.mapNotNull { key -> images[key]?.let {
            key to RayTerrainTexture(copyRayImage(it), RayTextureSampler(minFilter = RayTextureFilter.MIPMAP_LINEAR_LINEAR, mipmaps = true))
        } }.toMap()
        return RayTerrainSnapshot(data.resolution, data.size, data.uv, data.heights, data.vertices(), data.indices(), splat, layers)
    }
}
