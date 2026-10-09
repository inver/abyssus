/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh


/** The texture unit of the splat map; the layers take the units before it. */
const val SPLAT_UNIT = 5

private val HAS_UNIFORMS = SPLAT_LAYERS.map { "u_has_$it" }
private val LAYER_UNIFORMS = SPLAT_LAYERS.map { "u_$it" }

/**
 * One terrain asset on the GPU. Its splat textures belong to the storage: the mesh holds only their asset folders
 * ([PreparedTerrain.splats]) and reads the built textures from [assets] on every draw, so it never owns or disposes one
 * and always draws the current revision. The splat map is set to linear filtering and clamped edges the first time a
 * given texture is drawn as one, which a layer texture shared with it would also get.
 */
class TerrainMesh(prepared: PreparedTerrain, private val assets: BuiltAssets) : Disposable {
    val data = prepared.data
    private val indexCount: Int
    private val mesh: Mesh
    private val splats = prepared.splats
    private var configuredSplat: Texture? = null
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
    }

    private fun texture(field: String): Texture? = splats[field]?.let { assets.get(it) as? Texture }

    fun draw(shader: ShaderProgram, blank: Texture) {
        val splat = texture(SPLAT_MAP)?.also {
            if (it !== configuredSplat) {
                it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                it.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                configuredSplat = it
            }
        }
        shader.setUniformf("u_terrainSize", data.size.toFloat())
        // base layer first, then the channels, blended by the splat map (neutral gray without any texture)
        shader.setUniformi("u_hasSplat", if (splat != null) 1 else 0)
        for (unit in SPLAT_LAYERS.indices) {
            val texture = texture(SPLAT_LAYERS[unit])
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
    }
}
