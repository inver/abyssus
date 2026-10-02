/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.sceneview.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import net.nevinsky.abyssus.core.loader.Pixmaps
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.sceneview.AssetLoader
import net.nevinsky.abyssus.sceneview.ProjectAssetFiles
import java.io.File

/** Terrain assets: height data and splat images read off the GL thread, images uploaded one per frame. */
class TerrainLoader : AssetLoader<PreparedTerrain, TerrainMesh> {
    /** A splat texture that cannot be read is left out; the terrain is drawn without it. */
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedTerrain? {
        val terrain = files.terrain(name) ?: return null
        val data = TerrainData.read(terrain.data, terrain.size, terrain.uv)
        val splat = terrain.splat[ProjectLayout.SPLAT_MAP]?.let(::pixmapOrNull)
        val layers =
            ProjectLayout.SPLAT_LAYERS.mapNotNull { f -> terrain.splat[f]?.let(::pixmapOrNull)?.let { f to it } }
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
        splatMap?.let { m[ProjectLayout.SPLAT_MAP] = it }
        m.putAll(layers)
    }
    val textures = HashMap<String, Texture>()

    /** Uploads one more image; true when every image is on the GPU. */
    fun uploadNext(): Boolean {
        val name = pixmaps.keys.firstOrNull() ?: return true
        val pixmap = pixmaps.remove(name)!!
        textures[name] = try {
            if (name == ProjectLayout.SPLAT_MAP) Texture(pixmap).also {
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