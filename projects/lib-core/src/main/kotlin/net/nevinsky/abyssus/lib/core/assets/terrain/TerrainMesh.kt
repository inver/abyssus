/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import net.nevinsky.abyssus.lib.core.util.TERRAIN_SPLAT_TEXTURE_UNIT

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.graphics.g3d.Renderable
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.gdx.g3d.IntMesh


/** The texture unit of the splat map; the layers take the units before it. */
const val SPLAT_UNIT = TERRAIN_SPLAT_TEXTURE_UNIT

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
    private val mesh: IntMesh
    private val splats = prepared.splats
    private var configuredSplat: Texture? = null
    private val depthMaterial = Material()

    init {
        val vertices = prepared.vertices
        val indices = prepared.indices
        indexCount = indices.size
        mesh = IntMesh(
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

    /**
     * Re-sends the vertices after the heights of the rectangle [minX]..[maxX] by [minZ]..[maxZ] (inclusive) changed in
     * [data]: positions and normals of that rectangle and of the one-vertex border around it, whose normals read the
     * changed heights. Beyond [FULL_UPLOAD_FRACTION] of the grid the whole vertex buffer is rebuilt instead. libGDX copies a
     * partial update into the mesh's buffer and uploads that buffer on the next draw. Needs a GL context.
     */
    fun updateRegion(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        val plan = terrainRegionPlan(data.resolution, minX, minZ, maxX, maxZ)
        if (plan.full) {
            mesh.setVertices(data.vertices())
            return
        }
        val width = plan.maxX - plan.minX + 1
        val row = FloatArray(width * TERRAIN_FLOATS_PER_VERTEX)
        for (z in plan.minZ..plan.maxZ) {
            data.vertexRow(z, plan.minX, plan.maxX, row, 0)
            mesh.updateVertices((z * data.resolution + plan.minX) * TERRAIN_FLOATS_PER_VERTEX, row, 0, row.size)
        }
    }

    /** The vertices as the mesh holds them now; for tests. */
    internal fun vertexSnapshot(): FloatArray =
        FloatArray(data.resolution * data.resolution * TERRAIN_FLOATS_PER_VERTEX).also { mesh.getVertices(0, it.size, it) }

    override fun dispose() {
        mesh.dispose()
    }
}

/** Past this share of the grid, a region update rebuilds the whole vertex buffer instead of sending rows. */
const val FULL_UPLOAD_FRACTION = 0.5f

/** The vertices a region update sends: the changed rectangle grown by one vertex (clamped), or everything when [full]. */
data class TerrainRegionPlan(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val full: Boolean)

/** Plans the update for a changed rectangle of a [resolution] by [resolution] terrain; pure, so it is tested without GL. */
fun terrainRegionPlan(resolution: Int, minX: Int, minZ: Int, maxX: Int, maxZ: Int): TerrainRegionPlan {
    require(minX in 0..maxX && maxX < resolution && minZ in 0..maxZ && maxZ < resolution) { "rectangle outside the terrain" }
    val x0 = maxOf(0, minX - 1)
    val z0 = maxOf(0, minZ - 1)
    val x1 = minOf(resolution - 1, maxX + 1)
    val z1 = minOf(resolution - 1, maxZ + 1)
    val share = (x1 - x0 + 1).toLong() * (z1 - z0 + 1) / (resolution.toLong() * resolution).toFloat()
    return TerrainRegionPlan(x0, z0, x1, z1, share > FULL_UPLOAD_FRACTION)
}
