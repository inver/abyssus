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

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.loader.Pixmaps
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import java.io.File

/**
 * A parsed terrain waiting for its GL resources. Its images are uploaded one per frame ([uploadNext], the slow part),
 * then [TerrainMesh] takes the textures. [dispose] releases whatever was not taken and may be called more than once.
 */
class PreparedTerrain(val data: TerrainData, splatMap: Pixmap?, layers: Map<String, Pixmap>) {
    // computed with the rest of the preparation, off the GL thread: the mesh is then just an upload
    val vertices: FloatArray = data.vertices()
    val indices: ShortArray = data.indices()

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
            if (name == ProjectLayout.SPLAT_MAP) Texture(pixmap).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
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

/** One terrain asset on the GPU. */
class TerrainMesh(prepared: PreparedTerrain) : Disposable {
    val data = prepared.data
    private val indexCount: Int
    private val mesh: Mesh
    private val splat: Texture?
    private val layers: Map<String, Texture>

    init {
        val vertices = prepared.vertices
        val indices = prepared.indices
        indexCount = indices.size
        mesh = Mesh(true, vertices.size / TerrainData.FLOATS_PER_VERTEX, indices.size,
            VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
        mesh.setVertices(vertices)
        mesh.setIndices(indices)
        // the mesh now owns the textures; the prepared terrain must not release them
        splat = prepared.textures.remove(ProjectLayout.SPLAT_MAP)
        layers = HashMap(prepared.textures)
        prepared.textures.clear()
    }

    fun draw(shader: ShaderProgram, blank: Texture) {
        shader.setUniformf("u_terrainSize", data.size.toFloat())
        // base layer first, then the channels, blended by the splat map (neutral gray without any texture)
        val hasSplat = splat != null
        shader.setUniformi("u_hasSplat", if (hasSplat) 1 else 0)
        for ((unit, field) in ProjectLayout.SPLAT_LAYERS.withIndex()) {
            val texture = layers[field]
            shader.setUniformi("u_has_$field", if (texture != null) 1 else 0)
            shader.setUniformi("u_$field", unit)
            (texture ?: blank).bind(unit)
        }
        shader.setUniformi("u_splat", SPLAT_UNIT)
        (splat ?: blank).bind(SPLAT_UNIT)
        mesh.render(shader, GL20.GL_TRIANGLES, 0, indexCount)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
    }

    override fun dispose() {
        mesh.dispose()
        splat?.dispose()
        layers.values.forEach(Texture::dispose)
    }

    private companion object {
        const val SPLAT_UNIT = 5
    }
}

/** Terrain assets: height data and splat images read off the GL thread, images uploaded one per frame. */
class TerrainLoader : AssetLoader<PreparedTerrain, TerrainMesh> {
    /** A splat texture that cannot be read is left out; the terrain is drawn without it. */
    override fun prepare(files: ProjectAssetFiles, name: String): PreparedTerrain? {
        val terrain = files.terrain(name) ?: return null
        val data = TerrainData.read(terrain.data, terrain.size, terrain.uv)
        val splat = terrain.splat[ProjectLayout.SPLAT_MAP]?.let(::pixmapOrNull)
        val layers = ProjectLayout.SPLAT_LAYERS.mapNotNull { f -> terrain.splat[f]?.let(::pixmapOrNull)?.let { f to it } }.toMap()
        return PreparedTerrain(data, splat, layers)
    }

    override fun upload(prepared: PreparedTerrain) = prepared.uploadNext()

    override fun build(prepared: PreparedTerrain) = TerrainMesh(prepared)

    override fun discard(prepared: PreparedTerrain) = prepared.dispose()

    private fun pixmapOrNull(file: File): Pixmap? = runCatchingKeepingCancellation { Pixmaps.load(FileHandle(file)) }.getOrNull()
}
