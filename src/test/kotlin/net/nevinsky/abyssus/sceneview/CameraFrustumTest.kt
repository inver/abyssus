/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFrustumTest {
    private fun camera(direction: Vec3, lookAtId: String? = null, position: Vec3 = Vec3(0f, 0f, 0f)) =
        CameraPlacement("c", "c", position, direction, lookAtId, near = 1f, far = 10f, fieldOfView = 90f)

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
        assertEquals(expected.z, actual.z, 1e-4f)
    }

    @Test
    fun nearCornersOfAQuarterTurnCameraLookingAlongX() {
        val f = CameraFrustum.of(camera(Vec3(1f, 0f, 0f)), emptyMap(), 1f)
        assertEquals(8, f.corners.size)
        for (corner in f.corners.take(4)) {
            assertEquals(1f, corner.x, 1e-4f)
            assertEquals(1f, kotlin.math.abs(corner.y), 1e-4f)
            assertEquals(1f, kotlin.math.abs(corner.z), 1e-4f)
        }
        for (corner in f.corners.drop(4)) {
            assertEquals(10f, corner.x, 1e-4f)
            assertEquals(10f, kotlin.math.abs(corner.y), 1e-4f)
        }
    }

    @Test
    fun aspectWidensTheFrustumHorizontally() {
        val f = CameraFrustum.of(camera(Vec3(1f, 0f, 0f)), emptyMap(), 2f)
        assertEquals(2f, f.corners.take(4).maxOf { kotlin.math.abs(it.z) }, 1e-4f)
        assertEquals(1f, f.corners.take(4).maxOf { kotlin.math.abs(it.y) }, 1e-4f)
    }

    @Test
    fun aResolvableTargetOverridesTheDirection() {
        val f = CameraFrustum.of(camera(Vec3(1f, 0f, 0f), lookAtId = "t"), mapOf("t" to Vec3(0f, 0f, -5f)), 1f)
        assertVec(Vec3(0f, 0f, -1f), f.direction)
    }

    @Test
    fun anUnknownTargetFallsBackToTheDirection() {
        val f = CameraFrustum.of(camera(Vec3(2f, 0f, 0f), lookAtId = "missing"), mapOf("t" to Vec3(0f, 0f, -5f)), 1f)
        assertVec(Vec3(1f, 0f, 0f), f.direction)
    }

    @Test
    fun aTargetAtTheCameraFallsBackToTheDirection() {
        val f = CameraFrustum.of(camera(Vec3(0f, 0f, 1f), lookAtId = "t"), mapOf("t" to Vec3(0f, 0f, 0f)), 1f)
        assertVec(Vec3(0f, 0f, 1f), f.direction)
    }

    @Test
    fun straightUpDoesNotCollapseTheFrustum() {
        val f = CameraFrustum.of(camera(Vec3(0f, 1f, 0f)), emptyMap(), 1f)
        assertEquals(1f, f.corners[0].y, 1e-4f)
        assertEquals(1f, kotlin.math.abs(f.corners[0].x), 1e-4f)
    }
}
