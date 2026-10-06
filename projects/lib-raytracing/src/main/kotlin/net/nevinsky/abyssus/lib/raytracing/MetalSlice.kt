/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import kotlin.math.sqrt
import kotlin.math.tan

/** Minimal feasibility geometry, not yet the complete material/asset snapshot representation. */
class MetalSliceMesh(vertices: FloatArray, indices: IntArray) {
    private val positions = vertices.copyOf()
    private val triangles = indices.copyOf()
    init {
        require(positions.isNotEmpty() && positions.size % 3 == 0 && positions.all { it.isFinite() })
        require(triangles.isNotEmpty() && triangles.size % 3 == 0 && triangles.all { it in 0 until positions.size / 3 })
    }
    internal fun vertices() = positions.copyOf()
    internal fun indices() = triangles.copyOf()
}

class MetalSliceInstance(val mesh: Int, transform: List<Float>, color: List<Float>, val reflective: Boolean = false) {
    private val matrix = transform.toFloatArray()
    private val tint = color.toFloatArray()
    init {
        require(mesh >= 0 && matrix.size == 16 && matrix.all { it.isFinite() })
        require(matrix[3] == 0f && matrix[7] == 0f && matrix[11] == 0f && matrix[15] == 1f)
        require(tint.size == 3 && tint.all { it.isFinite() && it in 0f..1f })
    }
    internal fun uniforms() = matrix + tint + floatArrayOf(if (reflective) 1f else 0f)
}

class MetalSliceCamera(
    origin: List<Float>, forward: List<Float>, up: List<Float>,
    val fieldOfView: Float, val near: Float, val far: Float,
    lightDirection: List<Float> = listOf(0f,1f,0f),
) {
    private val position = origin.toFloatArray()
    private val direction = unit(forward.toFloatArray())
    private val right = unit(cross(direction, unit(up.toFloatArray())))
    private val vertical = cross(right, direction)
    private val light = unit(lightDirection.toFloatArray())
    init {
        require(position.size == 3 && position.all { it.isFinite() })
        require(fieldOfView.isFinite() && fieldOfView > 0f && fieldOfView < 180f)
        require(near.isFinite() && far.isFinite() && near > 0f && far > near)
    }
    internal fun uniforms(width: Int, height: Int) = position + near + direction + far +
        right + tan(Math.toRadians(fieldOfView.toDouble() / 2)).toFloat() +
        vertical + (width.toFloat() / height) + light + 0f

    private fun unit(v: FloatArray): FloatArray {
        require(v.size == 3 && v.all { it.isFinite() })
        val length = sqrt(v.sumOf { it.toDouble() * it }.toFloat())
        require(length.isFinite() && length > 0.000001f)
        return FloatArray(3) { v[it] / length }
    }
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]
    )
}
