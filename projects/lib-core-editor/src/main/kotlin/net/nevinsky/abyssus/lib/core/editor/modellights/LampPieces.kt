/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.modellights

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.floor

/** Vertices closer than this (in model units, after node transforms) count as one vertex when pieces are found. */
const val WELD_TOLERANCE = 1e-3f

/** One lamp: the bounds of a connected piece of geometry, as its [center] and its [size] along each axis. */
class LampPiece(val center: Vector3, val size: Vector3) {
    val largest: Float
        get() = maxOf(size.x, size.y, size.z)

    /** This piece moved, rotated and scaled by [transform]: the centre goes through it, the size follows its scale. */
    fun placed(transform: Matrix4): LampPiece {
        val scale = transform.getScale(Vector3())
        return LampPiece(
            Vector3(center).mul(transform),
            Vector3(size.x * abs(scale.x), size.y * abs(scale.y), size.z * abs(scale.z)),
        )
    }

    /** The piece whose bounds hold both this and [other]. */
    fun merged(other: LampPiece): LampPiece {
        val min = Vector3(minOf(minX, other.minX), minOf(minY, other.minY), minOf(minZ, other.minZ))
        val max = Vector3(maxOf(maxX, other.maxX), maxOf(maxY, other.maxY), maxOf(maxZ, other.maxZ))
        return LampPiece(Vector3(min).add(max).scl(0.5f), Vector3(max).sub(min))
    }

    private val minX get() = center.x - size.x / 2
    private val minY get() = center.y - size.y / 2
    private val minZ get() = center.z - size.z / 2
    private val maxX get() = center.x + size.x / 2
    private val maxY get() = center.y + size.y / 2
    private val maxZ get() = center.z + size.z / 2
}

/**
 * The lamps of [geometry]: each connected piece of its triangles (triangles that share a vertex, vertices at the same
 * position welded), ordered by the position of their centre (x, then y, then z) so that the answer is stable.
 */
fun lampPieces(geometry: MaterialGeometry): List<LampPiece> {
    val count = geometry.positions.size / 3
    if (count == 0 || geometry.triangles.isEmpty()) {
        return emptyList()
    }
    val weld = HashMap<Triple<Long, Long, Long>, Int>()
    val welded = IntArray(count)
    for (v in 0 until count) {
        fun cell(i: Int) = floor(geometry.positions[v * 3 + i] / WELD_TOLERANCE).toLong()
        welded[v] = weld.getOrPut(Triple(cell(0), cell(1), cell(2))) { weld.size }
    }
    val parent = IntArray(weld.size) { it }
    fun find(a: Int): Int {
        var root = a
        while (parent[root] != root) {
            root = parent[root]
        }
        var at = a
        while (parent[at] != root) {
            val next = parent[at]
            parent[at] = root
            at = next
        }
        return root
    }
    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) {
            parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
    }
    val used = BooleanArray(weld.size)
    for (t in 0 until geometry.triangles.size - geometry.triangles.size % 3 step 3) {
        val a = welded[geometry.triangles[t]]
        val b = welded[geometry.triangles[t + 1]]
        val c = welded[geometry.triangles[t + 2]]
        union(a, b)
        union(a, c)
        used[a] = true
        used[b] = true
        used[c] = true
    }
    val bounds = HashMap<Int, FloatArray>()
    for (v in 0 until count) {
        val w = welded[v]
        if (!used[w]) {
            continue
        }
        val box =
            bounds.getOrPut(find(w)) {
                val big = Float.MAX_VALUE
                floatArrayOf(big, big, big, -big, -big, -big)
            }
        for (i in 0..2) {
            val p = geometry.positions[v * 3 + i]
            box[i] = minOf(box[i], p)
            box[i + 3] = maxOf(box[i + 3], p)
        }
    }
    return bounds.values
        .map { b ->
            LampPiece(
                Vector3((b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2),
                Vector3(b[3] - b[0], b[4] - b[1], b[5] - b[2]),
            )
        }
        .sortedWith(compareBy({ it.center.x }, { it.center.y }, { it.center.z }))
}

/** One material the user can take lamps from: its [emissive] color (null when none) and how many [lamps] it has. */
data class LampMaterialChoice(val materialId: String, val emissive: com.badlogic.gdx.graphics.Color?, val lamps: Int)

/** The choices of [geometries], materials with an emissive color first, each group in the model's order. */
fun lampMaterialChoices(geometries: List<MaterialGeometry>): List<LampMaterialChoice> =
    geometries
        .map { LampMaterialChoice(it.materialId, it.emissive, lampPieces(it).size) }
        .filter { it.lamps > 0 }
        .sortedBy { it.emissive == null }
