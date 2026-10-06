/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.gizmo

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.sceneview.toVector3
import kotlin.math.abs

/** How close, in pixels, a ray must pass to a handle to grab it. */
const val TOLERANCE_PIXELS = 8f

/** Finds the gizmo handle under a ray. No GL needed. */
class GizmoHit {
    /** The handle of [handles] nearest along [ray] that it passes within [TOLERANCE_PIXELS] of, or null. */
    fun find(ray: Ray, handles: GizmoHandles): GizmoAxis? {
        val tolerance = TOLERANCE_PIXELS * handles.worldPerPixel
        var best: GizmoAxis? = null
        var bestT = Float.MAX_VALUE
        for (axis in GizmoAxis.entries) {
            val t = when (handles.mode) {
                GizmoMode.MOVE -> arrowHit(ray, handles, axis, tolerance)
                GizmoMode.ROTATE -> ringHit(ray, handles, axis, tolerance)
            } ?: continue
            if (t < bestT) {
                bestT = t
                best = axis
            }
        }
        return best
    }

    /** The distance along [ray] of the point nearest the arrow, when that is within [tolerance] of it. */
    private fun arrowHit(ray: Ray, handles: GizmoHandles, axis: GizmoAxis, tolerance: Float): Float? {
        val origin = handles.origin.toVector3()
        val a = handles.axisVector(axis)
        val w = Vector3(ray.origin).sub(origin)
        val b = ray.direction.dot(a)
        val d = ray.direction.dot(w)
        val e = a.dot(w)
        val denominator = 1f - b * b
        // parallel lines are equally close everywhere along the arrow: take its middle
        var s = if (denominator < 1e-6f) handles.size / 2f else (e - b * d) / denominator
        s = s.coerceIn(0f, handles.size)
        val onArrow = Vector3(origin).mulAdd(a, s)
        val t = Vector3(onArrow).sub(ray.origin).dot(ray.direction)
        if (t < 0f) return null
        val onRay = Vector3(ray.origin).mulAdd(ray.direction, t)
        return if (onRay.dst(onArrow) <= tolerance) t else null
    }

    /** The distance along [ray] to the ring's plane, when it crosses it within [tolerance] of the ring. */
    private fun ringHit(ray: Ray, handles: GizmoHandles, axis: GizmoAxis, tolerance: Float): Float? {
        val origin = handles.origin.toVector3()
        val normal = handles.axisVector(axis)
        val t = GizmoMath().rayPlane(ray, origin, normal) ?: return null
        val point = Vector3(ray.origin).mulAdd(ray.direction, t)
        return if (abs(point.dst(origin) - handles.size) <= tolerance) t else null
    }
}
