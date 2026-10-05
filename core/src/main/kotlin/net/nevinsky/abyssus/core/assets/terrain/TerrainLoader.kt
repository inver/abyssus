/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.loading.AssetLoader
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotStore
import net.nevinsky.abyssus.core.assets.loading.TextureUploadQueue
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.core.loader.Pixmaps
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Terrain assets: height data and splat images read off the GL thread, images uploaded one per frame. */
class TerrainLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val raySnapshots: RaySnapshotStore<RayTerrainSnapshot, RayTerrainSource>? = null,
) : AssetLoader<PreparedTerrain, TerrainMesh> {

    override fun loadPrepared(meta: AssetMeta<Any>): PreparedTerrain? {
        val capture = raySnapshots?.preparation(meta.name)
        val additional = meta.typedAdditional<TerrainMeta>()

        val data = read(meta.name, additional)
        val images = LinkedHashMap<String, Pixmap>()
        for (field in SPLAT_FIELDS) {
            additional.splat(field)?.let { loadPixmap(meta.name, it) }?.let { images[field] = it }
        }

        val prepared = PreparedTerrain(data, images)
        try {
            capture?.offer(RayTerrainSource(data, prepared.pixmaps))
        } catch (failure: Throwable) {
            prepared.dispose()
            throw failure
        }
        return prepared
    }

    /** A splat texture that cannot be read is left out; the terrain is drawn without it. */
    override fun prepare(name: String): PreparedTerrain? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        return loadPrepared(meta)
    }

    private fun loadPixmap(assetName: String, fileName: String): Pixmap? = runCatchingKeepingCancellation {
        Pixmaps.load(FileHandle(fileLoader.loadFile(assetName, fileName)))
    }.getOrNull()

    private fun read(assetName: String, meta: TerrainMeta): TerrainData {
        val bytes = fileLoader.loadFile(assetName, meta.file).readBytes()
        val heights = FloatArray(bytes.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes).asFloatBuffer().get(heights) // big-endian, as written by the editor
        val resolution = sqrt(heights.size.toFloat()).roundToInt()
        require(resolution * resolution == heights.size) { "terrain data has ${heights.size} heights, not a square" }
        return TerrainData(resolution, heights, meta.size, meta.uv)
    }

    override fun upload(prepared: PreparedTerrain) = prepared.uploadNext()

    override fun build(prepared: PreparedTerrain) = TerrainMesh(prepared)

    override fun discard(prepared: PreparedTerrain) = prepared.dispose()
}

/**
 * A parsed terrain waiting for its GL resources. Its images are uploaded one per frame ([uploadNext], the slow part),
 * then [TerrainMesh] takes the textures. [dispose] releases whatever was not taken and may be called more than once.
 */
class PreparedTerrain(val data: TerrainData, pixmaps: Map<String, Pixmap>) {
    // computed with the rest of the preparation, off the GL thread: the mesh is then just an upload
    val vertices = data.vertices()
    val indices = data.indices()

    private val uploads = TextureUploadQueue(pixmaps, ::makeTexture)
    internal val pixmaps: Map<String, Pixmap> get() = uploads.pending
    val textures: MutableMap<String, Texture> get() = uploads.textures

    /** Uploads one more image; true when every image is on the GPU. */
    fun uploadNext(): Boolean = uploads.uploadNext()

    fun dispose() = uploads.dispose()

    private fun makeTexture(name: String, pixmap: Pixmap): Texture = try {
        if (name == SPLAT_MAP) Texture(pixmap).also {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        else Texture(pixmap, true).also {
            it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            it.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        }
    } finally {
        pixmap.dispose()
    }
}
