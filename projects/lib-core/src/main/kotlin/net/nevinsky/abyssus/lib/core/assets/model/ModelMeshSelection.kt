/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.utils.Array
import net.nevinsky.abyssus.lib.core.assets.loading.exception.UnknownMeshException
import net.nevinsky.abyssus.lib.gdx.model.ModelData

/**
 * Cuts [data] down to the one mesh [meshId], in place: the other meshes go, the materials only the mesh uses stay, and
 * node parts that point at dropped mesh parts are pruned. The node tree itself (transforms, bones, animations) is kept,
 * so the mesh sits where it does in the whole model. Throws [UnknownMeshException] naming the model's meshes when it
 * has none by that id; [data] is then untouched.
 */
fun selectMesh(data: ModelData, assetName: String, meshId: String) {
    val mesh = data.meshes.firstOrNull { it.id == meshId }
        ?: throw UnknownMeshException(assetName, meshId, data.meshes.map { it.id.orEmpty() })
    val partIds = mesh.parts.mapNotNullTo(HashSet()) { it.id }

    val usedMaterials = HashSet<String>()
    for (node in data.nodes) pruneNodeParts(node, partIds, usedMaterials)

    data.meshes.clear()
    data.meshes.add(mesh)
    val kept = Array<com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial>()
    for (material in data.materials) if (material.id in usedMaterials) kept.add(material)
    data.materials.clear()
    data.materials.addAll(kept)
}

private fun pruneNodeParts(node: ModelNode, partIds: Set<String>, usedMaterials: MutableSet<String>) {
    val parts = node.parts
    if (parts != null && parts.isNotEmpty()) {
        val keep = parts.filter { it.meshPartId in partIds }
        node.parts = keep.toTypedArray()
        for (part in keep) part.materialId?.let(usedMaterials::add)
    }
    node.children?.forEach { pruneNodeParts(it, partIds, usedMaterials) }
}
