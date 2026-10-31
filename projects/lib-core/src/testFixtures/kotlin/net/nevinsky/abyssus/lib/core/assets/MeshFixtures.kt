/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import net.nevinsky.abyssus.lib.gdx.gltf.GltfWriter
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import java.io.File

/** Two textureless quads, `Body` (material `skin`, at y 0) and `Wing` (material `glass`, at y 1), one node each. */
fun twoQuadData(): ModelData {
    val data = ModelData()
    for (i in 0..1) {
        val y = i.toFloat()
        data.addMesh(ModelMesh().apply {
            id = "mesh_$i"
            attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
            vertices = floatArrayOf(
                0f, y, 0f, 0f, 1f, 0f, 0f, 0f,
                1f, y, 0f, 0f, 1f, 0f, 1f, 0f,
                1f, y, 1f, 0f, 1f, 0f, 1f, 1f,
                0f, y, 1f, 0f, 1f, 0f, 0f, 1f,
            )
            parts = arrayOf(ModelMeshPart().apply {
                id = "part_$i"; indices = intArrayOf(0, 1, 2, 0, 2, 3); primitiveType = GL20.GL_TRIANGLES
            })
        })
    }
    data.materials.add(PbrModelMaterial().apply { id = "skin"; baseColor = Color.WHITE; metallic = 0f; roughness = 0.8f })
    data.materials.add(PbrModelMaterial().apply { id = "glass"; baseColor = Color(0.2f, 0.3f, 0.4f, 0.5f); metallic = 0f; roughness = 0.8f })
    for ((name, i) in listOf("Body" to 0, "Wing" to 1)) {
        data.nodes.add(ModelNode().apply {
            id = name
            parts = arrayOf(ModelNodePart().apply { meshPartId = "part_$i"; materialId = if (i == 0) "skin" else "glass" })
        })
    }
    return data
}

/** Writes `assets/[name]` into [projectDir]: a `meta.json` and a `model.glb` holding [data]. */
fun writeModelAsset(projectDir: File, name: String, data: ModelData) {
    val folder = File(projectDir, "assets/$name").also { it.mkdirs() }
    File(folder, "model.glb").writeBytes(GltfWriter().write(data, emptyMap(), "test"))
    File(folder, "meta.json").writeText(
        """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"29e9be61-6594-4f82-a6cf-44ccf09f71fb","type":"MODEL","additional":{"file":"model.glb","format":"GLTF","binary":true,"materials":[]}}"""
    )
}
