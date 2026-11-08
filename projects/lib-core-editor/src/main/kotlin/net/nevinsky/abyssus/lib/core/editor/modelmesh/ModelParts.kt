/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelmesh

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.assets.model.ModelPartMeta
import net.nevinsky.abyssus.lib.core.assets.model.PartHinge
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.assets.model.PivotRefusalReason
import net.nevinsky.abyssus.lib.core.assets.model.pivotRefusals
import net.nevinsky.abyssus.lib.gdx.model.ModelData

/** Whether a part's pivot entry applies: [NONE] without an entry, [APPLIED], or [REFUSED] with its [reason]. */
data class PartPivotStatus(val configured: Boolean, val refusal: PivotRefusalReason? = null) {
    val applicable: Boolean
        get() = refusal == null
}

/** No pivot entry. */
val NO_PIVOT = PartPivotStatus(false)

/** One part (a node with geometry, or an ancestor of one), named by its node name. */
data class ModelPart(
    val name: String,
    val children: List<ModelPart>,
    /** Bounds of the node's own geometry in the node's frame (after its pivot), null without own geometry. */
    val bounds: BoundingBox?,
    val pivot: PartPivotStatus,
    /** The hinge the asset gives this node, if any (valid or not). */
    val hinge: PartHinge? = null,
    /** Why a pivot cannot be applied to this node, whatever the asset says; null when one could. */
    val pivotRefusal: PivotRefusalReason? = null,
)

/** Triangles of one node in the node's frame: [positions] is x,y,z triples, [indices] triples into them. */
class PartTriangles(val positions: FloatArray, val indices: IntArray) {
    val triangleCount: Int
        get() = indices.size / 3

    fun vertex(i: Int, into: Vector3): Vector3 =
        into.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
}

/**
 * What the editor knows about the parts of a model asset (or of one mesh variant of it): the pruned [roots], the rest
 * transform of each node ([rest], pivots applied), its transform without its own pivot ([fileFrames]) and the triangles
 * of the nodes that draw geometry.
 */
class ModelPartsInfo(
    val roots: List<ModelPart>,
    val rest: Map<String, Matrix4>,
    val fileFrames: Map<String, Matrix4>,
    val triangles: Map<String, PartTriangles>,
    /** The asset's `parts` entries as read (by node name), for the numbers Properties shows. */
    val declared: Map<String, ModelPartMeta> = emptyMap(),
) {
    /** The number of parts, nested ones included. */
    val count: Int = roots.sumOf(::countOf)

    /** The names from a root part down to part [name] (itself last), or null when there is no such part. */
    fun pathTo(name: String): List<String>? = roots.firstNotNullOfOrNull { pathIn(it, name) }

    private fun pathIn(part: ModelPart, name: String): List<String>? =
        if (part.name == name) {
            listOf(name)
        } else {
            part.children.firstNotNullOfOrNull { pathIn(it, name) }?.let { listOf(part.name) + it }
        }

    fun find(name: String): ModelPart? = roots.firstNotNullOfOrNull { findIn(it, name) }

    private fun countOf(part: ModelPart): Int = 1 + part.children.sumOf(::countOf)

    private fun findIn(part: ModelPart, name: String): ModelPart? =
        if (part.name == name) part else part.children.firstNotNullOfOrNull { findIn(it, name) }
}

internal fun buildParts(
    data: ModelData,
    parts: Map<String, ModelPartMeta>?,
    refusals: Map<String, PivotRefusalReason>,
): ModelPartsInfo {
    val partMesh = HashMap<String, net.nevinsky.abyssus.lib.gdx.model.ModelMesh>()
    for (mesh in data.meshes) for (part in mesh.parts) part.id?.let { partMesh[it] = mesh }

    val rest = nodeRestTransforms(data)
    // whether a pivot could be applied to each node, with or without an entry for it
    val wouldRefuse = pivotRefusals(data, rest.keys.associateWith { ModelPartMeta(pivot = PartPivot()) })
        .associate { it.node to it.reason }
    val frames = LinkedHashMap<String, Matrix4>()
    for ((name, matrix) in rest) {
        val pivot = parts?.get(name)?.pivot
        frames[name] = if (pivot != null && name !in refusals) {
            Matrix4(matrix).mul(pivotMatrix(pivot).inv())
        } else {
            Matrix4(matrix)
        }
    }

    val triangles = HashMap<String, PartTriangles>()
    fun status(name: String?): PartPivotStatus {
        if (name == null || parts?.get(name)?.pivot == null) return NO_PIVOT
        return PartPivotStatus(true, refusals[name])
    }

    fun build(node: ModelNode): ModelPart? {
        val children = node.children.orEmpty().mapNotNull(::build)
        val own = ownGeometry(node, partMesh)
        val id = node.id
        if (own == null && children.isEmpty()) return null
        if (own != null && id != null) triangles.putIfAbsent(id, own.first)
        return ModelPart(id ?: "", children, own?.second, status(id), id?.let { parts?.get(it)?.hinge }, id?.let(wouldRefuse::get))
    }
    return ModelPartsInfo(data.nodes.mapNotNull(::build), rest, frames, triangles, parts.orEmpty())
}

/** The matrix of a pivot frame T(p) R(q). */
fun pivotMatrix(pivot: PartPivot): Matrix4 =
    Matrix4().set(
        pivot.position?.let { Vector3(it.x, it.y, it.z) } ?: Vector3(),
        pivot.rotation?.let { Quaternion(it.x, it.y, it.z, it.w).nor() } ?: Quaternion(),
    )

private fun ownGeometry(
    node: ModelNode,
    partMesh: Map<String, net.nevinsky.abyssus.lib.gdx.model.ModelMesh>,
): Pair<PartTriangles, BoundingBox>? {
    val positions = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val box = BoundingBox()
    var any = false
    for (nodePart in node.parts.orEmpty()) {
        val mesh = partMesh[nodePart.meshPartId] ?: continue
        val meshPart = mesh.parts.firstOrNull { it.id == nodePart.meshPartId } ?: continue
        var stride = 0
        var positionOffset = -1
        for (attribute in mesh.attributes) {
            if (attribute.usage == VertexAttributes.Usage.Position && positionOffset < 0) positionOffset = stride
            stride += attribute.numComponents
        }
        if (positionOffset < 0 || stride == 0) continue
        val remap = HashMap<Int, Int>()
        val v = Vector3()
        for (index in meshPart.indices) {
            val local = remap.getOrPut(index) {
                val base = index * stride + positionOffset
                v.set(mesh.vertices[base], mesh.vertices[base + 1], mesh.vertices[base + 2])
                positions += v.x
                positions += v.y
                positions += v.z
                box.ext(v)
                any = true
                positions.size / 3 - 1
            }
            indices += local
        }
    }
    if (!any) return null
    return PartTriangles(positions.toFloatArray(), indices.toIntArray()) to box
}
