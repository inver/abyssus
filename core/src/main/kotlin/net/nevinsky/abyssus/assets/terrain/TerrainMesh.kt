package net.nevinsky.abyssus.assets.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.mesh.Mesh
import net.nevinsky.abyssus.assets.SPLAT_LAYERS
import net.nevinsky.abyssus.assets.SPLAT_MAP


/** The texture unit of the splat map; the layers take the units before it. */
const val SPLAT_UNIT = 5

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
        mesh = Mesh(
            true, vertices.size / TERRAIN_FLOATS_PER_VERTEX, indices.size,
            VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
        )
        mesh.setVertices(vertices)
        mesh.setIndices(indices)
        // the mesh now owns the textures; the prepared terrain must not release them
        splat = prepared.textures.remove(SPLAT_MAP)
        layers = HashMap(prepared.textures)
        prepared.textures.clear()
    }

    fun draw(shader: ShaderProgram, blank: Texture) {
        shader.setUniformf("u_terrainSize", data.size.toFloat())
        // base layer first, then the channels, blended by the splat map (neutral gray without any texture)
        val hasSplat = splat != null
        shader.setUniformi("u_hasSplat", if (hasSplat) 1 else 0)
        for ((unit, field) in SPLAT_LAYERS.withIndex()) {
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

}
