/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart

/** A flat [size] x [size] vertex grid on the XZ plane, `x` and `z` in `0 until size`, as a position-only [IntMesh]. */
fun gridMesh(size: Int, create: (Int, Int, Int, Array<VertexAttribute>) -> IntMesh = { v, i, _, a ->
    IntMesh(true, v, i, *a)
}): IntMesh {
    val vertexCount = size * size
    val indexCount = (size - 1) * (size - 1) * 6
    val mesh = create(vertexCount, indexCount, 0, arrayOf(VertexAttribute.Position()))
    val vertices = FloatArray(vertexCount * 3)
    for (z in 0 until size) for (x in 0 until size) {
        val at = (z * size + x) * 3
        vertices[at] = x.toFloat()
        vertices[at + 2] = z.toFloat()
    }
    val indices = IntArray(indexCount)
    var n = 0
    for (z in 0 until size - 1) for (x in 0 until size - 1) {
        val a = z * size + x
        val b = a + 1
        val c = a + size
        val d = c + 1
        indices[n++] = a; indices[n++] = c; indices[n++] = b
        indices[n++] = b; indices[n++] = c; indices[n++] = d
    }
    mesh.setVertices(vertices)
    mesh.setIndices(indices)
    return mesh
}

/** A stock [Model] with one node drawing all of [mesh] with [material]; the model owns the mesh. */
fun singleMeshModel(mesh: IntMesh, material: Material = Material()): Model {
    val part = MeshPart("part", mesh, 0, mesh.numIndices, GL20.GL_TRIANGLES)
    val model = Model()
    model.meshes.add(mesh)
    model.meshParts.add(part)
    model.materials.add(material)
    model.nodes.add(Node().apply {
        id = "node"
        parts.add(NodePart(part, material))
    })
    model.manageDisposable(mesh)
    return model
}
