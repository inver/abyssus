/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assimp

import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ArrayMap
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import org.lwjgl.assimp.AIMatrix4x4
import org.lwjgl.assimp.AINode

/**
 * Walks the Assimp node tree, producing [ModelNode]s with unique ids and decomposed local transforms.
 */
internal class NodeProcessor(
    private val meshesByIndex: MutableMap<Int, ModelMesh>,
    private val materialIdByMeshIndex: MutableMap<Int, String>,
    private val skinsByMeshIndex: MutableMap<Int, MeshProcessor.Skin>
) {
    private val usedIds: MutableSet<String> = HashSet<String>()
    private val idsByName: MutableMap<String, String> = HashMap<String, String>()
    private val pendingSkins: MutableList<PendingSkin> = ArrayList<PendingSkin>()

    private class PendingSkin(val part: ModelNodePart, val skin: MeshProcessor.Skin)

    /** Converts the tree and binds skin bones to the (unique) node ids.  */
    fun process(root: AINode): ModelNode {
        val node = build(root)
        for (pending in pendingSkins) {
            val bones = ArrayMap<String, Matrix4>(pending.skin.names.size)
            for (i in pending.skin.names.indices) {
                val name = pending.skin.names.get(i)
                bones.put(idsByName.getOrDefault(name, name), pending.skin.offsets.get(i))
            }
            pending.part.bones = bones
        }
        return node
    }

    /** @return the id of the first node with the given Assimp name, or the name itself if there is none
     */
    fun idForName(name: String?): String? {
        return idsByName.getOrDefault(name, name)
    }

    private fun build(aiNode: AINode): ModelNode {
        val node = ModelNode()
        val name = aiNode.mName().dataString()
        node.id = uniqueId(name)
        idsByName.putIfAbsent(name, node.id)

        val matrix: Matrix4 = toMatrix4(aiNode.mTransformation())
        val translation = matrix.getTranslation(Vector3())
        val scale = matrix.getScale(Vector3())
        val rotation = matrix.getRotation(Quaternion(), true)
        node.translation = if (translation.isZero()) null else translation
        node.scale = if (scale.epsilonEquals(1f, 1f, 1f, 1e-6f)) null else scale
        node.rotation = if (rotation.isIdentity()) null else rotation

        val parts = ArrayList<ModelNodePart>()
        val meshIndices = aiNode.mMeshes()
        for (i in 0..<aiNode.mNumMeshes()) {
            val meshIndex = meshIndices!!.get(i)
            val mesh = meshesByIndex.get(meshIndex)
            if (mesh == null) {
                continue  // skipped (non-triangle) mesh
            }
            val nodePart = ModelNodePart()
            nodePart.meshPartId = mesh.parts[0].id
            nodePart.materialId = materialIdByMeshIndex.get(meshIndex)
            val skin = skinsByMeshIndex.get(meshIndex)
            if (skin != null) {
                pendingSkins.add(PendingSkin(nodePart, skin))
            }
            parts.add(nodePart)
            if (node.meshId == null) {
                node.meshId = mesh.id
            }
        }
        if (!parts.isEmpty()) {
            node.parts = parts.toTypedArray<ModelNodePart>()
        }

        val childCount = aiNode.mNumChildren()
        if (childCount > 0) {
            node.children = arrayOfNulls<ModelNode>(childCount)
            val children = aiNode.mChildren()
            for (i in 0..<childCount) {
                node.children[i] = build(AINode.create(children!!.get(i)))
            }
        }
        return node
    }

    private fun uniqueId(name: String?): String {
        val base = if (name == null || name.isEmpty()) "node" else name
        var id = base
        var n = 1
        while (!usedIds.add(id)) {
            id = base + "_" + n
            n++
        }
        return id
    }

    companion object {
        /** Assimp matrices are row-major, LibGDX [Matrix4.set] is column-major.  */
        fun toMatrix4(m: AIMatrix4x4): Matrix4 {
            return Matrix4(
                floatArrayOf(
                    m.a1(), m.b1(), m.c1(), m.d1(),
                    m.a2(), m.b2(), m.c2(), m.d2(),
                    m.a3(), m.b3(), m.c3(), m.d3(),
                    m.a4(), m.b4(), m.c4(), m.d4()
                )
            )
        }
    }
}
