/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import net.nevinsky.abyssus.lib.gdx.gltf.PhongToPbr
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import net.nevinsky.abyssus.lib.gdx.model.PbrModelMaterial
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.badlogic.gdx.utils.Array as GdxArray

/** Builds CPU model data in the imported frame, preserving part, primitive and material order. */
internal class FlightGearGeometry {
    fun build(
        collected: Collected,
        request: FlightGearImportRequest,
        images: List<String>,
        skipped: MutableList<SkippedItem>,
        cancel: () -> Unit,
        image: (Placement, String?) -> Int?,
    ): ModelData {
        // geometry, in the Abyssus frame before scaling
        val materials = ArrayList<PartMaterial>()
        val nodes = ArrayList<PartGeometry>()
        for (placement in collected.placements) {
            cancel()
            for ((obj, toWorld) in placement.objectsWithTransforms()) {
                val name = obj.name ?: "part${nodes.size + 1}"
                if (name in request.excludedParts) continue
                val lines = obj.surfaces.count { it.type != 0 }
                if (lines > 0) skipped += SkippedItem("$name: $lines line surfaces", "lines are not imported")
                val polygons = obj.surfaces.filter { it.type == 0 && it.refs.size >= 3 }
                if (polygons.isEmpty()) continue
                val imageIndex = image(placement, obj.texture)
                nodes += geometry(name, obj, polygons, toWorld, placement.model.materials, imageIndex, materials)
            }
        }
        if (nodes.isEmpty()) throw FlightGearArchiveException("${collected.aircraft.id} has no polygons to import")

        // place: centre span and length, lowest point at 0, then scale
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        for (node in nodes) for (p in node.primitives) for (i in p.positions.indices step 3) {
            minX = minOf(minX, p.positions[i]); maxX = maxOf(maxX, p.positions[i])
            minY = minOf(minY, p.positions[i + 1])
            minZ = minOf(minZ, p.positions[i + 2]); maxZ = maxOf(maxZ, p.positions[i + 2])
        }
        val scale = when (val size = request.size) {
            ImportSize.Original -> 1f
            is ImportSize.Span -> (size.metres / (maxX - minX).coerceAtLeast(1e-6f)).toFloat()
        }
        val origin = when (val o = request.origin) {
            ImportOrigin.Ground -> floatArrayOf((minX + maxX) / 2, minY, (minZ + maxZ) / 2)
            is ImportOrigin.SourcePoint -> FloatArray(3).also {
                FG_TO_ABYSSUS.transform(floatArrayOf(o.x.toFloat(), o.y.toFloat(), o.z.toFloat()), 0, it, 0)
            }
        }
        for (node in nodes) for (p in node.primitives) for (i in p.positions.indices step 3) {
            for (c in 0..2) p.positions[i + c] = (p.positions[i + c] - origin[c]) * scale
        }
        return modelData(nodes, materials, images)
    }

    /** A material: base colour (RGBA, alpha below 1 blends), an optional image (index into the images), sides. */
    private data class PartMaterial(val name: String, val color: List<Float>, val image: Int?, val doubleSided: Boolean)

    /** Triangles of one material: three floats per position and normal, two per UV, one vertex per [indices] entry. */
    private class PartPrimitive(val material: Int, val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray)

    private class PartGeometry(val name: String, val primitives: List<PartPrimitive>)

