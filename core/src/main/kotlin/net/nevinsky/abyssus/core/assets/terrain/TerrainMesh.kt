/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.Renderable
import net.nevinsky.abyssus.core.mesh.Mesh


/** The texture unit of the splat map; the layers take the units before it. */
const val SPLAT_UNIT = 5

private val HAS_UNIFORMS = SPLAT_LAYERS.map { "u_has_$it" }
private val LAYER_UNIFORMS = SPLAT_LAYERS.map { "u_$it" }

/** One terrain asset on the GPU. */
class TerrainMesh(prepared: PreparedTerrain) : Disposable {
    val data = prepared.data
    private val indexCount: Int
    private val mesh: Mesh
    private val splat: Texture?
    private val layers: Map<String, Texture>
    private val depthMaterial = Material()

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
        shader.setUniformi("u_hasSplat", if (splat != null) 1 else 0)
        for (unit in SPLAT_LAYERS.indices) {
            val texture = layers[SPLAT_LAYERS[unit]]
            shader.setUniformi(HAS_UNIFORMS[unit], if (texture != null) 1 else 0)
            shader.setUniformi(LAYER_UNIFORMS[unit], unit)
            (texture ?: blank).bind(unit)
        }
        shader.setUniformi("u_splat", SPLAT_UNIT)
        (splat ?: blank).bind(SPLAT_UNIT)
        mesh.render(shader, GL20.GL_TRIANGLES, 0, indexCount)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
    }

    /** The same indexed geometry as the color pass, with an opaque material for the caller's depth shader. */
    fun depthRenderable(world: Matrix4, out: Renderable): Renderable = out.apply {
        worldTransform.set(world)
        meshPart.set("terrain", mesh, 0, indexCount, GL20.GL_TRIANGLES)
        material = depthMaterial
        bones = null
    }

    override fun dispose() {
        mesh.dispose()
        splat?.dispose()
        layers.values.forEach(Texture::dispose)
    }
}
