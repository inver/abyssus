/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.gizmo

import net.nevinsky.abyssus.lib.gdx.editor.pick.GizmoAxis
import net.nevinsky.abyssus.lib.gdx.editor.pick.GizmoHandles
import net.nevinsky.abyssus.lib.gdx.editor.pick.GizmoMode
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.editor.pick.LineSink
import net.nevinsky.abyssus.lib.gdx.editor.content.Rgba
import net.nevinsky.abyssus.lib.gdx.editor.scene.toVec3
import net.nevinsky.abyssus.lib.gdx.editor.scene.toVector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The lines of a gizmo, the hovered handle brighter. No GL needed. */
object GizmoDraw {
    private const val RING_SEGMENTS = 48
    private const val HEAD_LENGTH = 0.2f
    private const val HEAD_RADIUS = 0.07f

    private fun colorOf(axis: GizmoAxis, hovered: Boolean): Rgba {
        val k = if (hovered) 0.6f else 0f
        return Rgba(axis.red + (1f - axis.red) * k, axis.green + (1f - axis.green) * k, axis.blue + (1f - axis.blue) * k, 1f)
    }

    fun draw(out: LineSink, handles: GizmoHandles, hovered: GizmoAxis?) {
        for (axis in GizmoAxis.entries) {
            val color = colorOf(axis, axis == hovered)
            when (handles.mode) {
                GizmoMode.MOVE -> arrow(out, handles, axis, color)
                GizmoMode.ROTATE -> ring(out, handles, axis, color)
            }
        }
    }

    private fun arrow(out: LineSink, handles: GizmoHandles, axis: GizmoAxis, color: Rgba) {
        out.line(handles.origin, handles.tip(axis), color)
        val a = handles.axisVector(axis)
        val tip = handles.tip(axis).toVector3()
        val base = Vector3(tip).mulAdd(a, -HEAD_LENGTH * handles.size)
        val side = if (axis == GizmoAxis.Y) Vector3.X else Vector3.Y
        val u = Vector3(a).crs(side).nor().scl(HEAD_RADIUS * handles.size)
        val v = Vector3(a).crs(u).nor().scl(HEAD_RADIUS * handles.size)
        for ((su, sv) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
            out.line(tip.toVec3(), Vector3(base).mulAdd(u, su).mulAdd(v, sv).toVec3(), color)
        }
    }

    private fun ring(out: LineSink, handles: GizmoHandles, axis: GizmoAxis, color: Rgba) {
        val n = handles.axisVector(axis)
        val side = if (axis == GizmoAxis.Y) Vector3.X else Vector3.Y
        val u = Vector3(n).crs(side).nor()
        val v = Vector3(n).crs(u).nor()
        val center = handles.origin.toVector3()
        fun at(i: Int): Vector3 {
            val angle = 2.0 * PI * i / RING_SEGMENTS
            return Vector3(center).mulAdd(u, cos(angle).toFloat() * handles.size).mulAdd(v, sin(angle).toFloat() * handles.size)
        }
        for (i in 0 until RING_SEGMENTS) out.line(at(i).toVec3(), at(i + 1).toVec3(), color)
    }
}
