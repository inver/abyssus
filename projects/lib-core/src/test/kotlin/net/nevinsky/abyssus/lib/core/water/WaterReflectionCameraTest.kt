/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterReflectionCameraTest {
    private val mirror = WaterReflectionCamera()

    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    private fun assertVec(expected: Vector3, actual: Vector3, eps: Float = 1e-5f) {
        assertEquals(expected.x, actual.x, eps); assertEquals(expected.y, actual.y, eps); assertEquals(expected.z, actual.z, eps)
    }

    @Test fun positionIsMirroredAcrossTheLevel() {
        assertVec(Vector3(3f, -4f, 5f), mirror.point(Vector3(3f, 8f, 5f), 2f))
        assertVec(Vector3(3f, 2f, 5f), mirror.point(Vector3(3f, 2f, 5f), 2f))
        assertVec(Vector3(1f, 2f, 3f), mirror.point(Vector3(1f, -2f, 3f), 0f))
    }

    @Test fun directionAndUpOnlyFlipTheirVerticalPart() {
        val pose = mirror.pose(Vector3(0f, 10f, 0f), Vector3(0.6f, -0.8f, 0f), Vector3(0.8f, 0.6f, 0f), 4f)
        assertVec(Vector3(0f, -2f, 0f), pose.position)
        assertVec(Vector3(0.6f, 0.8f, 0f), pose.direction)
        assertVec(Vector3(0.8f, -0.6f, 0f), pose.up)
    }

    @Test fun theMatrixMirrorsPointsAndDirectionsLikeThePoseDoes() {
        val m = mirror.matrix(2f)
        assertVec(Vector3(3f, -4f, 5f), Vector3(3f, 8f, 5f).mul(m))
        assertVec(Vector3(3f, 2f, 5f), Vector3(3f, 2f, 5f).mul(m))
        val twice = Matrix4(m).mul(m)
        assertVec(Vector3(7f, 1f, -3f), Vector3(7f, 1f, -3f).mul(twice))
    }

    @Test fun theMirrorFlipsWinding() {
        assertTrue(mirror.flipsWinding)
        assertTrue("determinant of the mirror is negative", mirror.matrix(3f).det() < 0f)
    }

    @Test fun aTriangleKeepsItsWindingInTheMainViewAndLosesItThroughTheMirror() {
        val camera = PerspectiveCamera(60f, 800f, 600f).also {
            it.position.set(0f, 10f, 20f); it.lookAt(0f, 0f, 0f); it.near = 0.1f; it.far = 200f; it.update()
        }
        val a = Vector3(-1f, 5f, 0f)
        val b = Vector3(1f, 5f, 0f)
        val c = Vector3(0f, 7f, 0f)
        fun signedArea(matrix: Matrix4): Float {
            val p = listOf(a, b, c).map { Vector3(it).prj(matrix) }
            return (p[1].x - p[0].x) * (p[2].y - p[0].y) - (p[2].x - p[0].x) * (p[1].y - p[0].y)
        }
        val main = signedArea(camera.combined)
        val reflected = signedArea(Matrix4(camera.combined).mul(mirror.matrix(2f)))
        assertTrue(main > 0f)
        assertTrue("winding flips through the mirror", reflected < 0f)
    }

    @Test fun aPointOnThePlaneProjectsToTheSamePixelWithAndWithoutTheMirror() {
        val camera = PerspectiveCamera(60f, 800f, 600f).also {
            it.position.set(5f, 12f, 25f); it.lookAt(0f, 0f, 0f); it.near = 0.1f; it.far = 200f; it.update()
        }
        val onPlane = Vector3(4f, 2f, -3f)
        val direct = Vector3(onPlane).prj(camera.combined)
        val viaMirror = Vector3(onPlane).prj(Matrix4(camera.combined).mul(mirror.matrix(2f)))
        assertVec(direct, viaMirror, 1e-4f)
    }

    @Test fun theClipPlaneKeepsTheSurfaceSideAndTheBiasLowersIt() {
        val plane = mirror.clipPlane(2f, 0.5f)
        fun keeps(y: Float) = plane[0] * 0f + plane[1] * y + plane[3] >= 0f
        assertTrue(keeps(5f))
        assertTrue(keeps(1.6f))
        assertTrue("within the bias below the level", keeps(1.5f))
        assertTrue(!keeps(1.4f))
    }

    @Test fun theBiasGrowsWithWavesAndDistanceButStaysBounded() {
        val small = mirror.bias(0.0, 0f)
        assertEquals(0.02f, small, 1e-6f)
        assertTrue(mirror.bias(0.6, 100f) > mirror.bias(0.08, 10f))
        assertEquals(2f, mirror.bias(1e9, 1e9f), 0f)
    }
}
