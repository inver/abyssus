/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import kotlin.math.cos
import kotlin.math.sin

/**
 * One world axis seen from the orbit camera, in the orientation widget's unit disc: [x] to the right and [y] up (both
 * from -1 through 1), and [depth], positive toward the viewer.
 */
data class AxisMark(val axis: GizmoAxis, val x: Float, val y: Float, val depth: Float)

/**
 * The three world axes projected for the orientation widget of an orbit camera at [yaw] and [pitch] (see [OrbitCamera]:
 * the eye sits at `(cos p sin y, sin p, cos p cos y)` from its target). No GL needed.
 */
class OrientationProjection(yaw: Float, pitch: Float) {
    private val toViewer = floatArrayOf(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw))
    private val right = floatArrayOf(cos(yaw), 0f, -sin(yaw))
    private val up = floatArrayOf(-sin(pitch) * sin(yaw), cos(pitch), -sin(pitch) * cos(yaw))

    /** The marks drawn far to near, so the nearest axis is painted last. */
    fun marks(): List<AxisMark> = GizmoAxis.entries.map { mark(it) }.sortedBy { it.depth }

    /** The nearest-to-the-viewer mark whose center is within [markRadius] of ([x], [y]) in the unit disc, or null. */
    fun hit(x: Float, y: Float, markRadius: Float): AxisMark? =
        marks().filter { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) <= markRadius * markRadius }.maxByOrNull { it.depth }

    private fun mark(axis: GizmoAxis): AxisMark {
        val d = axis.direction
        fun dot(v: FloatArray) = d.x * v[0] + d.y * v[1] + d.z * v[2]
        return AxisMark(axis, dot(right), dot(up), dot(toViewer))
    }
}
