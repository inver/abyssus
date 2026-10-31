/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vitrine's camera: in front of the chosen plane on its table, gliding from table to table. */
class PlaneSelectCameraTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // the camera's frustum update is native
    }

    // a plane on a 0.75 m table, its nose along +X
    private val first = Vector3(30f, 0.9f, 0f)
    private val second = Vector3(30f, 0.9f, 3f)
    private val nose = Vector3(1f, 0f, 0f)

    private fun settle(cameras: Cameras, plane: Vector3) = repeat(120) { cameras.planeSelect(plane, nose, 1f / 60f) }

    @Test
    fun theEyeStaysAboveTheTableTopAndLooksAtThePlane() {
        val cameras = Cameras(1280f, 720f)
        settle(cameras, first)
        val camera = cameras.camera
        assertTrue("eye ${camera.position}", camera.position.y > first.y)
        assertTrue(camera.frustum.pointInFrustum(first))
        // in front of the nose, not behind the plane
        assertTrue(camera.position.x > first.x)
    }

    @Test
    fun theCameraGlidesToTheNextTableAndEndsInFrontOfItsPlane() {
        val cameras = Cameras(1280f, 720f)
        settle(cameras, first)
        val before = Vector3(cameras.camera.position)

        cameras.planeSelect(second, nose, 1f / 60f)
        val partway = Vector3(cameras.camera.position)
        assertTrue("moved toward the next table", partway.z > before.z)
        assertTrue("not there in one frame", partway.dst(Vector3(second).add(2.4f, 0.9f, 1.4f)) > 0.5f)

        settle(cameras, second)
        val camera = cameras.camera
        assertEquals(second.z, camera.position.z, 1.6f)
        assertTrue(camera.frustum.pointInFrustum(second))
    }

    @Test
    fun theFirstFrameOnPlaneSelectStartsInFrontWithNoGlideFromElsewhere() {
        val cameras = Cameras(1280f, 720f)
        cameras.planeSelect(first, nose, 1f / 60f)
        assertTrue(cameras.camera.frustum.pointInFrustum(first))
        assertEquals(first.x + 2.4f, cameras.camera.position.x, 0.05f)
    }
}