    /** One top-level node per part, one mesh per primitive, metallic-roughness materials, textures by path. */
    private fun modelData(nodes: List<PartGeometry>, materials: List<PartMaterial>, images: List<String>): ModelData {
        val data = ModelData()
        val ids = ArrayList<String>()
        for (m in materials) {
            var id = m.name
            var n = 2
            while (id in ids) id = "${m.name}_${n++}"
            ids += id
            val material = PbrModelMaterial()
            material.id = id
            material.baseColor = Color(m.color[0], m.color[1], m.color[2], m.color[3])
            material.metallic = 0f
            material.roughness = PhongToPbr.DEFAULT_ROUGHNESS
            material.doubleSided = m.doubleSided
            if (m.color[3] < 1f) material.alphaMode = PbrModelMaterial.AlphaMode.BLEND
            material.textures = GdxArray()
            m.image?.let { image ->
                material.textures.add(ModelTexture().apply {
                    this.id = "${id}_0"
                    usage = ModelTexture.USAGE_DIFFUSE
                    fileName = images[image]
                })
            }
            data.materials.add(material)
        }
        for (node in nodes) {
            val parts = node.primitives.map { p ->
                val index = data.meshes.size
                val vertices = FloatArray(p.positions.size / 3 * 8)
                for (v in 0 until p.positions.size / 3) {
                    p.positions.copyInto(vertices, v * 8, v * 3, v * 3 + 3)
                    p.normals.copyInto(vertices, v * 8 + 3, v * 3, v * 3 + 3)
                    p.uvs.copyInto(vertices, v * 8 + 6, v * 2, v * 2 + 2)
                }
                val part = ModelMeshPart().apply {
                    id = "part_$index"
                    indices = p.indices
                    primitiveType = GL20.GL_TRIANGLES
                }
                data.addMesh(ModelMesh().apply {
                    id = "mesh_$index"
                    attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
                    this.vertices = vertices
                    this.parts = arrayOf(part)
                })
                ModelNodePart().apply {
                    meshPartId = part.id
                    materialId = ids[p.material]
                }
            }
            data.nodes.add(ModelNode().apply {
                id = node.name
                this.parts = parts.toTypedArray()
            })
        }
        return data
    }

    private fun geometry(
        name: String,
        obj: Ac3dObject,
        polygons: List<Ac3dSurface>,
        toWorld: Matrix,
        acMaterials: List<Ac3dMaterial>,
        image: Int?,
        materials: MutableList<PartMaterial>,
    ): PartGeometry {
        val count = obj.vertices.size / 3
        val world = FloatArray(obj.vertices.size)
        for (i in 0 until count) toWorld.transform(obj.vertices, i * 3, world, i * 3)

        // face normals of every polygon, for flat shading and the crease test
        val faceNormals = polygons.map { s -> newell(world, s.refs) }
        val smoothAt = HashMap<Int, MutableList<Int>>() // vertex -> smooth faces using it
        polygons.forEachIndexed { f, s -> if (s.smooth) s.refs.forEach { smoothAt.getOrPut(it) { ArrayList() } += f } }
        val crease = cos(Math.toRadians(obj.crease.toDouble())).toFloat()

        class Builder {
            val positions = ArrayList<Float>(); val normals = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
        }
        val builders = LinkedHashMap<Int, Builder>()
        polygons.forEachIndexed { f, s ->
            val acMaterial = acMaterials.getOrNull(s.material)
            val material = PartMaterial(
                acMaterial?.name ?: "material${s.material}",
                listOf(acMaterial?.r ?: 0.8f, acMaterial?.g ?: 0.8f, acMaterial?.b ?: 0.8f, 1f - (acMaterial?.transparency ?: 0f)),
                image, s.twoSided,
            )
            val index = materials.indexOf(material).takeIf { it >= 0 } ?: (materials.size.also { materials += material })
            val b = builders.getOrPut(index) { Builder() }
            val first = b.positions.size / 3
            val n = faceNormals[f]
            for (k in s.refs.indices) {
                val v = s.refs[k]
                b.positions += world[v * 3]; b.positions += world[v * 3 + 1]; b.positions += world[v * 3 + 2]
                val normal = if (s.smooth) {
                    val sum = FloatArray(3)
                    for (g in smoothAt[v].orEmpty()) {
                        val m = faceNormals[g]
                        if (m[0] * n[0] + m[1] * n[1] + m[2] * n[2] >= crease) { sum[0] += m[0]; sum[1] += m[1]; sum[2] += m[2] }
                    }
                    normalised(sum, n)
                } else n
                b.normals += normal[0]; b.normals += normal[1]; b.normals += normal[2]
                b.uvs += s.uvs[k * 2] * obj.textureRepeat[0] + obj.textureOffset[0]
                b.uvs += 1f - (s.uvs[k * 2 + 1] * obj.textureRepeat[1] + obj.textureOffset[1]) // glTF's V runs down
            }
            for (k in 1 until s.refs.size - 1) { b.indices += first; b.indices += first + k; b.indices += first + k + 1 }
        }
        return PartGeometry(name, builders.map { (material, b) ->
            PartPrimitive(material, b.positions.toFloatArray(), b.normals.toFloatArray(), b.uvs.toFloatArray(), b.indices.toIntArray())
        })
    }

