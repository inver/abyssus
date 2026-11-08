/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh

/** Why a pivot could not be applied; the editor shows the matching text. */
enum class PivotRefusalReason {
    UNKNOWN_NODE,
    SHARED_MESH,
    SHARED_PART,
    SKINNED,
    NON_UNIFORM_SCALE,
    ANIMATED,
    INVALID_VALUE,
}

data class PivotRefusal(val node: String, val reason: PivotRefusalReason)

/** The nodes whose pivot was applied and the entries that were refused (those nodes are left as in the file). */
data class PivotResult(val applied: List<String>, val refused: List<PivotRefusal>)

/** A reason the hinge is invalid, or null: `min <= 0 <= max`, both finite and within +-180 degrees. */
fun hingeProblem(hinge: PartHinge): String? =
    when {
        !hinge.min.isFinite() || !hinge.max.isFinite() -> "limits must be finite"
        hinge.min > 0f -> "min must be at most 0"
        hinge.max < 0f -> "max must be at least 0"
        hinge.min < -180f || hinge.max > 180f -> "limits must be within -180 and 180"
        else -> null
    }

/**
 * Moves the origin and axes of nodes of [data] to the pivot frames of [parts], in place. For a node with frame
 * F = T(p) R(q) in the file, the node's transform becomes node x F, its vertices (and normals, tangents, binormals) go
 * through F^-1 and each child node becomes F^-1 x child, so in the rest pose everything stays where it was. A pivot that
 * cannot be applied is returned as a refusal and the node is left as it is; the other nodes are unaffected.
 */
fun applyPivots(data: ModelData, parts: Map<String, ModelPartMeta>?): PivotResult {
    if (parts.isNullOrEmpty()) return PivotResult(emptyList(), emptyList())
    val index = ModelIndex(data)
    val refused = pivotRefusals(data, parts, index)
    val applied = ArrayList<String>()
    for ((name, entry) in parts) {
        val pivot = entry.pivot ?: continue
        if (refused.any { it.node == name }) continue
        apply(checkNotNull(index.nodes[name]), pivot, index.meshOfPart)
        applied += name
    }
    return PivotResult(applied, refused)
}

/**
 * The pivots of [parts] that cannot be applied to [data], without changing it. The causes (missing node, sharing,
 * skinning, scale, animation) do not depend on an earlier pivot, so this gives the same answer before and after
 * [applyPivots].
 */
fun pivotRefusals(data: ModelData, parts: Map<String, ModelPartMeta>?): List<PivotRefusal> =
    if (parts.isNullOrEmpty()) emptyList() else pivotRefusals(data, parts, ModelIndex(data))

private class ModelIndex(data: ModelData) {
    val nodes = HashMap<String, ModelNode>()
    val owners = ArrayList<ModelNode>()
    val meshOfPart = HashMap<String, ModelMesh>()
    val animated = HashSet<String>()

    init {
        fun collect(node: ModelNode) {
            node.id?.let { nodes.putIfAbsent(it, node) }
            owners += node
            node.children?.forEach(::collect)
        }
        data.nodes.forEach(::collect)
        for (mesh in data.meshes) for (part in mesh.parts) part.id?.let { meshOfPart[it] = mesh }
        for (animation in data.animations) for (channel in animation.nodeAnimations ?: continue) {
            channel.nodeId?.let(animated::add)
        }
    }
}

private fun pivotRefusals(data: ModelData, parts: Map<String, ModelPartMeta>, index: ModelIndex): List<PivotRefusal> {
    val refused = ArrayList<PivotRefusal>()
    for ((name, entry) in parts) {
        val pivot = entry.pivot ?: continue
        val node = index.nodes[name]
        val reason = when {
            node == null -> PivotRefusalReason.UNKNOWN_NODE
            !finite(pivot) -> PivotRefusalReason.INVALID_VALUE
            else -> refusal(node, index.owners, index.meshOfPart, index.animated)
        }
        if (reason != null) refused += PivotRefusal(name, reason)
    }
    return refused
}

