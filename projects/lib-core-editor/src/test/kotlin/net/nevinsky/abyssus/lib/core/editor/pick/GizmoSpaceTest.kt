/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.content.Quat
import org.junit.Assert.assertEquals
import org.junit.Test

class GizmoSpaceTest {
    private fun aboutY(degrees: Float): Quat = Quaternion(Vector3.Y, degrees).let { Quat(it.x, it.y, it.z, it.w) }

    private fun assertVec(expected: Vector3, actual: Vector3) {
        assertEquals(expected.x, actual.x, 1e-5f)
        assertEquals(expected.y, actual.y, 1e-5f)
        assertEquals(expected.z, actual.z, 1e-5f)
    }

    @Test fun worldAxesIgnoreTheRotation() {
        assertEquals(WORLD_AXES, gizmoAxes(GizmoSpace.WORLD, aboutY(90f)))
    }

    @Test fun anUnrotatedObjectHasTheSameAxesInBothSpaces() {
        val local = gizmoAxes(GizmoSpace.LOCAL, Quat.IDENTITY)
        for (axis in GizmoAxis.entries) assertVec(axis.direction, local.getValue(axis))
    }

    @Test fun localAxesTurnWithTheObject() {
        val axes = gizmoAxes(GizmoSpace.LOCAL, aboutY(90f))
        assertVec(Vector3(0f, 0f, -1f), axes.getValue(GizmoAxis.X))
        assertVec(Vector3(0f, 1f, 0f), axes.getValue(GizmoAxis.Y))
        assertVec(Vector3(1f, 0f, 0f), axes.getValue(GizmoAxis.Z))
    }

    @Test fun handlesFollowTheirAxes() {
        val axes = gizmoAxes(GizmoSpace.LOCAL, aboutY(90f))
        val handles = GizmoHandles(Vector3(1f, 2f, 3f), GizmoMode.MOVE, 0.01f, axes)
        val tip = handles.tip(GizmoAxis.X)
        assertEquals(1f, tip.x, 1e-5f)
        assertEquals(3f - handles.size, tip.z, 1e-5f)
    }
}
