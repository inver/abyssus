/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSelectionTest {
    private fun assertVec(expected: Vector3, actual: Vector3) {
        assertEquals(expected.x, actual.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
        assertEquals(expected.z, actual.z, 1e-4f)
    }

    @Test fun aBoxAtTheOriginIsLookedAtItsCenter() {
        val f = FrameSelection().frame(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f), 60f, 1f)
        assertVec(Vector3(0f, 0f, 0f), f.target)
    }

    @Test fun aBoxAwayFromTheOriginMovesTheTargetAndKeepsTheDistance() {
        val atOrigin = FrameSelection().frame(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f), 60f, 1.5f)
        val away = FrameSelection().frame(Vector3(9f, -1f, -1f), Vector3(11f, 1f, 1f), 60f, 1.5f)
        assertVec(Vector3(10f, 0f, 0f), away.target)
        assertEquals(atOrigin.distance, away.distance, 1e-4f)
    }

    @Test fun theBoundingSphereFitsTheNarrowSideOfTheView() {
        val box = Vector3(-2f, -2f, -2f) to Vector3(2f, 2f, 2f)
        val radius = Math.sqrt(12.0).toFloat()
        val wide = FrameSelection().frame(box.first, box.second, 60f, 2f)
        val tall = FrameSelection().frame(box.first, box.second, 60f, 0.5f)
        // the vertical field of view is the narrow side of a wide view, so the sphere fits it exactly (plus margin)
        assertEquals(radius / Math.sin(Math.toRadians(30.0)).toFloat() * 1.15f, wide.distance, 1e-3f)
        assertTrue("a narrow view is farther away", tall.distance > wide.distance)
    }

    @Test fun aPointIsFramedAsAUnitSphere() {
        val f = FrameSelection().frame(Vector3(3f, 3f, 3f), Vector3(3f, 3f, 3f), 60f, 1f)
        assertTrue(f.distance > 1f)
        assertVec(Vector3(3f, 3f, 3f), f.target)
    }

    @Test fun framingKeepsTheViewDirection() {
        val orbit = OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0.7f, 0.3f)
        orbit.frame(Framing(Vector3(5f, 1f, 2f), 20f))
        assertVec(Vector3(5f, 1f, 2f), orbit.target)
        assertEquals(20f, orbit.distance, 0f)
        assertEquals(0.7f, orbit.yaw, 0f)
        assertEquals(0.3f, orbit.pitch, 0f)
    }
}
