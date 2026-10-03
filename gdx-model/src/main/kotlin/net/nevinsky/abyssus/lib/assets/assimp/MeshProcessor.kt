/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.assets.assimp

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.core.model.ModelMesh
import net.nevinsky.abyssus.core.model.ModelMeshPart
import org.lwjgl.assimp.AIBone
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AIVector3D
import org.lwjgl.assimp.Assimp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Converts an [AIMesh] into an interleaved [ModelMesh] with a single triangle part.
 * Attribute order: position, normal, color, texcoord0, texcoord1, tangent, binormal (each only if present), then
 * [.BONE_WEIGHTS] bone weight attributes (bone index, weight) for skinned meshes.
 */
internal class MeshProcessor {
    /** Bones of a skinned mesh. The order of [.names] is the bone index used by the vertex attributes.  */
    internal class Skin {
        val names: MutableList<String> = ArrayList<String>()
        val offsets: MutableList<Matrix4> = ArrayList<Matrix4>()
    }

    private val skins: MutableMap<Int, Skin> = HashMap<Int, Skin>()

    /** @return the skin of the processed mesh with the given index, or `null` if it is not skinned
     */
    fun skinOf(meshIndex: Int): Skin? {
        return skins.get(meshIndex)
    }

    /**
     * @return the mesh, or `null` for non-triangle meshes (points/lines)
     */
    fun process(aiMesh: AIMesh, index: Int): ModelMesh? {
        if ((aiMesh.mPrimitiveTypes() and Assimp.aiPrimitiveType_TRIANGLE) == 0) {
            return null
        }
        val count = aiMesh.mNumVertices()
        val positions = aiMesh.mVertices()
        val normals = aiMesh.mNormals()
        val colors = aiMesh.mColors(0)
        val uv0 = aiMesh.mTextureCoords(0)
        val uv1 = aiMesh.mTextureCoords(1)
        val tangents = aiMesh.mTangents()
        val binormals = aiMesh.mBitangents()

        val skin: Skin? = readSkin(aiMesh)
        val boneIds = IntArray(if (skin == null) 0 else count * BONE_WEIGHTS)
        val boneWeights = FloatArray(boneIds.size)
        if (skin != null) {
            readBoneWeights(aiMesh, boneIds, boneWeights)
            skins.put(index, skin)
        }

        val attributes: MutableList<VertexAttribute> = ArrayList<VertexAttribute>()
        attributes.add(VertexAttribute.Position())
        if (normals != null) {
            attributes.add(VertexAttribute.Normal())
        }
        if (colors != null) {
            attributes.add(VertexAttribute.ColorUnpacked())
        }
        if (uv0 != null) {
            attributes.add(VertexAttribute.TexCoords(0))
        }
        if (uv1 != null) {
            attributes.add(VertexAttribute.TexCoords(1))
        }
        if (tangents != null) {
            attributes.add(VertexAttribute.Tangent())
        }
        if (binormals != null) {
            attributes.add(VertexAttribute.Binormal())
        }

        if (skin != null) {
            for (k in 0..<BONE_WEIGHTS) {
                attributes.add(VertexAttribute.BoneWeight(k))
            }
        }

        var stride = 0
        for (a in attributes) {
            stride += a.numComponents
        }

        val vertices = FloatArray(count * stride)
        val min = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val max = Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        var pos = 0
        for (i in 0..<count) {
            val p = positions.get(i)
            pos = put(vertices, pos, p)
            min.set(min(min.x, p.x()), min(min.y, p.y()), min(min.z, p.z()))
            max.set(max(max.x, p.x()), max(max.y, p.y()), max(max.z, p.z()))
            if (normals != null) {
                pos = put(vertices, pos, normals.get(i))
            }
            if (colors != null) {
                val c = colors.get(i)
                vertices[pos++] = c.r()
                vertices[pos++] = c.g()
                vertices[pos++] = c.b()
                vertices[pos++] = c.a()
            }
            if (uv0 != null) {
                pos = putUv(vertices, pos, uv0.get(i))
            }
            if (uv1 != null) {
                pos = putUv(vertices, pos, uv1.get(i))
            }
            if (tangents != null) {
                pos = put(vertices, pos, tangents.get(i))
            }
            if (binormals != null) {
                pos = put(vertices, pos, binormals.get(i))
            }
            if (skin != null) {
                for (k in 0..<BONE_WEIGHTS) {
                    vertices[pos++] = boneIds[i * BONE_WEIGHTS + k].toFloat()
                    vertices[pos++] = boneWeights[i * BONE_WEIGHTS + k]
                }
            }
        }

        val mesh = ModelMesh()
        mesh.id = "mesh_" + index
        mesh.attributes = attributes.toTypedArray<VertexAttribute>()
        mesh.vertices = vertices
        mesh.parts =
            arrayOf<ModelMeshPart>(processPart(aiMesh, index, if (count == 0) BoundingBox() else BoundingBox(min, max)))
        sanitizeDirections(mesh)
        return mesh
    }

