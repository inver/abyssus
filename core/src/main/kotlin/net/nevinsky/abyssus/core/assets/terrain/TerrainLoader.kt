/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.terrain

import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.AssetIndex
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.BuiltAssets
import java.nio.ByteBuffer
import net.nevinsky.abyssus.core.assets.parseUuidOrNull
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Terrain assets: the height data is read off the GL thread. The splat textures are other assets, named in the meta by
 * `uuid` and resolved to asset folders through [index]; the terrain names them in [dependencies], so the storage loads
 * them first, and the [TerrainMesh] reads them from the storage when it draws, so a replaced texture is picked up
 * without rebuilding the terrain.
 */
class TerrainLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val index: AssetIndex = AssetIndex(fileLoader, metaLoader),
) : AssetLoader<PreparedTerrain, TerrainMesh> {

    override fun loadPrepared(meta: AssetMeta<Any>): PreparedTerrain {
        val additional = meta.typedAdditional<TerrainMeta>()
        val data = read(meta.name, additional)
        val folders =
            SPLAT_FIELDS.mapNotNull { additional.splat(it) }.takeIf { it.isNotEmpty() }?.let { index.folders() }
        val splats = LinkedHashMap<String, String>()
        for (field in SPLAT_FIELDS) {
            val reference = additional.splat(field) ?: continue
            val folder =
                parseUuidOrNull(reference)?.let { folders?.get(it) } ?: continue // unknown: left out
            splats[field] = folder
        }
        return PreparedTerrain(data, splats)
    }

    override fun prepare(name: String): PreparedTerrain? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    private fun read(assetName: String, meta: TerrainMeta): TerrainData {
        val bytes = fileLoader.loadAssetFile(assetName, meta.terrainFile).readBytes()
        val heights = FloatArray(bytes.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes).asFloatBuffer().get(heights) // big-endian, as written by the editor
        val resolution = sqrt(heights.size.toFloat()).roundToInt()
        require(resolution * resolution == heights.size) { "terrain data has ${heights.size} heights, not a square" }
        return TerrainData(resolution, heights, meta.size, meta.uv)
    }

    override fun dependencies(prepared: PreparedTerrain): Set<String> = prepared.splats.values.toSet()

    override fun build(prepared: PreparedTerrain, assets: BuiltAssets) = TerrainMesh(prepared, assets)

    override fun discard(prepared: PreparedTerrain) = Unit
}

/** A parsed terrain waiting for its GL resources: [splats] maps each splat field to its texture asset's folder. */
class PreparedTerrain(val data: TerrainData, val splats: Map<String, String>) {
    // computed with the rest of the preparation, off the GL thread: the mesh is then just an upload
    val vertices = data.vertices()
    val indices = data.indices()
}
