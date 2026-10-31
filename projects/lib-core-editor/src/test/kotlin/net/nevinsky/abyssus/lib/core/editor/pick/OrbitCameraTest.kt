/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.pick

import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import com.badlogic.gdx.math.Vector3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitCameraTest {
    private fun assertVec(expected: Vector3, actual: Vector3, eps: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
        assertEquals(expected.z, actual.z, eps)
    }

    @Test
    fun fromReproducesCameraPosition() {
        val params = CameraParams(Vector3(4.8f, 3.3f, 6.0f), Vector3(-0.9485f, -0.1088f, -0.2975f), 1f, 100f, 67f)
        assertVec(params.position, OrbitCamera(params).position(), 0.05f)
    }

    @Test
    fun fromDefaultCameraLooksAtTarget() {
        val cam = OrbitCamera(CameraParams.DEFAULT)
        val p = cam.position()
        val d = CameraParams.DEFAULT.direction
        assertVec(Vector3(p.x + d.x * cam.distance, p.y + d.y * cam.distance, p.z + d.z * cam.distance), cam.target)
    }

    @Test
    fun pitchIsClamped() {
        val cam = OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0f, 0f)
        cam.orbit(0f, 1_000_000f)
        assertTrue(cam.pitch < Math.PI.toFloat() / 2)
        cam.orbit(0f, -2_000_000f)
        assertTrue(cam.pitch > -Math.PI.toFloat() / 2)
    }

    @Test
    fun orbitKeepsDistanceToTarget() {
        val cam = OrbitCamera(Vector3(1f, 2f, 3f), 10f, 0.3f, 0.4f)
        cam.orbit(120f, -40f)
        val p = cam.position()
        val d = kotlin.math.sqrt((p.x - 1f) * (p.x - 1f) + (p.y - 2f) * (p.y - 2f) + (p.z - 3f) * (p.z - 3f))
        assertEquals(10f, d, 1e-3f)
    }

    @Test
    fun zoomStaysWithinLimits() {
        val cam = OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0f, 0f)
        cam.zoom(10_000f)
        assertTrue(cam.distance > 0f)
        cam.zoom(-10_000f)
        assertTrue(cam.distance.isFinite() && cam.distance <= 5000f)
    }

    @Test
    fun panMovesTargetAndKeepsDistance() {
        val cam = OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0f, 0f)
        val before = cam.target
        cam.pan(50f, 0f)
        assertTrue(cam.target != before)
        assertEquals(10f, cam.distance, 0f)
    }

    @Test
    fun aspectIsNullForNonPositiveSizes() {
        assertNull(aspectOf(0, 0))
        assertNull(aspectOf(100, 0))
        assertNull(aspectOf(0, 100))
        assertNull(aspectOf(-1, 5))
        assertEquals(2f, aspectOf(200, 100)!!, 0f)
    }

    @Test
    fun resetReturnsToTheGivenCamera() {
        val params = CameraParams(Vector3(4.8f, 3.3f, 6.0f), Vector3(-0.9485f, -0.1088f, -0.2975f), 1f, 100f, 67f)
        val cam = OrbitCamera(CameraParams.DEFAULT)
        cam.orbit(200f, 50f)
        cam.zoom(3f)
        cam.reset(params)
        assertVec(params.position, cam.position(), 0.05f)
    }

    @Test
    fun lookingAlongAnAxisPutsTheEyeOnTheOppositeSideKeepingTargetAndDistance() {
        val orbit = OrbitCamera(Vector3(1f, 2f, 3f), 8f, 0.4f, 0.2f)
        orbit.lookAlong(Vector3(0f, 0f, -1f))
        val eye = orbit.position()
        assertEquals(1f, eye.x, 1e-4f)
        assertEquals(2f, eye.y, 1e-4f)
        assertEquals(3f + 8f, eye.z, 1e-4f)
        assertEquals(Vector3(1f, 2f, 3f), orbit.target)
        assertEquals(8f, orbit.distance, 0f)
    }

    @Test
    fun lookingStraightDownSettlesAtTheHighestPitchKeepingYaw() {
        val orbit = OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0.7f, 0.1f)
        orbit.lookAlong(Vector3(0f, -1f, 0f))
        assertEquals(0.7f, orbit.yaw, 0f)
        assertTrue(orbit.pitch > 1.5f)
        assertTrue(orbit.position().y > 9.9f)
    }

    @Test
    fun aZeroDirectionChangesNothing() {
        val orbit = OrbitCamera(Vector3(0f, 0f, 0f), 10f, 0.7f, 0.1f)
        orbit.lookAlong(Vector3(0f, 0f, 0f))
        assertEquals(0.7f, orbit.yaw, 0f)
        assertEquals(0.1f, orbit.pitch, 0f)
    }
}
