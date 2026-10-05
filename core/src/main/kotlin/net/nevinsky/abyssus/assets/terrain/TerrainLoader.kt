/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.loading.TextureUploadQueue
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.assets.terrain.TerrainMeta.Companion.SPLAT_MAP_KEY
import net.nevinsky.abyssus.core.AssetMetaLoader
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.loader.Pixmaps
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Terrain assets: height data and splat images read off the GL thread, images uploaded one per frame. */
class TerrainLoader(
    private val fileLoader: FileLoader,
    private val metaLoader: AssetMetaLoader,
    private val raySnapshots: RayTerrainSnapshots? = null
) : AssetLoader<PreparedTerrain, TerrainMesh> {
    /** A splat texture that cannot be read is left out; the terrain is drawn without it. */
    override fun prepare(name: String): PreparedTerrain? {
        val meta = metaLoader.loadBaseMeta(name) ?: return null
        val additional = meta.typedAdditional<TerrainMeta>()

        val data = read(name, additional.file, additional.size, additional.uv)
        val map = mapOf(
            "splatMap" to fileLoader.loadFile(name, additional.splatMap).let(::pixmapOrNull),
            "splatBase" to fileLoader.loadFile(name, additional.splatBase).let(::pixmapOrNull),
            "splatR" to fileLoader.loadFile(name, additional.splatR).let(::pixmapOrNull),
            "splatG" to fileLoader.loadFile(name, additional.splatG).let(::pixmapOrNull),
            "splatB" to fileLoader.loadFile(name, additional.splatB).let(::pixmapOrNull),
            "splatA" to fileLoader.loadFile(name, additional.splatA).let(::pixmapOrNull),
        )

        val prepared = PreparedTerrain(data, map)
        try {
            capture?.offer(data, prepared.pixmaps)
            return prepared
        } catch (cancelled: CancellationException) {
            prepared.dispose()
            throw cancelled
        }
    }


    private fun pixmapOrNull(file: File): Pixmap? =
        runCatchingKeepingCancellation { Pixmaps.load(FileHandle(file)) }.getOrNull()

    private fun read(assetName: String, fileName: String?, size: Int, uv: Float): TerrainData {
        val file = fileLoader.loadFile(assetName, fileName)
        val heights = DataInputStream(file.inputStream().buffered()).use { input ->
            val count = (file.length() / 4).toInt()
            FloatArray(count) { input.readFloat() }
        }
        val resolution = sqrt(heights.size.toFloat()).roundToInt()
        require(resolution * resolution == heights.size) { "terrain data has ${heights.size} heights, not a square" }
        return TerrainData(resolution, heights, size, uv)
    }

    override fun upload(prepared: PreparedTerrain) = prepared.uploadNext()

    override fun build(prepared: PreparedTerrain) = TerrainMesh(prepared)

    override fun discard(prepared: PreparedTerrain) = prepared.dispose()

}


/**
 * A parsed terrain waiting for its GL resources. Its images are uploaded one per frame ([uploadNext], the slow part),
 * then [TerrainMesh] takes the textures. [dispose] releases whatever was not taken and may be called more than once.
 */
class PreparedTerrain(
    val data: TerrainData,
    pixmapMap: Map<String, Pixmap>,
) {
    // computed with the rest of the preparation, off the GL thread: the mesh is then just an upload
    val vertices = data.vertices()
    val indices = data.indices()

    private val uploads = TextureUploadQueue(pixmapMap, ::makeTexture)

    internal val pixmaps: Map<String, Pixmap> get() = uploads.pending
    val textures: MutableMap<String, Texture> get() = uploads.textures

    /** Uploads one more image; true when every image is on the GPU. */
    fun uploadNext(): Boolean = uploads.uploadNext()

    fun dispose() = uploads.dispose()

    private fun makeTexture(name: String, pixmap: Pixmap): Texture = try {
        if (name == SPLAT_MAP_KEY) Texture(pixmap).also {
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
