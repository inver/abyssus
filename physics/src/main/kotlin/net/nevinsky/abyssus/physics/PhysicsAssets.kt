/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.terrain.TerrainData
import net.nevinsky.abyssus.assets.terrain.TerrainDataReader
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.runtime.ecs.render.AssetType
import net.nevinsky.abyssus.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.runtime.ecs.render.RenderableObjectDelegate

/**
 * Reads what colliders need from a project's asset folders, with no GL: the vertex positions of a model, for a convex
 * hull, and the heights of a terrain, for a height field. Each asset is read once per instance.
 */
class PhysicsAssets(
    private val files: AssetFiles,
    private val models: AssimpModelLoader = AssimpModelLoader(),
    private val terrains: TerrainDataReader = TerrainDataReader(),
) {
    private val points = HashMap<String, List<Vector3>?>()
    private val heights = HashMap<String, TerrainData?>()

    /** The asset folder [entity]'s render component names when it is of [type]; null otherwise. */
    fun assetName(entity: Entity, type: AssetType): String? {
        val asset = (entity.getComponent(RenderComponent::class.java)?.renderable as? RenderableObjectDelegate)?.asset ?: return null
        return asset.assetName.takeIf { asset.type == type }
    }

    /** Every vertex position of the model in [assetName], in model space (node transforms applied); null without one. */
    fun modelPoints(assetName: String): List<Vector3>? = points.getOrPut(assetName) {
        files.model(assetName)?.let { file -> positions(models.loadData(FileHandle(file))) }
    }

    /** The heights of the terrain in [assetName]; null without terrain data. */
    fun terrain(assetName: String): TerrainData? = heights.getOrPut(assetName) {
        files.terrain(assetName)?.takeIf { it.data.isFile }?.let { terrains.read(it.data, it.size, it.uv) }
    }

    private fun positions(data: ModelData): List<Vector3> {
        val out = ArrayList<Vector3>()
        val meshes = data.meshes.associateBy { it.id }
        fun visit(node: ModelNode, parent: Matrix4) {
            val local = Matrix4().set(
                node.translation ?: Vector3.Zero,
                node.rotation ?: com.badlogic.gdx.math.Quaternion(),
                node.scale ?: Vector3(1f, 1f, 1f),
            )
            val world = Matrix4(parent).mul(local)
            for (part in node.parts.orEmpty()) {
                val mesh = data.meshes.firstOrNull { m -> m.parts.any { it.id == part.meshPartId } } ?: meshes[node.meshId] ?: continue
                val stride = mesh.attributes.sumOf { it.numComponents }
                var offset = 0
                var position = -1
                for (a in mesh.attributes) {
                    if (a.usage == VertexAttributes.Usage.Position) position = offset
                    offset += a.numComponents
                }
                if (position < 0 || stride == 0) continue
                val used = mesh.parts.firstOrNull { it.id == part.meshPartId }?.indices
                val vertices = mesh.vertices
                val indices = used?.distinct() ?: (0 until vertices.size / stride).toList()
                for (i in indices) {
                    val at = i * stride + position
                    if (at + 2 >= vertices.size) continue
                    out += Vector3(vertices[at], vertices[at + 1], vertices[at + 2]).mul(world)
                }
            }
            for (child in node.children.orEmpty()) child?.let { visit(it, world) }
        }
        for (node in data.nodes) visit(node, Matrix4())
        return out
    }
}
