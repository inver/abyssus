/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview.gizmo

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import net.nevinsky.abyssus.sceneview.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GizmoHitTest {
    /** One world unit per pixel keeps sizes readable: arrows are 90 long, tolerance 8. */
    private fun handles(mode: GizmoMode) = GizmoHandles(Vec3(0f, 0f, 0f), mode, 1f)

    private fun ray(from: Vector3, to: Vector3) = Ray(from, Vector3(to).sub(from).nor())

    @Test
    fun aRayThroughTheXArrowTipHitsX() {
        val h = handles(GizmoMode.MOVE)
        assertEquals(GizmoAxis.X, GizmoHit.find(ray(Vector3(90f, 0f, 500f), Vector3(90f, 0f, 0f)), h))
    }

    @Test
    fun aRayBetweenTheArrowsHitsNothing() {
        val h = handles(GizmoMode.MOVE)
        assertNull(GizmoHit.find(ray(Vector3(60f, 60f, 500f), Vector3(60f, 60f, 0f)), h))
    }

    @Test
    fun aRayPastTheArrowTipHitsNothing() {
        val h = handles(GizmoMode.MOVE)
        assertNull(GizmoHit.find(ray(Vector3(120f, 0f, 500f), Vector3(120f, 0f, 0f)), h))
    }

    @Test
    fun aRayThroughTheYRingRimHitsY() {
        val h = handles(GizmoMode.ROTATE)
        // the Y ring lies in the XZ plane: its rim passes (90, 0, 0); a ray from above crosses the plane there
        assertEquals(GizmoAxis.Y, GizmoHit.find(ray(Vector3(90f, 500f, 0f), Vector3(90f, 0f, 0f)), h))
    }

    @Test
    fun aRayInsideTheRingHitsNothing() {
        val h = handles(GizmoMode.ROTATE)
        assertNull(GizmoHit.find(ray(Vector3(30f, 500f, 0f), Vector3(30f, 0f, 0f)), h))
    }

    @Test
    fun theNearerOverlappingHandleWins() {
        val h = handles(GizmoMode.MOVE)
        // one ray through both the X arrow (40, 0, 0) and the Y arrow (0, 40, 0), seen from either side
        assertEquals(GizmoAxis.X, GizmoHit.find(ray(Vector3(80f, -40f, 0f), Vector3(0f, 40f, 0f)), h))
        assertEquals(GizmoAxis.Y, GizmoHit.find(ray(Vector3(-40f, 80f, 0f), Vector3(40f, 0f, 0f)), h))
    }
}