    private fun newell(positions: FloatArray, refs: IntArray): FloatArray {
        val n = FloatArray(3)
        for (k in refs.indices) {
            val a = refs[k] * 3
            val b = refs[(k + 1) % refs.size] * 3
            n[0] += (positions[a + 1] - positions[b + 1]) * (positions[a + 2] + positions[b + 2])
            n[1] += (positions[a + 2] - positions[b + 2]) * (positions[a] + positions[b])
            n[2] += (positions[a] - positions[b]) * (positions[a + 1] + positions[b + 1])
        }
        return normalised(n, floatArrayOf(0f, 1f, 0f))
    }

    private fun normalised(v: FloatArray, fallback: FloatArray): FloatArray {
        val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (length < 1e-12f) fallback else floatArrayOf(v[0] / length, v[1] / length, v[2] / length)
    }
}

/** A 3x4 affine matrix (rotation and scale, then translation), row-major. */
internal class Matrix(private val m: DoubleArray) {
    operator fun times(o: Matrix): Matrix {
        val r = DoubleArray(12)
        for (row in 0..2) for (col in 0..3) {
            var sum = if (col == 3) m[row * 4 + 3] else 0.0
            for (k in 0..2) sum += m[row * 4 + k] * o.m[k * 4 + col]
            r[row * 4 + col] = sum
        }
        return Matrix(r)
    }

    fun transform(src: FloatArray, at: Int, dst: FloatArray, to: Int) {
        val x = src[at].toDouble()
        val y = src[at + 1].toDouble()
        val z = src[at + 2].toDouble()
        for (row in 0..2) dst[to + row] = (m[row * 4] * x + m[row * 4 + 1] * y + m[row * 4 + 2] * z + m[row * 4 + 3]).toFloat()
    }
}

/** AC3D files (y up) to FlightGear's body frame (x aft, y right, z up): x, -z, y. */
internal val AC_TO_FG = Matrix(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 1.0, 0.0, 0.0))

/** FlightGear's body frame to the Abyssus plane frame (nose +Z, up +Y, left wing +X): -y, z, -x. */
internal val FG_TO_ABYSSUS = Matrix(doubleArrayOf(0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0, 0.0))

/** An AC3D object's placement in its parent: its `rot` (row-major) then its `loc`. */
internal fun acMatrix(rotation: FloatArray, location: FloatArray) = Matrix(doubleArrayOf(
    rotation[0].toDouble(), rotation[1].toDouble(), rotation[2].toDouble(), location[0].toDouble(),
    rotation[3].toDouble(), rotation[4].toDouble(), rotation[5].toDouble(), location[1].toDouble(),
    rotation[6].toDouble(), rotation[7].toDouble(), rotation[8].toDouble(), location[2].toDouble(),
))

/** FlightGear `offsets` in its body frame: translation, then heading about z, pitch about y and roll about x. */
internal fun offsetsMatrix(o: FlightGearOffsets): Matrix {
    fun rad(degrees: Double) = Math.toRadians(degrees)
    val (ch, sh) = cos(rad(o.heading)) to sin(rad(o.heading))
    val (cp, sp) = cos(rad(o.pitch)) to sin(rad(o.pitch))
    val (cr, sr) = cos(rad(o.roll)) to sin(rad(o.roll))
    val heading = Matrix(doubleArrayOf(ch, -sh, 0.0, 0.0, sh, ch, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0))
    val pitch = Matrix(doubleArrayOf(cp, 0.0, sp, 0.0, 0.0, 1.0, 0.0, 0.0, -sp, 0.0, cp, 0.0))
    val roll = Matrix(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, cr, -sr, 0.0, 0.0, sr, cr, 0.0))
    val move = Matrix(doubleArrayOf(1.0, 0.0, 0.0, o.x, 0.0, 1.0, 0.0, o.y, 0.0, 0.0, 1.0, o.z))
    return move * heading * pitch * roll
}
