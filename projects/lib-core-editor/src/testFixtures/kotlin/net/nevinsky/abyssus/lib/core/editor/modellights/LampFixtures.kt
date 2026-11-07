/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart

/** Unit quads at the given x offsets (4 vertices, 2 triangles each) of material "lamp", plus an unused "unused". */
fun lampsAt(vararg xs: Float, rootScale: Float = 1f): ModelData {
    val vertices = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (x in xs) {
        val base = vertices.size / 3
        for ((dx, dy) in listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)) {
            vertices += listOf(x + dx, dy, 0f)
        }
        indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
    }
    val data = ModelData()
    data.addMesh(
        ModelMesh().apply {
            id = "m"
            attributes = arrayOf(VertexAttribute.Position())
            this.vertices = vertices.toFloatArray()
            parts =
                arrayOf(
                    ModelMeshPart().apply {
                        id = "p"
                        this.indices = indices.toIntArray()
                        primitiveType = GL20.GL_TRIANGLES
                    }
                )
        }
    )
    data.materials.add(
        ModelMaterial().apply {
            id = "lamp"
            emissive = Color(0.4f, 0.8f, 1f, 1f)
        }
    )
    data.materials.add(ModelMaterial().apply { id = "unused" })
    data.nodes.add(
        ModelNode().apply {
            id = "root"
            scale = Vector3(rootScale, rootScale, rootScale)
            translation = Vector3()
            rotation = Quaternion()
            parts =
                arrayOf(
                    ModelNodePart().apply {
                        meshPartId = "p"
                        materialId = "lamp"
                    }
                )
        }
    )
    return data
}
