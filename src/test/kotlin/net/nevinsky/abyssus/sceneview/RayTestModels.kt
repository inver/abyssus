/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.core.assets.model.ModelRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.model.RayModelSource
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.core.model.ModelMesh
import net.nevinsky.abyssus.core.model.ModelMeshPart

/** One node with two mesh parts (red, green) over a shared mesh, read the way the scene view reads models. */
internal fun rayTestModel(count: Int = 3, pbr: Boolean = false): RayModelSnapshot {
        val data = ModelData()
        data.meshes.add(ModelMesh().apply {
            id = "mesh"; attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
            vertices = FloatArray(count * 8).also { values ->
                for (i in 0 until count) { values[i * 8] = i.toFloat(); values[i * 8 + 4] = 1f; values[i * 8 + 6] = .25f; values[i * 8 + 7] = .75f }
            }
            parts = arrayOf("red", "green").map { name -> ModelMeshPart().apply {
                id = name; primitiveType = GL20.GL_TRIANGLES; indices = intArrayOf(0, count - 2, count - 1)
            } }.toTypedArray()
        })
        listOf("red" to Color.RED, "green" to Color.GREEN).forEach { (name, color) ->
            data.materials.add(if (pbr) net.nevinsky.abyssus.core.model.PbrModelMaterial().apply { id = name; diffuse = Color(color); baseColor = Color(color); metallic = 0f; roughness = 0.2f } else ModelMaterial().apply { id = name; diffuse = Color(color) })
        }
        data.nodes.add(ModelNode().apply {
            id = "node"; translation = Vector3(0f, 1f, 0f)
            parts = arrayOf("red", "green").map { name -> ModelNodePart().apply { meshPartId = name; materialId = name } }.toTypedArray()
        })
        return ModelRaySnapshotLoader(net.nevinsky.abyssus.core.FileLoader(java.io.File(".")), AssimpModelLoader()).capture(RayModelSource(data, emptyMap()))
    }
