package net.nevinsky.abyssus.core.assets.terrain

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.model.RayTextureFilter
import net.nevinsky.abyssus.core.assets.model.RayTextureSampler
import net.nevinsky.abyssus.core.assets.model.RayTextureWrap
import net.nevinsky.abyssus.core.assets.model.copyRayImage

/** Parsed terrain data and its decoded images, borrowed from a raster preparation; the snapshot copies them. */
data class RayTerrainSource(val data: TerrainData, val images: Map<String, Pixmap>)

/**
 * Reuses raster preparation's height/image reader without uploading or invalidating any GPU asset. [terrainLoader]
 * must not publish snapshots itself (built without a `RaySnapshotStore`).
 */
class TerrainRaySnapshotLoader(
    private val terrainLoader: TerrainLoader,
    private val maxBytes: Long = 128L * 1024 * 1024,
) : RaySnapshotLoader<RayTerrainSnapshot, RayTerrainSource> {
    override fun load(meta: AssetMeta<Any>): RayTerrainSnapshot? {
        val prepared = terrainLoader.loadPrepared(meta) ?: return null
        return try {
            capture(RayTerrainSource(prepared.data, prepared.pixmaps))
        } finally {
            prepared.dispose()
        }
    }

    /** Copies borrowed data/images before disposal; no libGDX mutable values or GL handles escape. */
    override fun capture(source: RayTerrainSource): RayTerrainSnapshot {
        val (data, images) = source
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
