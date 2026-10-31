package net.nevinsky.abyssus.lib.core.editor.ray.snapshot

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_LAYERS
import net.nevinsky.abyssus.lib.core.assets.terrain.SPLAT_MAP
import net.nevinsky.abyssus.lib.core.assets.terrain.TERRAIN_FLOATS_PER_VERTEX
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.texture.TextureLoader

/**
 * The CPU-only companion of a terrain: its heights and splat images are read afresh (no GL, nothing uploaded, no GPU
 * asset touched), because the raster side keeps no pixels once its splat textures are on the GPU. This is a
 * companion read, not an asset load, so it takes [terrainLoader] (for the parsed terrain) and [textureLoader] (for the
 * decoded images) directly, without the caching [net.nevinsky.abyssus.lib.core.assets.loading.AssetStorage] gives GPU assets.
 */
class TerrainRaySnapshotLoader(
    private val terrainLoader: TerrainLoader,
    private val textureLoader: TextureLoader,
    private val maxBytes: Long = RAY_SNAPSHOT_MAX_BYTES,
) : RaySnapshotLoader<RayTerrainSnapshot, Nothing> {
    override fun load(meta: AssetMeta<Any>): RayTerrainSnapshot {
        val prepared = checkNotNull(terrainLoader.loadPrepared(meta)).staged
        val images = LinkedHashMap<String, Pixmap>()
        try {
            for ((field, folder) in prepared.splats) {
                // an unreadable texture is left out, as the raster terrain leaves its layer out
                runCatchingKeepingCancellation { textureLoader.prepare(folder)?.staged?.release() }.getOrNull()
                    ?.let { images[field] = it }
            }
            return snapshot(prepared.data, images)
        } finally {
            images.values.forEach(Pixmap::dispose)
        }
    }

    /** Copies the data and images before they are disposed; no libGDX mutable values or GL handles escape. */
    internal fun snapshot(data: TerrainData, images: Map<String, Pixmap>): RayTerrainSnapshot {
        val cells = data.resolution.toLong() - 1
        val bytes = data.heights.size.toLong() * (1 + TERRAIN_FLOATS_PER_VERTEX) * 4 + cells * cells * 6 * 4 +
                SPLAT_FIELDS.sumOf { field -> images[field]?.let { it.width.toLong() * it.height * 4 } ?: 0L }
        requireSnapshotSize("terrain", bytes, maxBytes)
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
