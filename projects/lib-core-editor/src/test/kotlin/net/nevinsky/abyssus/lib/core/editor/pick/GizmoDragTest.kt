/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class GizmoDragTest {
    private fun ray(from: Vector3, to: Vector3) = Ray(from, Vector3(to).sub(from).nor())

    private fun at(position: Vector3 = Vector3(0f, 0f, 0f), rotation: Quat = Quat.IDENTITY) =
        PlacementTransform(position, rotation, Vector3(1f, 1f, 1f))

    private fun assertVec(expected: Vector3, actual: Vector3, delta: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
        assertEquals(expected.z, actual.z, delta)
    }

    private fun forward(q: Quat): Vector3 {
        val v = Quaternion(q.x, q.y, q.z, q.w).transform(Vector3(0f, 0f, -1f))
        return Vector3(v.x, v.y, v.z)
    }

    @Test
    fun movingXFromZeroToFiveChangesOnlyX() {
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, at(), ray(Vector3(0f, 10f, 20f), Vector3(0f, 0f, 0f)))
        assertNotNull(drag.isUsable)
        val moved = drag.update(ray(Vector3(5f, 10f, 20f), Vector3(5f, 0f, 0f))).transform
        assertVec(Vector3(5f, 0f, 0f), moved.position)
        assertEquals(Quat.IDENTITY, moved.rotation)
    }

    @Test
    fun movingFollowsTheCursorFromWhereThePressLanded() {
        val start = at(Vector3(1f, 2f, 3f))
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.Y, start, ray(Vector3(10f, 4f, 3f), Vector3(1f, 4f, 3f)))
        val moved = drag.update(ray(Vector3(10f, 7f, 3f), Vector3(1f, 7f, 3f))).transform
        assertVec(Vector3(1f, 5f, 3f), moved.position)
    }

    @Test
    fun aRayParallelToTheAxisIsNotUsable() {
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, at(), Ray(Vector3(-5f, 0f, 0f), Vector3(1f, 0f, 0f)))
        assertFalse(drag.isUsable)
    }

    @Test
    fun aRayThatCannotBeResolvedKeepsTheLastResult() {
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, at(), ray(Vector3(0f, 10f, 20f), Vector3(0f, 0f, 0f)))
        val first = drag.update(ray(Vector3(3f, 10f, 20f), Vector3(3f, 0f, 0f)))
        assertEquals(first, drag.update(Ray(Vector3(-5f, 0f, 0f), Vector3(1f, 0f, 0f))))
    }

    @Test
    fun aQuarterTurnAboutYTurnsForwardFromMinusZToMinusX() {
        // from +Z around to +X is +90 degrees about +Y; the plane crossings are seen from above
        val drag = GizmoDrag(GizmoMode.ROTATE, GizmoAxis.Y, at(), ray(Vector3(0f, 10f, 1f), Vector3(0f, 0f, 1f)))
        val turned = drag.update(ray(Vector3(1f, 10f, 0f), Vector3(1f, 0f, 0f))).transform
        assertVec(Vector3(-1f, 0f, 0f), forward(turned.rotation))
        assertVec(Vector3(0f, 0f, 0f), turned.position)
    }

    @Test
    fun rotatingKeepsThePosition() {
        val start = at(Vector3(4f, 5f, 6f))
        val drag = GizmoDrag(GizmoMode.ROTATE, GizmoAxis.Y, start, ray(Vector3(4f, 20f, 7f), Vector3(4f, 5f, 7f)))
        val turned = drag.update(ray(Vector3(5f, 20f, 6f), Vector3(5f, 5f, 6f))).transform
        assertVec(Vector3(4f, 5f, 6f), turned.position)
    }

    @Test
    fun aCamerasDirectionTurnsByTheSameRotation() {
        val drag = GizmoDrag(
            GizmoMode.ROTATE, GizmoAxis.Y, at(), ray(Vector3(0f, 10f, 1f), Vector3(0f, 0f, 1f)),
            startDirection = Vector3(0f, 0f, -1f),
        )
        val result = drag.update(ray(Vector3(1f, 10f, 0f), Vector3(1f, 0f, 0f)))
        assertVec(Vector3(-1f, 0f, 0f), result.direction!!)
    }

    @Test
    fun rotationComposesWithTheStartRotation() {
        val half = Quaternion(Vector3.Y, 90f)
        val start = at(rotation = Quat(half.x, half.y, half.z, half.w))
        val drag = GizmoDrag(GizmoMode.ROTATE, GizmoAxis.Y, start, ray(Vector3(0f, 10f, 1f), Vector3(0f, 0f, 1f)))
        val turned = drag.update(ray(Vector3(1f, 10f, 0f), Vector3(1f, 0f, 0f))).transform
        assertVec(Vector3(0f, 0f, 1f), forward(turned.rotation)) // 180 degrees about Y
    }

    private val snap = GizmoSnap(0.5f, 15f)

    @Test
    fun aSnappedMoveRoundsTheCoordinateAlongTheAxis() {
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, at(), ray(Vector3(0f, 10f, 20f), Vector3(0f, 0f, 0f)), null, GizmoAxis.X.direction, snap)
        val moved = drag.update(ray(Vector3(2.31f, 10f, 20f), Vector3(2.31f, 0f, 0f))).transform
        assertVec(Vector3(2.5f, 0f, 0f), moved.position)
    }

    @Test
    fun aSnappedMoveKeepsTheOtherCoordinates() {
        val start = at(Vector3(1.3f, 2.2f, 3.7f))
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, start, ray(Vector3(1.3f, 12.2f, 23.7f), Vector3(1.3f, 2.2f, 3.7f)), null, GizmoAxis.X.direction, snap)
        val moved = drag.update(ray(Vector3(2.1f, 12.2f, 23.7f), Vector3(2.1f, 2.2f, 3.7f))).transform
        assertVec(Vector3(2.0f, 2.2f, 3.7f), moved.position)
    }

    @Test
    fun withoutSnapTheRawValueIsKept() {
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, at(), ray(Vector3(0f, 10f, 20f), Vector3(0f, 0f, 0f)))
        val moved = drag.update(ray(Vector3(2.31f, 10f, 20f), Vector3(2.31f, 0f, 0f))).transform
        assertVec(Vector3(2.31f, 0f, 0f), moved.position)
    }

    @Test
    fun aSnappedRotationTurnsInFifteenDegreeSteps() {
        val start = Ray(Vector3(1f, 10f, 0f), Vector3(0f, -1f, 0f))
        val drag = GizmoDrag(GizmoMode.ROTATE, GizmoAxis.Y, at(), start, null, GizmoAxis.Y.direction, snap)
        val a = Math.toRadians(37.0)
        val to = Ray(Vector3(Math.cos(a).toFloat(), 10f, -Math.sin(a).toFloat()), Vector3(0f, -1f, 0f))
        val turned = drag.update(to).transform
        assertVec(Vector3(-0.5f, 0f, -0.8660254f), forward(turned.rotation))
    }

    @Test
    fun aLocalMoveGoesAlongTheObjectsOwnAxis() {
        val axes = gizmoAxes(GizmoSpace.LOCAL, Quaternion(Vector3.Y, 90f).let { Quat(it.x, it.y, it.z, it.w) })
        val localX = axes.getValue(GizmoAxis.X)
        val drag = GizmoDrag(GizmoMode.MOVE, GizmoAxis.X, at(), ray(Vector3(10f, 0f, 0f), Vector3(0f, 0f, 0f)), null, localX)
        val moved = drag.update(ray(Vector3(10f, 0f, -2f), Vector3(0f, 0f, -2f))).transform
        assertVec(Vector3(0f, 0f, -2f), moved.position)
    }

    @Test
    fun aLocalRotationTurnsAboutTheObjectsOwnAxis() {
        // an object turned 90 degrees about Y: its local X axis is world -Z; turning 90 degrees about it
        val start = Quaternion(Vector3.Y, 90f).let { Quat(it.x, it.y, it.z, it.w) }
        val localX = gizmoAxes(GizmoSpace.LOCAL, start).getValue(GizmoAxis.X)
        val drag = GizmoDrag(GizmoMode.ROTATE, GizmoAxis.X, at(rotation = start), Ray(Vector3(0f, 1f, 10f), Vector3(0f, 0f, -1f)), null, localX)
        val turned = drag.update(Ray(Vector3(1f, 0f, 10f), Vector3(0f, 0f, -1f))).transform.rotation
        // the arm went from +Y to +X about the world -Z axis: a quarter turn, so the object's own X axis is still world -Z
        val axisAfter = gizmoAxes(GizmoSpace.LOCAL, turned).getValue(GizmoAxis.X)
        assertVec(Vector3(0f, 0f, -1f), axisAfter)
        assertFalse("the rotation changed", turned == start)
    }
}
