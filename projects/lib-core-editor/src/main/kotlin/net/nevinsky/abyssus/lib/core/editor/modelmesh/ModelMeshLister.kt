/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import java.util.concurrent.ConcurrentHashMap

/** One mesh of a model asset: its [id] (what `meshId` names) and the ids of the materials its parts use. */
data class ModelMeshInfo(val id: String, val materials: List<String>)

/**
  * The meshes of model assets, for the mesh choosers. Reads the geometry only (no image is decoded, no GL), so it may
  * run
 * on any thread, but it parses the model file: call it off the UI thread. The answer for an asset is kept until
 * [invalidate] drops it.
 */
class ModelMeshLister(files: FileLoader, metas: AssetMetaLoader, assimp: AssimpModelLoader = AssimpModelLoader()) {
    private val loader = ModelLoader(metas, assimp, files, decodeTextures = false)
    private val known = ConcurrentHashMap<String, List<ModelMeshInfo>>()

    /** The meshes of the model asset [assetName], in the model's order; null when it is missing or cannot be read. */
    fun list(assetName: String): List<ModelMeshInfo>? {
        known[assetName]?.let { return it }
        val prepared = runCatchingKeepingCancellation { loader.prepare(assetName) }.getOrNull() ?: return null
        val infos = try {
            meshesOf(prepared.staged.data)
        } finally {
            loader.discardStaged(prepared.staged)
        }
        return known.putIfAbsent(assetName, infos) ?: infos
    }

    /** The answer already read for [assetName], without reading; null before [list] or after [invalidate]. */
    fun cached(assetName: String): List<ModelMeshInfo>? = known[assetName]

    /** Forgets [assetName], whose files changed. */
    fun invalidate(assetName: String) {
        known.remove(assetName)
    }

    /** Forgets everything. */
    fun clear() = known.clear()

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
