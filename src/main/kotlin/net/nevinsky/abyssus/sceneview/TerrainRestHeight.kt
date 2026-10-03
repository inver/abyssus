/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.sqrt

/** Highest point of a transformed bilinear height field inside a convex world X/Z footprint. No GL or ray march. */
internal class TerrainRestHeight {
    private data class Bilinear(val a: Double, val b: Double, val c: Double, val d: Double) {
        fun at(u: Double, v: Double) = a + b * u + c * v + d * u * v
        fun normalised(): Bilinear {
            val scale = maxOf(abs(a), abs(b), abs(c), abs(d))
            return if (scale == 0.0) this else Bilinear(a / scale, b / scale, c / scale, d / scale)
        }
    }

    fun height(footprint: OrientedBox, terrain: TerrainTarget): Float? {
        val determinant = terrain.world.det()
        if (!determinant.isFinite() || determinant == 0f) return null
        val hull = footprint.hull
        val minX = hull.minOf { it.x }; val maxX = hull.maxOf { it.x }
        val minZ = hull.minOf { it.y }; val maxZ = hull.maxOf { it.y }
        val data = terrain.data
        val cell = data.size.toFloat() / (data.resolution - 1)
        var best: Double? = null
        fun point(x: Int, z: Int) = Vector3(x * cell, data.heights[z * data.resolution + x], z * cell).mul(terrain.world)
        for (z in 0 until data.resolution - 1) for (x in 0 until data.resolution - 1) {
            val corners = listOf(point(x, z), point(x + 1, z), point(x, z + 1), point(x + 1, z + 1))
            if (corners.maxOf { it.x } < minX || corners.minOf { it.x } > maxX ||
                corners.maxOf { it.z } < minZ || corners.minOf { it.z } > maxZ) continue
            if (best != null && corners.maxOf { it.y }.toDouble() <= best) continue
            fun coordinate(value: (Vector3) -> Float): Bilinear {
                val a = value(corners[0]).toDouble()
                val b = value(corners[1]).toDouble() - a
                val c = value(corners[2]).toDouble() - a
                return Bilinear(a, b, c, value(corners[3]).toDouble() - a - b - c)
            }
            val worldX = coordinate { it.x }
            val worldZ = coordinate { it.z }
            val worldY = coordinate { it.y }
            fun plane(nx: Double, nz: Double, offset: Double) = Bilinear(
                nx * worldX.a + nz * worldZ.a + offset,
                nx * worldX.b + nz * worldZ.b, nx * worldX.c + nz * worldZ.c, nx * worldX.d + nz * worldZ.d,
            ).normalised()
            val constraints = mutableListOf(
                Bilinear(0.0, 1.0, 0.0, 0.0), Bilinear(1.0, -1.0, 0.0, 0.0),
                Bilinear(0.0, 0.0, 1.0, 0.0), Bilinear(1.0, 0.0, -1.0, 0.0),
            )
            // Bounds also constrain zero-area footprints (a line or point).
            constraints += plane(1.0, 0.0, -minX.toDouble())
            constraints += plane(-1.0, 0.0, maxX.toDouble())
            constraints += plane(0.0, 1.0, -minZ.toDouble())
            constraints += plane(0.0, -1.0, maxZ.toDouble())
            for (i in hull.indices) {
                val a = hull[i]; val b = hull[(i + 1) % hull.size]
                val nx = (a.y - b.y).toDouble(); val nz = (b.x - a.x).toDouble()
                constraints += plane(nx, nz, -nx * a.x - nz * a.y)
            }
            fun offer(u: Double, v: Double) {
                if (!u.isFinite() || !v.isFinite() || constraints.any { it.at(u, v) < -1e-8 }) return
                val y = worldY.at(u.coerceIn(0.0, 1.0), v.coerceIn(0.0, 1.0))
                if (y.isFinite()) best = best?.let { maxOf(it, y) } ?: y
            }
            offer(0.0, 0.0); offer(1.0, 0.0); offer(0.0, 1.0); offer(1.0, 1.0)
            for (i in constraints.indices) {
                val g = constraints[i]
                for (j in i + 1 until constraints.size) intersect(g, constraints[j], ::offer)
                // Along g=0, stationary Y has Y_u*g_v - Y_v*g_u = 0: its uv term cancels.
                val stationary = Bilinear(
                    worldY.b * g.c - worldY.c * g.b,
                    worldY.b * g.d - worldY.d * g.b,
                    worldY.d * g.c - worldY.c * g.d, 0.0,
                ).normalised()
                intersect(g, stationary, ::offer)
            }
            if (worldY.d != 0.0) offer(-worldY.c / worldY.d, -worldY.b / worldY.d)
        }
        return best?.toFloat()
    }

    /** Eliminate uv to obtain a line, then substitute it into the remaining bilinear equation. */
    private fun intersect(g: Bilinear, h: Bilinear, offer: (Double, Double) -> Unit) {
        if (g.d == 0.0 && h.d == 0.0) {
            val det = g.b * h.c - h.b * g.c
            if (abs(det) > 1e-12) offer((g.c * h.a - h.c * g.a) / det, (h.b * g.a - g.b * h.a) / det)
            return
        }
        val line = Bilinear(g.a * h.d - h.a * g.d, g.b * h.d - h.b * g.d, g.c * h.d - h.c * g.d, 0.0)
        val curve = if (abs(g.d) >= abs(h.d)) g else h
        fun valid(u: Double, v: Double) {
            if (abs(g.at(u, v)) <= 1e-7 && abs(h.at(u, v)) <= 1e-7) offer(u, v)
        }
        if (abs(line.c) > abs(line.b)) {
            val r = -line.a / line.c; val s = -line.b / line.c
            roots(curve.d * s, curve.b + curve.c * s + curve.d * r, curve.a + curve.c * r) { u -> valid(u, r + s * u) }
        } else if (abs(line.b) > 1e-12) {
            val r = -line.a / line.b; val s = -line.c / line.b
            roots(curve.d * s, curve.c + curve.b * s + curve.d * r, curve.a + curve.b * r) { v -> valid(r + s * v, v) }
        }
    }

    private fun roots(a: Double, b: Double, c: Double, offer: (Double) -> Unit) {
        if (abs(a) < 1e-12) {
            if (abs(b) >= 1e-12) offer(-c / b)
            return
        }
        val discriminant = b * b - 4 * a * c
        if (discriminant < -1e-12) return
        val root = sqrt(maxOf(0.0, discriminant))
        // This form avoids cancellation when b and sqrt(discriminant) are nearly equal.
        val q = -0.5 * (b + if (b >= 0) root else -root)
        if (q == 0.0) offer(-b / (2 * a)) else { offer(q / a); offer(c / q) }
    }
}
