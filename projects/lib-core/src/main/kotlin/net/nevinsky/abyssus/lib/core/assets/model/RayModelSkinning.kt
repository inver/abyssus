/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.assets.model

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import kotlin.math.sqrt

/**
 * CPU skin deformation matching the raster shader: each vertex is transformed by the weighted sum of its joint
 * matrices (`BoneWeight` attributes hold a joint index and a weight). Positions use the full matrix; normals, tangents
 * and binormals use its linear part and are renormalized. Handedness and every other channel are copied unchanged.
 *
 * A vertex without any weight keeps its bind-pose values. The source mesh is never modified, so repeated instances
 * can deform one shared [RayModelMesh] independently. Stateless and thread-safe.
 */
class RayModelSkinning {
    /** [palette] holds column-major 4x4 matrices in weight-index order; returns a new interleaved vertex array. */
    fun deform(mesh: RayModelMesh, palette: List<FloatArray>): FloatArray {
        require(palette.all { it.size == 16 && it.all(Float::isFinite) }) { "Skin palette matrices must be finite 4x4" }
        val stride = mesh.vertexSizeBytes / 4
        val source = mesh.vertices()
        require(stride > 0 && source.size % stride == 0) { "Vertex data does not match its stride" }
        val weights = mesh.attributes.filter { it.usage == Usage.BoneWeight }
            .map { requireFloatChannel(it, 2); it.offsetBytes / 4 }
        val position = mesh.attributes.firstOrNull { it.usage == Usage.Position && it.unit == 0 }
            ?.also { requireFloatChannel(it, 3) }
        val directions =
            mesh.attributes.filter { it.unit == 0 && (it.usage == Usage.Normal || it.usage == Usage.Tangent || it.usage == Usage.BiNormal) }
                .onEach { requireFloatChannel(it, 3) }
        val result = source.copyOf()
        val skin = FloatArray(16)
        for (vertex in 0 until source.size / stride) {
            val base = vertex * stride
            skin.fill(0f)
            var weighted = false
            for (offset in weights) {
                val joint = source[base + offset]
                val weight = source[base + offset + 1]
                require(weight.isFinite() && weight >= 0f) { "Invalid bone weight $weight" }
                if (weight == 0f) continue
                val index = joint.toInt()
                require(index.toFloat() == joint && index in palette.indices) { "Joint $joint is outside the ${palette.size}-matrix palette" }
                val matrix = palette[index]
                for (i in 0 until 16) skin[i] += weight * matrix[i]
                weighted = true
            }
            if (!weighted) continue
            if (position != null) transformPoint(skin, source, result, base + position.offsetBytes / 4)
            for (attribute in directions) transformDirection(skin, source, result, base + attribute.offsetBytes / 4)
        }
        return result
    }

    private fun requireFloatChannel(attribute: RayModelVertexAttribute, components: Int) =
        require(attribute.type == GL20.GL_FLOAT && attribute.components >= components && attribute.offsetBytes % 4 == 0) { "Unsupported skinned vertex channel" }

    private fun transformPoint(m: FloatArray, from: FloatArray, to: FloatArray, at: Int) {
        val x = from[at];
        val y = from[at + 1];
        val z = from[at + 2]
        to[at] = m[0] * x + m[4] * y + m[8] * z + m[12]
        to[at + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        to[at + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    private fun transformDirection(m: FloatArray, from: FloatArray, to: FloatArray, at: Int) {
        val x = from[at];
        val y = from[at + 1];
        val z = from[at + 2]
        val dx = m[0] * x + m[4] * y + m[8] * z
        val dy = m[1] * x + m[5] * y + m[9] * z
        val dz = m[2] * x + m[6] * y + m[10] * z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length <= 1e-8f) return
        to[at] = dx / length; to[at + 1] = dy / length; to[at + 2] = dz / length
    }
}
