/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.editor.ray

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.lib.core.assets.model.ModelRaySnapshotLoader
import net.nevinsky.abyssus.lib.core.assets.model.RayModelSource
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import java.io.File

/** One node with two mesh parts (red, green) over a shared mesh, read the way the scene view reads models. */
fun rayTestModel(count: Int = 3, pbr: Boolean = false): RayModelSnapshot {
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
            data.materials.add(if (pbr) net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial().apply { id = name; diffuse = Color(color); baseColor = Color(color); metallic = 0f; roughness = 0.2f } else ModelMaterial().apply { id = name; diffuse = Color(color) })
        }
        data.nodes.add(ModelNode().apply {
            id = "node"; translation = Vector3(0f, 1f, 0f)
            parts = arrayOf("red", "green").map { name -> ModelNodePart().apply { meshPartId = name; materialId = name } }.toTypedArray()
        })
        return ModelRaySnapshotLoader(FileLoader(File(".")), AssimpModelLoader()).capture(RayModelSource(data, emptyMap()))
    }
