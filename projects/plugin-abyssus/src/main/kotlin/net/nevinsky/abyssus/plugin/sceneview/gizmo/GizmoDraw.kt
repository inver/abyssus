/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.gizmo

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.plugin.sceneview.gizmo.GizmoDraw.plane
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The lines of a gizmo, the hovered handle brighter. No GL needed. */
object GizmoDraw {
    private const val RING_SEGMENTS = 48
    private const val HEAD_LENGTH = 0.2f
    private const val HEAD_RADIUS = 0.07f
    private const val LINE_WIDTH_PX = 2f

    private fun colorOf(axis: GizmoAxis, hovered: Boolean): Color {
        val k = if (hovered) {
            0.6f
        } else 0f
        return Color(
            axis.red + (1f - axis.red) * k,
            axis.green + (1f - axis.green) * k,
            axis.blue + (1f - axis.blue) * k,
            1f
        )
    }

    fun draw(out: LineSink, handles: GizmoHandles, hovered: GizmoAxis?, hoveredPlane: GizmoPlane? = null) {
        for (axis in GizmoAxis.entries) {
            val color = colorOf(axis, axis == hovered)
            when (handles.mode) {
                GizmoMode.MOVE -> arrow(out, handles, axis, color)
                GizmoMode.ROTATE -> ring(out, handles, axis, color)
                GizmoMode.SELECT -> Unit
            }
        }
        if (handles.mode == GizmoMode.MOVE) {
            for (p in GizmoPlane.entries) {
                plane(out, handles, p, p == hoveredPlane)
            }
        }
    }

    /** The square of [plane]: its edges along each axis in that axis's color, the whole square brighter while hovered. */
    private fun plane(out: LineSink, handles: GizmoHandles, plane: GizmoPlane, hovered: Boolean) {
        val first = colorOf(plane.first, hovered)
        val second = colorOf(plane.second, hovered)
        val i = PLANE_INNER
        val o = PLANE_OUTER
        out.thickLine(handles.planePoint(plane, i, i), handles.planePoint(plane, o, i), first, LINE_WIDTH_PX)
        out.thickLine(handles.planePoint(plane, i, o), handles.planePoint(plane, o, o), first, LINE_WIDTH_PX)
        out.thickLine(handles.planePoint(plane, i, i), handles.planePoint(plane, i, o), second, LINE_WIDTH_PX)
        out.thickLine(handles.planePoint(plane, o, i), handles.planePoint(plane, o, o), second, LINE_WIDTH_PX)
    }

    private fun arrow(out: LineSink, handles: GizmoHandles, axis: GizmoAxis, color: Color) {
        out.thickLine(handles.origin, handles.tip(axis), color, LINE_WIDTH_PX)
        val a = handles.axisVector(axis)
        val tip = handles.tip(axis).cpy()
        val base = Vector3(tip).mulAdd(a, -HEAD_LENGTH * handles.size)
        val side = sideOf(a)
        val u = Vector3(a).crs(side).nor().scl(HEAD_RADIUS * handles.size)
        val v = Vector3(a).crs(u).nor().scl(HEAD_RADIUS * handles.size)
        for ((su, sv) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
            out.thickLine(tip, Vector3(base).mulAdd(u, su).mulAdd(v, sv), color, LINE_WIDTH_PX)
        }
    }

    /** A world axis that is not parallel to [axis], to build a frame around it (Y, or X for an axis near Y). */
    private fun sideOf(axis: Vector3): Vector3 = if (kotlin.math.abs(axis.y) < 0.9f) Vector3.Y else Vector3.X

    private fun ring(out: LineSink, handles: GizmoHandles, axis: GizmoAxis, color: Color) {
        val n = handles.axisVector(axis)
        val side = sideOf(n)
        val u = Vector3(n).crs(side).nor()
        val v = Vector3(n).crs(u).nor()
        val center = handles.origin.cpy()
        fun at(i: Int): Vector3 {
            val angle = 2.0 * PI * i / RING_SEGMENTS
            return Vector3(center).mulAdd(u, cos(angle).toFloat() * handles.size)
                .mulAdd(v, sin(angle).toFloat() * handles.size)
        }
        for (i in 0 until RING_SEGMENTS) {
            out.thickLine(at(i), at(i + 1), color, LINE_WIDTH_PX)
        }
    }
}
