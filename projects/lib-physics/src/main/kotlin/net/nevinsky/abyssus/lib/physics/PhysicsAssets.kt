/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.loading.CompositeAssetLoader
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.model.PreparedModel
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.PreparedTerrain
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.core.model.ModelData
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.lib.runtime.ecs.render.RenderableObjectDelegate
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File

/**
 * Reads what colliders need from a project's asset folders, with no GL: the vertex positions of a model, for a convex
 * hull, and the heights of a terrain, for a height field. Each asset is read once per instance.
 */
class PhysicsAssets(private val loader: CompositeAssetLoader) {
    /** Assets of the project in [projectDir], with its own file access and meta reading. */
    constructor(projectDir: File, json: JsonProcessor = JsonProcessor(), log: Logger = NOPLogger.NOP_LOGGER) :
        this(FileLoader(projectDir), json, log)

    private constructor(fileLoader: FileLoader, json: JsonProcessor, log: Logger) :
        this(AssetMetaLoader(json, fileLoader, log), fileLoader)

    private constructor(metaLoader: AssetMetaLoader, fileLoader: FileLoader) : this(
        CompositeAssetLoader(
            metaLoader,
            mapOf(
                MetaType.MODEL to ModelLoader(metaLoader, AssimpModelLoader(), fileLoader, decodeTextures = false),
                MetaType.TERRAIN to TerrainLoader(fileLoader, metaLoader),
            ),
        ),
    )

    private val points = HashMap<String, List<Vector3>?>()
    private val heights = HashMap<String, TerrainData?>()

    /** The asset folder [entity]'s render component names when it is of [type]; null otherwise. */
    fun assetName(entity: Entity, type: MetaType): String? {
        val asset = (entity.getComponent(RenderComponent::class.java)?.renderable as? RenderableObjectDelegate)?.asset ?: return null
        return asset.assetName.takeIf { asset.type == type }
    }

    /** Every vertex position of the model in [assetName], in model space (node transforms applied); null without one. */
    fun modelPoints(assetName: String): List<Vector3>? = points.getOrPut(assetName) {
        runCatchingKeepingCancellation {
            val prepared = loader.prepare(assetName) ?: return@runCatchingKeepingCancellation null
            try {
                (prepared.value as? PreparedModel)?.let { positions(it.data) }
            } finally {
                loader.discard(prepared)
            }
        }.getOrNull()
    }

    /** The heights of the terrain in [assetName]; null without terrain data. */
    fun terrain(assetName: String): TerrainData? = heights.getOrPut(assetName) {
        runCatchingKeepingCancellation {
            val prepared = loader.prepare(assetName) ?: return@runCatchingKeepingCancellation null
            try {
                (prepared.value as? PreparedTerrain)?.data
            } finally {
                loader.discard(prepared)
            }
        }.getOrNull()
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
