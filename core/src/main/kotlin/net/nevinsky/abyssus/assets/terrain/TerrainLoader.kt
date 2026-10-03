/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.terrain

import net.nevinsky.abyssus.assets.loading.AssetLoader
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.loader.Pixmaps
import net.nevinsky.abyssus.assets.SPLAT_LAYERS
import net.nevinsky.abyssus.assets.SPLAT_MAP
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.assets.files.AssetFiles
import java.io.File

/** Terrain assets: height data and splat images read off the GL thread, images uploaded one per frame. */
class TerrainLoader(private val reader: TerrainDataReader) : AssetLoader<PreparedTerrain, TerrainMesh> {
    /** A splat texture that cannot be read is left out; the terrain is drawn without it. */
    override fun prepare(files: AssetFiles, name: String): PreparedTerrain? {
        val terrain = files.terrain(name) ?: return null
        val data = reader.read(terrain.data, terrain.size, terrain.uv)
        val splat = terrain.splat[SPLAT_MAP]?.let(::pixmapOrNull)
        val layers =
            SPLAT_LAYERS.mapNotNull { f -> terrain.splat[f]?.let(::pixmapOrNull)?.let { f to it } }
                .toMap()
        return PreparedTerrain(data, splat, layers)
    }

    override fun upload(prepared: PreparedTerrain) = prepared.uploadNext()

    override fun build(prepared: PreparedTerrain) = TerrainMesh(prepared)

    override fun discard(prepared: PreparedTerrain) = prepared.dispose()

    private fun pixmapOrNull(file: File): Pixmap? =
        runCatchingKeepingCancellation { Pixmaps.load(FileHandle(file)) }.getOrNull()
}


/**
 * A parsed terrain waiting for its GL resources. Its images are uploaded one per frame ([uploadNext], the slow part),
 * then [TerrainMesh] takes the textures. [dispose] releases whatever was not taken and may be called more than once.
 */
class PreparedTerrain(val data: TerrainData, splatMap: Pixmap?, layers: Map<String, Pixmap>) {
    // computed with the rest of the preparation, off the GL thread: the mesh is then just an upload
    val vertices = data.vertices()
    val indices = data.indices()

    private val pixmaps = LinkedHashMap<String, Pixmap>().also { m ->
        splatMap?.let { m[SPLAT_MAP] = it }
        m.putAll(layers)
    }
    val textures = HashMap<String, Texture>()

    /** Uploads one more image; true when every image is on the GPU. */
    fun uploadNext(): Boolean {
        val name = pixmaps.keys.firstOrNull() ?: return true
        val pixmap = pixmaps.remove(name)!!
        textures[name] = try {
            if (name == SPLAT_MAP) Texture(pixmap).also {
                it.setFilter(
                    Texture.TextureFilter.Linear,
                    Texture.TextureFilter.Linear
                )
            }
            else Texture(pixmap, true).also {
                it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                it.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
            }
        } finally {
            pixmap.dispose()
        }
        return pixmaps.isEmpty()
    }

    fun dispose() {
        pixmaps.values.forEach(Pixmap::dispose)
        pixmaps.clear()
        textures.values.forEach(Texture::dispose)
        textures.clear()
    }
}