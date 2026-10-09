/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.modelimport

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import java.io.File
import java.nio.file.Files

/** A fresh copy of the model import fixtures (`src/test/resources/modelimport`), so tests may look at the folder. */
fun importFixtures(): File {
    val source = File(object {}.javaClass.getResource("/modelimport/crate.obj")!!.toURI()).parentFile
    val dir = Files.createTempDirectory("abyssus_modelimport").toFile()
    source.copyRecursively(dir)
    return dir
}

/** The asset folder names of the Untitled fixture project. */
fun untitledAssetNames(): Set<String> =
    File(System.getProperty("abyssus.testData"), "project/Untitled/assets").list()!!.toSet()

/**
 * The world positions of every vertex the node parts draw at [time] seconds of [animation] (rest pose without one):
 * node transforms with the sampled keyframes, skinned parts through their joints. A CPU stand-in for what the
 * renderer draws.
 */
fun posedPositions(data: ModelData, animation: String?, time: Float): List<Vector3> {
    val sampled = data.animations.firstOrNull { it.id == animation }
    val world = HashMap<String, Matrix4>()
    fun local(node: ModelNode): Matrix4 {
        val track = sampled?.nodeAnimations?.firstOrNull { it.nodeId == node.id }
        val t = track?.translation?.let { sample(it.toList(), time) { a, b, f -> Vector3(a).lerp(b, f) } } ?: node.translation ?: Vector3()
        val r = track?.rotation?.let { sample(it.toList(), time) { a, b, f -> Quaternion(a).slerp(b, f) } } ?: node.rotation ?: Quaternion()
        val s = track?.scaling?.let { sample(it.toList(), time) { a, b, f -> Vector3(a).lerp(b, f) } } ?: node.scale ?: Vector3(1f, 1f, 1f)
        return Matrix4().set(t, r, s)
    }
    fun place(node: ModelNode, parent: Matrix4) {
        val m = Matrix4(parent).mul(local(node))
        world[node.id] = m
        node.children?.forEach { place(it, m) }
    }
    data.nodes.forEach { place(it, Matrix4()) }

    val parts = data.meshes.flatMap { mesh -> mesh.parts.map { it.id to (mesh to it) } }.toMap()
    val out = ArrayList<Vector3>()
    fun visit(node: ModelNode) {
        for (nodePart in node.parts.orEmpty()) {
            val (mesh, part) = parts[nodePart.meshPartId]!!
            val stride = mesh.attributes.sumOf { it.numComponents }
            val weights = ArrayList<Int>()
            var offset = 0
            for (a in mesh.attributes) {
                if (a.usage == VertexAttributes.Usage.BoneWeight) weights += offset
                offset += a.numComponents
            }
            val bones = nodePart.bones
            for (index in part.indices.distinct().sorted()) {
                val p = Vector3(mesh.vertices[index * stride], mesh.vertices[index * stride + 1], mesh.vertices[index * stride + 2])
                if (bones == null || weights.isEmpty()) {
                    out += p.mul(world[node.id]!!)
                } else {
                    val sum = Vector3()
                    for (w in weights) {
                        val weight = mesh.vertices[index * stride + w + 1]
                        if (weight <= 0f) continue
                        val bone = mesh.vertices[index * stride + w].toInt()
                        val m = Matrix4(world[bones.getKeyAt(bone)]!!).mul(bones.getValueAt(bone))
                        sum.mulAdd(Vector3(p).mul(m), weight)
                    }
                    out += sum
                }
            }
        }
        node.children?.forEach(::visit)
    }
    data.nodes.forEach(::visit)
    return out
}

private fun <T> sample(keys: List<ModelNodeKeyframe<T>?>, time: Float, lerp: (T, T, Float) -> T): T? {
    val frames = keys.filterNotNull()
    if (frames.isEmpty()) return null
    if (time <= frames.first().keytime) return frames.first().value
    for (i in 1 until frames.size) {
        if (time <= frames[i].keytime) {
            val a = frames[i - 1]
            val b = frames[i]
            return lerp(a.value, b.value, (time - a.keytime) / (b.keytime - a.keytime))
        }
    }
    return frames.last().value
}

/** The length of an animation: its last keyframe. */
fun ModelAnimation.seconds(): Float = nodeAnimations.maxOf { a ->
    listOfNotNull(a.translation?.maxOfOrNull { it.keytime }, a.rotation?.maxOfOrNull { it.keytime }, a.scaling?.maxOfOrNull { it.keytime })
        .maxOrNull() ?: 0f
}
