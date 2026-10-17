/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI

class OrientationProjectionTest {
    private fun mark(p: OrientationProjection, axis: GizmoAxis) = p.marks().first { it.axis == axis }

    @Test fun lookingDownMinusZShowsXRightAndYUp() {
        val p = OrientationProjection(0f, 0f)
        val x = mark(p, GizmoAxis.X)
        val y = mark(p, GizmoAxis.Y)
        assertEquals(1f, x.x, 1e-5f); assertEquals(0f, x.y, 1e-5f)
        assertEquals(0f, y.x, 1e-5f); assertEquals(1f, y.y, 1e-5f)
        assertEquals("Z points at the viewer", 1f, mark(p, GizmoAxis.Z).depth, 1e-5f)
    }

    @Test fun aQuarterTurnAboutYSwapsTheXAndZMarks() {
        val p = OrientationProjection((PI / 2).toFloat(), 0f)
        val x = mark(p, GizmoAxis.X)
        val z = mark(p, GizmoAxis.Z)
        assertEquals(0f, x.x, 1e-5f)
        assertEquals("X now points at the viewer", 1f, x.depth, 1e-5f)
        assertEquals(-1f, z.x, 1e-5f)
    }

    @Test fun theNearestAxisIsPaintedLast() {
        val p = OrientationProjection(0.6f, 0.4f)
        val depths = p.marks().map { it.depth }
        assertEquals(depths.sorted(), depths)
    }

    @Test fun aHitFindsTheMarkUnderThePointAndNotOutsideIt() {
        val p = OrientationProjection(0f, 0f)
        assertEquals(GizmoAxis.X, p.hit(0.95f, 0.02f, 0.2f)?.axis)
        assertNull(p.hit(-0.9f, -0.9f, 0.2f))
    }

    @Test fun whenMarksOverlapTheNearerOneWins() {
        // looking along Y from above: X and Z marks are at the center-ish, Y is nearest and at the center
        val p = OrientationProjection(0f, 1.5533f)
        val hit = p.hit(0f, 0f, 0.5f)
        assertNotNull(hit)
        assertEquals(GizmoAxis.Y, hit!!.axis)
    }
}
