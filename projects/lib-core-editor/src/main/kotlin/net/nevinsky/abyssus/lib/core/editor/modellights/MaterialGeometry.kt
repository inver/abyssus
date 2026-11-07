/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh

/**
 * The triangles of one material of a model, in the model's own space (every node transform applied): [positions] holds
 * x, y, z per vertex and [triangles] three vertex numbers per triangle. [emissive] is the material's emissive color,
 * null when it has none or it is black.
 */
class MaterialGeometry(
    val materialId: String,
    val emissive: Color?,
    val positions: FloatArray,
    val triangles: IntArray,
)

/** The geometry of every material of [data] that has triangles, in the materials' order. Reads CPU data only. */
fun materialGeometries(data: ModelData): List<MaterialGeometry> {
    val meshOfPart = HashMap<String, Pair<ModelMesh, Int>>()
    for (mesh in data.meshes) {
        mesh.parts.forEachIndexed { index, part -> part.id?.let { meshOfPart[it] = mesh to index } }
    }
    val builders = LinkedHashMap<String, Builder>()
    fun walk(node: ModelNode, parent: Matrix4) {
        val world = Matrix4(parent).mul(localTransform(node))
        node.parts?.forEach { nodePart ->
            val material = nodePart.materialId ?: return@forEach
            val (mesh, index) = meshOfPart[nodePart.meshPartId] ?: return@forEach
            val part = mesh.parts[index]
            if (part.primitiveType != GL20.GL_TRIANGLES) {
                return@forEach
            }
            builders.getOrPut(material) { Builder() }.add(mesh.vertices, mesh.attributes, part.indices, world)
        }
        node.children?.forEach { walk(it, world) }
    }
    data.nodes.forEach { walk(it, Matrix4()) }
    return data.materials.mapNotNull { material ->
        val id = material.id ?: return@mapNotNull null
        val builder = builders[id]?.takeIf { it.triangleCount > 0 } ?: return@mapNotNull null
        val emissive = material.emissive?.takeIf { it.r > 0f || it.g > 0f || it.b > 0f }
        MaterialGeometry(id, emissive?.let(::Color), builder.positions(), builder.triangles())
    }
}

private fun localTransform(node: ModelNode): Matrix4 =
    Matrix4()
        .set(
            node.translation ?: Vector3(),
            node.rotation ?: Quaternion(),
            node.scale ?: Vector3(1f, 1f, 1f),
        )

private class Builder {
    private val xyz = ArrayList<Float>()
    private val tris = ArrayList<Int>()
    val triangleCount: Int
        get() = tris.size / 3

    fun add(
        vertices: FloatArray,
        attributes: Array<com.badlogic.gdx.graphics.VertexAttribute>,
        indices: IntArray,
        world: Matrix4,
    ) {
        val stride = attributes.sumOf { it.numComponents }
        if (stride == 0) {
            return
        }
        var offset = 0
        var found = false
        for (attribute in attributes) {
            if (attribute.usage == VertexAttributes.Usage.Position) {
                found = true
                break
            }
            offset += attribute.numComponents
        }
        if (!found) {
            return
        }
        val seen = HashMap<Int, Int>()
        val point = Vector3()
        fun inRange(vertex: Int) = vertex >= 0 && vertex * stride + offset + 2 < vertices.size
        fun numberOf(vertex: Int): Int =
            seen.getOrPut(vertex) {
                val at = vertex * stride + offset
                point.set(vertices[at], vertices[at + 1], vertices[at + 2]).mul(world)
                xyz.add(point.x)
                xyz.add(point.y)
                xyz.add(point.z)
                xyz.size / 3 - 1
            }
        for (i in 0 until indices.size - indices.size % 3 step 3) {
            // a triangle that points outside the vertex data is dropped whole
            if (inRange(indices[i]) && inRange(indices[i + 1]) && inRange(indices[i + 2])) {
                tris.add(numberOf(indices[i]))
                tris.add(numberOf(indices[i + 1]))
                tris.add(numberOf(indices[i + 2]))
            }
        }
    }

    fun positions(): FloatArray = xyz.toFloatArray()

    fun triangles(): IntArray = tris.toIntArray()
}
