/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.gizmo

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.sceneview.toVec3
import net.nevinsky.abyssus.sceneview.toVector3
import kotlin.math.atan2

/** What a drag has done to the object so far; [direction] is the turned direction for objects that have one. */
data class DragResult(val transform: PlacementTransform, val direction: Vec3?)

internal class GizmoMath {
    /** The distance along [ray] to the plane through [point] with [normal], or null when parallel or behind. */
    fun rayPlane(ray: Ray, point: Vector3, normal: Vector3): Float? {
        val denominator = normal.dot(ray.direction)
        if (kotlin.math.abs(denominator) < 1e-5f) return null
        val t = normal.dot(Vector3(point).sub(ray.origin)) / denominator
        return if (t >= 0f) t else null
    }

    /** The parameter on the line through [point] along the unit [axis] of its point nearest [ray]; null when they are parallel. */
    fun rayLine(ray: Ray, point: Vector3, axis: Vector3): Float? {
        val w = Vector3(ray.origin).sub(point)
        val b = ray.direction.dot(axis)
        val denominator = 1f - b * b
        if (denominator < 1e-6f) return null
        return (axis.dot(w) - b * ray.direction.dot(w)) / denominator
    }
}

/**
 * One drag of a gizmo handle. Created at the press with the object's transform and the ray under the cursor, it turns
 * every later ray into the object's new transform: [GizmoMode.MOVE] slides it along [axis] by how far the cursor's
 * closest point on the axis line has travelled, [GizmoMode.ROTATE] turns it about [axis] by the angle the cursor has
 * swept around the object in the ring's plane. [startDirection] (a light's or camera's view direction) is turned with it.
 * No GL needed.
 */
class GizmoDrag(
    private val mode: GizmoMode,
    private val axis: GizmoAxis,
    val start: PlacementTransform,
    startRay: Ray,
    private val startDirection: Vec3? = null,
) {
    private val origin = start.position.toVector3()
    private val axisVector = axis.direction.toVector3()
    private val startParameter: Float? = when (mode) {
        GizmoMode.MOVE -> GizmoMath().rayLine(startRay, origin, axisVector)
        GizmoMode.ROTATE -> null
    }
    private val startArm: Vector3? = when (mode) {
        GizmoMode.MOVE -> null
        GizmoMode.ROTATE -> armOf(startRay)
    }

    /** Whether the press could be turned into a drag at all (the ray was not parallel to the axis or the ring). */
    val isUsable: Boolean get() = if (mode == GizmoMode.MOVE) startParameter != null else startArm != null

    private var last = DragResult(start, startDirection)

    /** The transform after the cursor has moved to [ray]; the previous result when [ray] cannot be resolved. */
    fun update(ray: Ray): DragResult {
        last = when (mode) {
            GizmoMode.MOVE -> move(ray)
            GizmoMode.ROTATE -> rotate(ray)
        } ?: last
        return last
    }

    private fun move(ray: Ray): DragResult? {
        val from = startParameter ?: return null
        val to = GizmoMath().rayLine(ray, origin, axisVector) ?: return null
        val shift = Vector3(axisVector).scl(to - from)
        val p = Vector3(origin).add(shift)
        return DragResult(start.copy(position = p.toVec3()), startDirection)
    }

    private fun rotate(ray: Ray): DragResult? {
        val from = startArm ?: return null
        val to = armOf(ray) ?: return null
        val angle = atan2(axisVector.dot(Vector3(from).crs(to)), from.dot(to))
        val delta = Quaternion(axisVector, Math.toDegrees(angle.toDouble()).toFloat())
        val q = Quaternion(start.rotation.x, start.rotation.y, start.rotation.z, start.rotation.w).mulLeft(delta).nor()
        val direction = startDirection?.let { delta.transform(it.toVector3()).toVec3() }
        return DragResult(start.copy(rotation = Quat(q.x, q.y, q.z, q.w)), direction)
    }

    /** The vector from the object to where [ray] crosses the ring's plane. */
    private fun armOf(ray: Ray): Vector3? {
        val t = GizmoMath().rayPlane(ray, origin, axisVector) ?: return null
        val arm = Vector3(ray.origin).mulAdd(ray.direction, t).sub(origin)
        return arm.takeIf { it.len2() > 1e-12f }
    }
}
