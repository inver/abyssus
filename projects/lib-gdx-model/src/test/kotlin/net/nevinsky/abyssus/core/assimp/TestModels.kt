/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assimp

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.core.model.ModelData
import java.io.File
import java.nio.file.Files

/** Copies a test resource into its own temp folder, keeping its file name. */
internal fun resourceCopy(path: String, name: String = path.substringAfterLast('/')): File {
    val dir = Files.createTempDirectory("abyssus_assimp").toFile()
    val target = File(dir, name)
    object {}.javaClass.getResourceAsStream(path)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
    return target
}

internal fun ModelNode.local(): Matrix4 =
    Matrix4().set(translation ?: Vector3(), rotation ?: Quaternion(), scale ?: Vector3(1f, 1f, 1f))

/** The rest-pose bounds of every vertex used by a node part, through the node transforms. */
internal fun ModelData.restBounds(): BoundingBox {
    val box = BoundingBox().inf()
    val meshOfPart = meshes.flatMap { mesh -> mesh.parts.map { it.id to mesh } }.toMap()
    val partById = meshes.flatMap { it.parts.toList() }.associateBy { it.id }
    fun walk(node: ModelNode, parent: Matrix4) {
        val world = Matrix4(parent).mul(node.local())
        for (part in node.parts.orEmpty()) {
            val mesh = meshOfPart[part.meshPartId] ?: continue
            val stride = mesh.attributes.sumOf { it.numComponents }
            val offset = mesh.attributes.takeWhile { it.usage != VertexAttributes.Usage.Position }.sumOf { it.numComponents }
            for (i in partById[part.meshPartId]!!.indices) {
                val v = Vector3(mesh.vertices[i * stride + offset], mesh.vertices[i * stride + offset + 1], mesh.vertices[i * stride + offset + 2])
                box.ext(v.mul(world))
            }
        }
        node.children.orEmpty().forEach { walk(it, world) }
    }
    nodes.forEach { walk(it, Matrix4()) }
    return box
}
