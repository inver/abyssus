/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.baseAssetName
import net.nevinsky.abyssus.lib.core.assets.forVariant
import net.nevinsky.abyssus.lib.core.assets.isModelVariant
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.assets.model.pivotRefusals
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import java.util.concurrent.ConcurrentHashMap

/** One mesh of a model asset: its [id] (what `meshId` names) and the ids of the materials its parts use. */
data class ModelMeshInfo(val id: String, val materials: List<String>)

/** One material of a model asset: its [id] and its [emissive] color, null when it has none or it is black. */
data class ModelMaterialInfo(val id: String, val emissive: Color?)

/**
 * The meshes of model assets, for the mesh choosers. Reads the geometry only (no image is decoded, no GL), so it may
 * run on any thread, but it parses the model file: call it off the UI thread. The answer for an asset is kept until
 * [invalidate] drops it.
 */
class ModelMeshLister(
    files: FileLoader,
    metas: AssetMetaLoader,
    assimp: AssimpModelLoader = AssimpModelLoader(),
    /** Where an asset's base meta comes from; an editor passes one that sees unsaved text. */
    private val metaSource: (String) -> AssetMeta<Any>? = metas::loadBaseMeta,
) {
    private val loader = ModelLoader(metas, assimp, files, decodeTextures = false)
    private val known = ConcurrentHashMap<String, List<ModelMeshInfo>>()
    private val knownMaterials = ConcurrentHashMap<String, List<ModelMaterialInfo>>()
    private val knownNodes = ConcurrentHashMap<String, Map<String, Matrix4>>()
    private val knownParts = ConcurrentHashMap<String, ModelPartsInfo>()

    /** Prepares model [assetName] (a variant name reads its base asset's meta); null when it is missing or unreadable. */
    private fun prepare(assetName: String) = runCatchingKeepingCancellation {
        metaSource(baseAssetName(assetName))?.let { base ->
            loader.loadPrepared(if (isModelVariant(assetName)) base.forVariant(assetName) else base)
        }
    }.getOrNull()

    /** The meshes of the model asset [assetName], in the model's order; null when it is missing or cannot be read. */
    fun list(assetName: String): List<ModelMeshInfo>? {
        known[assetName]?.let { return it }
        val prepared = prepare(assetName) ?: return null
        val infos = try {
            knownMaterials[assetName] = materialsOf(prepared.staged.data)
            knownNodes[assetName] = nodeRestTransforms(prepared.staged.data)
            meshesOf(prepared.staged.data)
        } finally {
            loader.discardStaged(prepared.staged)
        }
        return known.putIfAbsent(assetName, infos) ?: infos
    }

    /** The materials of the model asset [assetName], in the model's order; null when it cannot be read. */
    fun materials(assetName: String): List<ModelMaterialInfo>? {
        list(assetName) ?: return null
        return knownMaterials[assetName]
    }

    private fun materialsOf(data: ModelData): List<ModelMaterialInfo> =
        data.materials.mapNotNull { material ->
            val id = material.id ?: return@mapNotNull null
            val emissive = material.emissive?.takeIf { it.r > 0f || it.g > 0f || it.b > 0f }
            ModelMaterialInfo(id, emissive?.let(::Color))
        }

    /**
     * The rest transform within the model of every named node of model asset [assetName] (node id to matrix, parents'
     * transforms applied), already read; null before [list] or after [invalidate]. Never reads.
     */
    fun cachedNodes(assetName: String): Map<String, Matrix4>? = knownNodes[assetName]

    /** The node rest transforms of [assetName], reading the model when needed (off the UI thread); null when
     * unreadable. */
    fun nodes(assetName: String): Map<String, Matrix4>? {
        list(assetName) ?: return null
        return knownNodes[assetName]
    }

    /**
     * The parts of the model asset [assetName] (an encoded variant name lists only that mesh's nodes and their
     * ancestors): the pruned node tree with own bounds and pivot status, node frames and triangles. Reads the model when
     * needed (off the UI thread); null when it cannot be read.
     */
    fun parts(assetName: String): ModelPartsInfo? {
        knownParts[assetName]?.let { return it }
        val prepared = prepare(assetName) ?: return null
        val info = try {
            val declared = runCatchingKeepingCancellation {
                metaSource(baseAssetName(assetName))?.typedAdditional<ModelMeta>()?.parts
            }.getOrNull()
            val refusals = pivotRefusals(prepared.staged.data, declared).associate { it.node to it.reason }
            buildParts(prepared.staged.data, declared, refusals)
        } finally {
            loader.discardStaged(prepared.staged)
        }
        return knownParts.putIfAbsent(assetName, info) ?: info
    }

    /** The parts already read for [assetName], without reading; null before [parts] or after [invalidate]. */
    fun cachedParts(assetName: String): ModelPartsInfo? = knownParts[assetName]

    /**
     * The rest transform of [node] in the model of [assetName] as the model file has it, without the node's own pivot
     * (`rest x pivot^-1`); null when the model or node is unknown. Reads when needed.
     */
    fun fileFrame(assetName: String, node: String): Matrix4? = parts(assetName)?.fileFrames?.get(node)

    /** The answer already read for [assetName], without reading; null before [list] or after [invalidate]. */
    fun cached(assetName: String): List<ModelMeshInfo>? = known[assetName]

    /** Forgets [assetName], whose files changed. */
    fun invalidate(assetName: String) {
        // Variants are stored under their encoded name; they go with the asset.
        known.keys.removeIf { baseAssetName(it) == assetName }
        knownMaterials.keys.removeIf { baseAssetName(it) == assetName }
        knownNodes.keys.removeIf { baseAssetName(it) == assetName }
        knownParts.keys.removeIf { baseAssetName(it) == assetName }
    }

    /** Forgets everything. */
    fun clear() {
        known.clear()
        knownMaterials.clear()
        knownNodes.clear()
        knownParts.clear()
    }

    private fun meshesOf(data: ModelData): List<ModelMeshInfo> {
        val materialOfPart = LinkedHashMap<String, MutableList<String>>()
        fun walk(node: ModelNode) {
            node.parts?.forEach { part ->
                val material = part.materialId ?: return@forEach
                materialOfPart.getOrPut(part.meshPartId) { mutableListOf() }.let {
                    if (material !in it) {
                        it += material
                    }
                }
            }
            node.children?.forEach(::walk)
        }
        data.nodes.forEach(::walk)
        return data.meshes.mapNotNull { mesh ->
            val id = mesh.id ?: return@mapNotNull null
            ModelMeshInfo(id, mesh.parts.flatMap { materialOfPart[it.id].orEmpty() }.distinct())
        }
    }
}

/**
 * The rest transform within the model of every node of [data] that has an id: each node's translation, rotation and
 * scale composed under its parents'. The first node of a name wins.
 */
fun nodeRestTransforms(data: ModelData): Map<String, Matrix4> {
    val out = LinkedHashMap<String, Matrix4>()
    fun walk(node: ModelNode, parent: Matrix4) {
        val local = Matrix4().set(
            node.translation ?: Vector3(),
            node.rotation ?: Quaternion(),
            node.scale ?: Vector3(1f, 1f, 1f),
        )
        val global = Matrix4(parent).mul(local)
        node.id?.let { out.putIfAbsent(it, global) }
        node.children?.forEach { walk(it, global) }
    }
    data.nodes.forEach { walk(it, Matrix4()) }
    return out
}
