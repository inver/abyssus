/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.math.Matrix4
import kotlin.math.abs

/**
 * How much of the screen a water surface's rectangle covers: its four corners at the mean level are clipped to the
 * view frustum in homogeneous clip space (so a corner behind the camera is handled) and the polygon's area on screen is
 * measured in pixels. Zero when nothing of it is in view. Used to rank levels for the planar reflection budget. Pure.
 */
class WaterScreenArea {
    private class Vertex(val x: Double, val y: Double, val z: Double, val w: Double)

    fun pixels(viewProjection: Matrix4, surface: WaterSettings, viewWidth: Int, viewHeight: Int): Double {
        if (viewWidth <= 0 || viewHeight <= 0) return 0.0
        val hw = surface.width / 2
        val hl = surface.length / 2
        var polygon = listOf(
            clip(viewProjection, surface.x - hw, surface.level, surface.z - hl),
            clip(viewProjection, surface.x + hw, surface.level, surface.z - hl),
            clip(viewProjection, surface.x + hw, surface.level, surface.z + hl),
            clip(viewProjection, surface.x - hw, surface.level, surface.z + hl),
        )
        // the five planes that bound what is seen: near, left, right, bottom, top (far is ignored: a far surface still shows)
        val planes: List<(Vertex) -> Double> = listOf(
            { v -> v.z + v.w }, { v -> v.w + v.x }, { v -> v.w - v.x }, { v -> v.w + v.y }, { v -> v.w - v.y },
        )
        for (distance in planes) {
            polygon = clipAgainst(polygon, distance)
            if (polygon.size < 3) return 0.0
        }
        val screen = polygon.map { (it.x / it.w) to (it.y / it.w) }
        var twice = 0.0
        for (i in screen.indices) {
            val a = screen[i]
            val b = screen[(i + 1) % screen.size]
            twice += a.first * b.second - b.first * a.second
        }
        // NDC spans 2 x 2: one unit of NDC area is a quarter of the viewport
        return abs(twice) / 2.0 * viewWidth.toDouble() * viewHeight / 4.0
    }

    private fun clip(m: Matrix4, x: Double, y: Double, z: Double): Vertex {
        val v = m.`val`
        val fx = x.toFloat(); val fy = y.toFloat(); val fz = z.toFloat()
        return Vertex(
            (v[Matrix4.M00] * fx + v[Matrix4.M01] * fy + v[Matrix4.M02] * fz + v[Matrix4.M03]).toDouble(),
            (v[Matrix4.M10] * fx + v[Matrix4.M11] * fy + v[Matrix4.M12] * fz + v[Matrix4.M13]).toDouble(),
            (v[Matrix4.M20] * fx + v[Matrix4.M21] * fy + v[Matrix4.M22] * fz + v[Matrix4.M23]).toDouble(),
            (v[Matrix4.M30] * fx + v[Matrix4.M31] * fy + v[Matrix4.M32] * fz + v[Matrix4.M33]).toDouble(),
        )
    }

    /** Sutherland-Hodgman: the part of [polygon] where [distance] is not negative. */
    private fun clipAgainst(polygon: List<Vertex>, distance: (Vertex) -> Double): List<Vertex> {
        val out = ArrayList<Vertex>()
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val da = distance(a)
            val db = distance(b)
            if (da >= 0) out += a
            if ((da >= 0) != (db >= 0)) {
                val t = da / (da - db)
                out += Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, a.w + (b.w - a.w) * t)
            }
        }
        return out
    }
}