    companion object {
        const val BONE_WEIGHTS: Int = 4

        private const val MIN_LENGTH2 = 1e-12f

        /**
         * Degenerate triangles give zero length normals (and tangents), which turn into NaN as soon as a shader
         * normalizes them. A zero normal is replaced by the average of the normals of the valid triangles that use the
         * vertex, or by up if there is none. Zero tangents and binormals are replaced by directions perpendicular to the
         * normal.
         */
        fun sanitizeDirections(mesh: ModelMesh) {
            var stride = 0
            var normalOffset = -1
            var tangentOffset = -1
            var binormalOffset = -1
            for (attribute in mesh.attributes) {
                if (attribute.usage == VertexAttributes.Usage.Normal) {
                    normalOffset = stride
                } else if (attribute.usage == VertexAttributes.Usage.Tangent) {
                    tangentOffset = stride
                } else if (attribute.usage == VertexAttributes.Usage.BiNormal) {
                    binormalOffset = stride
                }
                stride += attribute.numComponents
            }
            if (normalOffset < 0) {
                return
            }
            val v: FloatArray = mesh.vertices
            val count = v.size / stride

            val broken = BooleanArray(count)
            var anyBroken = false
            for (i in 0..<count) {
                broken[i] = !isValid(v, i * stride + normalOffset)
                anyBroken = anyBroken or broken[i]
            }
            if (anyBroken) {
                fixNormals(mesh, stride, normalOffset, broken)
            }

            val normal = Vector3()
            val other = Vector3()
            val binormal = Vector3()
            for (i in 0..<count) {
                normal.set(
                    v[i * stride + normalOffset], v[i * stride + normalOffset + 1],
                    v[i * stride + normalOffset + 2]
                )
                if (tangentOffset >= 0 && !isValid(v, i * stride + tangentOffset)) {
                    perpendicular(normal, other)
                    set(v, i * stride + tangentOffset, other)
                }
                if (binormalOffset >= 0 && !isValid(v, i * stride + binormalOffset)) {
                    if (tangentOffset >= 0) {
                        other.set(
                            v[i * stride + tangentOffset], v[i * stride + tangentOffset + 1],
                            v[i * stride + tangentOffset + 2]
                        )
                    } else {
                        perpendicular(normal, other)
                    }
                    binormal.set(normal).crs(other).nor()
                    set(v, i * stride + binormalOffset, binormal)
                }
            }
        }

        private fun fixNormals(mesh: ModelMesh, stride: Int, normalOffset: Int, broken: BooleanArray) {
            val v: FloatArray = mesh.vertices
            val count = broken.size
            val sums = FloatArray(count * 3)
            val a = Vector3()
            val b = Vector3()
            val c = Vector3()
            for (part in mesh.parts) {
                val indices = part.indices
                var t = 0
                while (t + 2 < indices.size) {
                    if (!(broken[indices[t]] || broken[indices[t + 1]] || broken[indices[t + 2]])) {
                        t += 3
                        continue
                    }
                    position(v, indices[t] * stride, a)
                    position(v, indices[t + 1] * stride, b)
                    position(v, indices[t + 2] * stride, c)
                    b.sub(a)
                    c.sub(a)
                    b.crs(c)
                    if (b.len2() < MIN_LENGTH2) {
                        t += 3
                        continue  // degenerate triangle, no direction
                    }
                    b.nor()
                    for (k in 0..2) {
                        val vertex = indices[t + k]
                        sums[vertex * 3] += b.x
                        sums[vertex * 3 + 1] += b.y
                        sums[vertex * 3 + 2] += b.z
                    }
                    t += 3
                }
            }
            for (i in 0..<count) {
                if (!broken[i]) {
                    continue
                }
                a.set(sums[i * 3], sums[i * 3 + 1], sums[i * 3 + 2])
                if (a.len2() < MIN_LENGTH2) {
                    a.set(0f, 1f, 0f)
                }
                set(v, i * stride + normalOffset, a.nor())
            }
        }

        private fun isValid(data: FloatArray, offset: Int): Boolean {
            val x = data[offset]
            val y = data[offset + 1]
            val z = data[offset + 2]
            val len2 = x * x + y * y + z * z
            return len2.isFinite() && len2 >= MIN_LENGTH2
        }

        private fun position(data: FloatArray, vertexOffset: Int, out: Vector3) {
            out.set(data[vertexOffset], data[vertexOffset + 1], data[vertexOffset + 2])
        }

        private fun set(data: FloatArray, offset: Int, value: Vector3) {
            data[offset] = value.x
            data[offset + 1] = value.y
            data[offset + 2] = value.z
        }

        /** Any unit vector perpendicular to the normal.  */
        private fun perpendicular(normal: Vector3, out: Vector3) {
            val axis = if (abs(normal.x) < 0.9f) Vector3.X else Vector3.Y
            out.set(normal).crs(axis).nor()
        }

        private fun readSkin(aiMesh: AIMesh): Skin? {
            if (aiMesh.mNumBones() == 0) {
                return null
            }
            val skin = Skin()
            val bones = aiMesh.mBones()
            for (b in 0..<aiMesh.mNumBones()) {
                val bone = AIBone.create(bones!!.get(b))
                skin.names.add(bone.mName().dataString())
                skin.offsets.add(NodeProcessor.Companion.toMatrix4(bone.mOffsetMatrix()))
            }
            return skin
        }

        /** Keeps the [.BONE_WEIGHTS] strongest influences per vertex; unused slots stay (0, 0).  */
        private fun readBoneWeights(aiMesh: AIMesh, ids: IntArray, weights: FloatArray) {
            val bones = aiMesh.mBones()
            for (b in 0..<aiMesh.mNumBones()) {
                val bone = AIBone.create(bones!!.get(b))
                val vertexWeights = bone.mWeights()
                for (w in 0..<bone.mNumWeights()) {
                    val vw = vertexWeights.get(w)
                    val base: Int = vw.mVertexId() * BONE_WEIGHTS
                    var slot = -1
                    for (k in 0..<BONE_WEIGHTS) {
                        if (weights[base + k] == 0f) {
                            slot = k
                            break
                        }
                        if (slot < 0 || weights[base + k] < weights[base + slot]) {
                            slot = k
                        }
                    }
                    if (weights[base + slot] == 0f || vw.mWeight() > weights[base + slot]) {
                        ids[base + slot] = b
                        weights[base + slot] = vw.mWeight()
                    }
                }
            }
        }

        private fun processPart(aiMesh: AIMesh, index: Int, boundingBox: BoundingBox?): ModelMeshPart {
            val faces = aiMesh.mFaces()
            val indices = IntArray(aiMesh.mNumFaces() * 3)
            var n = 0
            for (i in 0..<aiMesh.mNumFaces()) {
                val face = faces.get(i)
                if (face.mNumIndices() != 3) {
                    continue
                }
                val buffer = face.mIndices()
                indices[n++] = buffer.get(0)
                indices[n++] = buffer.get(1)
                indices[n++] = buffer.get(2)
            }
            val part = ModelMeshPart()
            part.id = "part_" + index
            part.indices = if (n == indices.size) indices else indices.copyOf(n)
            part.primitiveType = GL20.GL_TRIANGLES
            part.boundingBox = boundingBox
            return part
        }

        private fun put(dst: FloatArray, pos: Int, v: AIVector3D): Int {
            var pos = pos
            dst[pos++] = v.x()
            dst[pos++] = v.y()
            dst[pos++] = v.z()
            return pos
        }

        /** The single place where the V coordinate is flipped (Assimp: origin bottom-left, LibGDX: top-left).  */
        private fun putUv(dst: FloatArray, pos: Int, v: AIVector3D): Int {
            var pos = pos
            dst[pos++] = v.x()
            dst[pos++] = 1 - v.y()
            return pos
        }
    }
}
