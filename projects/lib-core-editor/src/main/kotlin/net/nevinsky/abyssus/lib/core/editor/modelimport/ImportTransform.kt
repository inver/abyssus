/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modelimport

import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart

/** The id of the node an import wraps the source roots in. */
const val IMPORT_ROOT = "import_root"

/**
 * A transformed model: [data] holds the source's roots under one `import_root` node whose transform is [root];
 * [size] is its rest-pose extent in metres.
 */
class TransformedModel(val data: ModelData, val root: Matrix4, val size: Vector3)

/**
 * Brings a source model into metres and +Y up, then sizes, grounds and centres it:
 * 1. the up axis to Y (Z up: -90° about X, keeping handedness), then the unit as a uniform scale;
 * 2. the rest-pose bounds (node transforms; skinned parts through their joints at rest);
 * 3. the fit scale: the largest extent, or the height, to the chosen metres;
 * 4. a translation so that the lowest point is at y = 0 and the X/Z centre at 0.
 *
 * The result is a new `import_root` node around the source roots: vertices, inverse bind matrices and keyframes stay
 * as they are, so skins and animations remain valid, and the source [ModelData] is not changed.
 */
class ImportTransform {
    fun apply(source: ModelData, settings: ImportSettings): TransformedModel {
        val rotation = when (settings.upAxis) {
            UpAxis.Z -> Quaternion(Vector3.X, -90f)
            UpAxis.Y -> Quaternion()
            UpAxis.X -> throw IllegalArgumentException("X up is not offered")
        }
        val oriented = Matrix4().set(Vector3(), rotation, Vector3(1f, 1f, 1f).scl(settings.unit.metres))
        val bounds = restBounds(source).mul(oriented)
        val extent = bounds.getDimensions(Vector3())
        val fit = when (val fit = settings.fit) {
            FitSize.Original -> 1f
            is FitSize.LargestExtent -> fitScale(fit.metres, maxOf(extent.x, extent.y, extent.z))
            is FitSize.Height -> fitScale(fit.metres, extent.y)
        }
        val scale = settings.unit.metres * fit
        val translation = Vector3(
            -(bounds.min.x + bounds.max.x) / 2f * fit,
            -bounds.min.y * fit,
            -(bounds.min.z + bounds.max.z) / 2f * fit,
        )

        val root = ModelNode()
        root.id = uniqueRootId(source)
        root.translation = translation
        root.rotation = rotation.takeIf { !it.isIdentity }
        root.scale = Vector3(scale, scale, scale)
        root.children = Array(source.nodes.size) { source.nodes[it] }

        val data = ModelData()
        data.id = source.id
        data.meshes.addAll(source.meshes)
        data.materials.addAll(source.materials)
        data.animations.addAll(source.animations)
        data.nodes.add(root)
        val matrix = Matrix4().set(translation, rotation, Vector3(scale, scale, scale))
        return TransformedModel(data, matrix, extent.scl(fit))
    }

    /** The bounds of every vertex a node part draws, in the rest pose: node transforms, skinned parts at their joints. */
    fun restBounds(data: ModelData): BoundingBox {
        val box = BoundingBox().inf()
        val world = HashMap<String, Matrix4>()
        val parts = HashMap<String, Pair<ModelMesh, ModelMeshPart>>()
        for (mesh in data.meshes) for (part in mesh.parts) part.id?.let { parts.putIfAbsent(it, mesh to part) }
        fun place(node: ModelNode, parent: Matrix4) {
            val m = Matrix4(parent).mul(local(node))
            node.id?.let { world.putIfAbsent(it, m) }
            node.children?.forEach { place(it, m) }
        }
        data.nodes.forEach { place(it, Matrix4()) }

        val p = Vector3()
        val skinned = Matrix4()
        val bone = Matrix4()
        fun visit(node: ModelNode, parent: Matrix4) {
            val m = Matrix4(parent).mul(local(node))
            for (nodePart in node.parts.orEmpty()) {
                val (mesh, part) = parts[nodePart.meshPartId] ?: continue
                val stride = mesh.attributes.sumOf { it.numComponents }
                val position = offsetOf(mesh, VertexAttributes.Usage.Position) ?: continue
                val weights = weightOffsets(mesh)
                val bones = nodePart.bones?.takeIf { weights.isNotEmpty() && it.size > 0 }
                val boneWorld = bones?.let { b -> (0 until b.size).map { i -> Matrix4(world[b.getKeyAt(i)] ?: Matrix4()).mul(b.getValueAt(i)) } }
                for (index in part.indices) {
                    val base = index * stride
                    p.set(mesh.vertices[base + position], mesh.vertices[base + position + 1], mesh.vertices[base + position + 2])
                    if (boneWorld == null) {
                        p.mul(m)
                    } else {
                        for (k in skinned.`val`.indices) skinned.`val`[k] = 0f
                        for (w in weights) {
                            val weight = mesh.vertices[base + w + 1]
                            if (weight <= 0f) continue
                            bone.set(boneWorld.getOrNull(mesh.vertices[base + w].toInt()) ?: continue)
                            for (k in skinned.`val`.indices) skinned.`val`[k] += bone.`val`[k] * weight
                        }
                        p.mul(skinned)
                    }
                    box.ext(p)
                }
            }
            node.children?.forEach { visit(it, m) }
        }
        data.nodes.forEach { visit(it, Matrix4()) }
        if (!box.isValid) box.set(Vector3(), Vector3())
        return box
    }

    private fun fitScale(target: Double, extent: Float): Float =
        if (extent > 1e-9f) (target / extent).toFloat() else 1f

    private fun uniqueRootId(data: ModelData): String {
        val ids = HashSet<String>()
        fun collect(node: ModelNode) {
            node.id?.let(ids::add)
            node.children?.forEach(::collect)
        }
        data.nodes.forEach(::collect)
        var id = IMPORT_ROOT
        var n = 1
        while (id in ids) id = IMPORT_ROOT + "_" + n++
        return id
    }

    private fun local(node: ModelNode): Matrix4 =
        Matrix4().set(node.translation ?: Vector3(), node.rotation ?: Quaternion(), node.scale ?: Vector3(1f, 1f, 1f))

    private fun offsetOf(mesh: ModelMesh, usage: Int): Int? {
        var offset = 0
        for (a in mesh.attributes) {
            if (a.usage == usage) return offset
            offset += a.numComponents
        }
        return null
    }

    private fun weightOffsets(mesh: ModelMesh): List<Int> {
        val result = ArrayList<Int>()
        var offset = 0
        for (a in mesh.attributes) {
            if (a.usage == VertexAttributes.Usage.BoneWeight) result += offset
            offset += a.numComponents
        }
        return result
    }
}
