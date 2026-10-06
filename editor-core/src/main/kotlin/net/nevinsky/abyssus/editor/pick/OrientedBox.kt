/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor.pick

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox

/** CPU-side box geometry; projection uses all eight corners, including for pitched or rolled boxes. */
class OrientedBox(local: BoundingBox, world: Matrix4) {
    val corners: List<Vector3> = buildList {
        for (y in listOf(local.min.y, local.max.y))
            for (z in listOf(local.min.z, local.max.z))
                for (x in listOf(local.min.x, local.max.x)) add(Vector3(x, y, z).mul(world))
    }
    val bottom: Float = corners.minOf { it.y }
    val top: Float = corners.maxOf { it.y }
    val hull: List<Vector2> = convexHull(corners.map { Vector2(it.x, it.z) })

    fun overlaps(other: OrientedBox): Boolean {
        if (hull.maxOf { it.x } < other.hull.minOf { it.x } || other.hull.maxOf { it.x } < hull.minOf { it.x } ||
            hull.maxOf { it.y } < other.hull.minOf { it.y } || other.hull.maxOf { it.y } < hull.minOf { it.y }) return false
        for (polygon in listOf(hull, other.hull)) for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val nx = a.y - b.y
            val nz = b.x - a.x
            fun projection(p: Vector2) = p.x * nx + p.y * nz
            if (hull.maxOf(::projection) < other.hull.minOf(::projection) ||
                other.hull.maxOf(::projection) < hull.minOf(::projection)) return false
        }
        return true
    }

    private fun convexHull(points: List<Vector2>): List<Vector2> {
        val sorted = points.distinctBy { it.x to it.y }.sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size <= 2) return sorted
        fun turn(a: Vector2, b: Vector2, c: Vector2) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        fun half(order: List<Vector2>): List<Vector2> {
            val out = mutableListOf<Vector2>()
            for (p in order) {
                while (out.size >= 2 && turn(out[out.lastIndex - 1], out.last(), p) <= 0f) out.removeAt(out.lastIndex)
                out += p
            }
            return out.dropLast(1)
        }
        return half(sorted) + half(sorted.reversed())
    }
}