private fun finite(pivot: PartPivot): Boolean {
    val p = pivot.position
    val q = pivot.rotation
    return (p == null || (p.x.isFinite() && p.y.isFinite() && p.z.isFinite())) &&
        (q == null || (q.x.isFinite() && q.y.isFinite() && q.z.isFinite() && q.w.isFinite() &&
            q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w > 1e-12f))
}

private fun refusal(
    node: ModelNode,
    owners: List<ModelNode>,
    meshOfPart: Map<String, ModelMesh>,
    animated: Set<String>,
): PivotRefusalReason? {
    val own = node.parts.orEmpty()
    if (own.any { (it.bones?.size ?: 0) > 0 }) return PivotRefusalReason.SKINNED
    val s = node.scale
    if (s != null) {
        val tolerance = 1e-5f * max(1f, max(abs(s.x), max(abs(s.y), abs(s.z))))
        if (abs(s.x - s.y) > tolerance || abs(s.y - s.z) > tolerance) return PivotRefusalReason.NON_UNIFORM_SCALE
    }
    if (node.id in animated) return PivotRefusalReason.ANIMATED
    val ownParts = own.mapNotNullTo(HashSet()) { it.meshPartId }
    val ownMeshes = ownParts.mapNotNullTo(HashSet()) { meshOfPart[it] }
    for (other in owners) {
        if (other === node) continue
        for (part in other.parts.orEmpty()) {
            val id = part.meshPartId ?: continue
            if (id in ownParts) return PivotRefusalReason.SHARED_PART
            if (meshOfPart[id] in ownMeshes) return PivotRefusalReason.SHARED_MESH
        }
    }
    return null
}

private fun apply(node: ModelNode, pivot: PartPivot, meshOfPart: Map<String, ModelMesh>) {
    val p = pivot.position?.let { Vector3(it.x, it.y, it.z) } ?: Vector3()
    val q = pivot.rotation?.let { Quaternion(it.x, it.y, it.z, it.w).nor() } ?: Quaternion()
    val inverse = Quaternion(q).conjugate()

    val scale = node.scale?.x ?: 1f
    val rotation = node.rotation?.let { Quaternion(it) } ?: Quaternion()
    val shift = Vector3(p).scl(scale).mul(rotation)
    node.translation = (node.translation ?: Vector3()).add(shift)
    node.rotation = Quaternion(rotation).mul(q)

    node.children?.forEach { child ->
        val t = Vector3(child.translation ?: Vector3()).sub(p).mul(inverse)
        child.translation = t
        child.rotation = Quaternion(inverse).mul(child.rotation ?: Quaternion())
    }

    for (part in node.parts.orEmpty()) {
        val mesh = part.meshPartId?.let(meshOfPart::get) ?: continue
        val meshPart = mesh.parts.first { it.id == part.meshPartId }
        transformVertices(mesh, meshPart.indices, p, inverse)
    }
}

private fun transformVertices(
    mesh: ModelMesh,
    indices: IntArray,
    p: Vector3,
    inverse: Quaternion,
) {
    var stride = 0
    val offsets = HashMap<Int, Int>()
    for (attribute in mesh.attributes) {
        offsets.putIfAbsent(attribute.usage, stride)
        stride += attribute.numComponents
    }
    val vertices = mesh.vertices
    val seen = java.util.BitSet()
    val v = Vector3()
    for (index in indices) {
        if (seen.get(index)) continue
        seen.set(index)
        val base = index * stride
        offsets[VertexAttributes.Usage.Position]?.let { o ->
            v.set(vertices[base + o], vertices[base + o + 1], vertices[base + o + 2]).sub(p).mul(inverse)
            vertices[base + o] = v.x
            vertices[base + o + 1] = v.y
            vertices[base + o + 2] = v.z
        }
        for (usage in intArrayOf(
            VertexAttributes.Usage.Normal,
            VertexAttributes.Usage.Tangent,
            VertexAttributes.Usage.BiNormal,
        )) {
            offsets[usage]?.let { o ->
                v.set(vertices[base + o], vertices[base + o + 1], vertices[base + o + 2]).mul(inverse)
                vertices[base + o] = v.x
                vertices[base + o + 1] = v.y
                vertices[base + o + 2] = v.z
            }
        }
    }
}
