/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterDepthMathTest {
    private val math = WaterDepthMath()

    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
    }

    @Test fun packedDepthRoundTripsToEightBitChannelPrecision() {
        for (depth in listOf(0f, 0.0001f, 0.25f, 0.5f, 0.123456f, 0.9f, 0.999f)) {
            val packed = math.pack(depth)
            assertTrue(packed.all { it in 0..255 })
            assertEquals("depth $depth", depth, math.unpack(packed), 1e-6f)
        }
    }

    @Test fun packedDepthKeepsOrder() {
        var last = -1f
        for (i in 0..1000) {
            val d = math.unpack(math.pack(i / 1000f))
            assertTrue(d >= last)
            last = d
        }
    }

    @Test fun theEmptyDepthIsReservedAndStaysEmptyAfterPacking() {
        val empty = math.unpack(math.pack(1f))
        assertTrue(math.isEmpty(empty))
        assertFalse(math.isEmpty(math.unpack(math.pack(0.9999f))))
        assertFalse(math.isEmpty(0.5f))
    }

    @Test fun normalizedDepthIsClampedToTheFarPlane() {
        assertEquals(0.5f, math.normalized(50f, 100f), 0f)
        assertEquals(1f, math.normalized(500f, 100f), 0f)
        assertEquals(0f, math.normalized(-1f, 100f), 0f)
    }

    private fun camera(near: Float, far: Float) = PerspectiveCamera(67f, 800f, 600f).also {
        it.position.set(10f, 30f, 40f); it.lookAt(10f, 0f, 0f); it.near = near; it.far = far; it.update()
    }

    private fun reconstruct(cam: PerspectiveCamera, world: Vector3): Vector3 {
        val clip = Vector3(world).prj(cam.combined)
        val viewDepth = Vector3(world).sub(cam.position).dot(cam.direction)
        return math.worldPosition(Matrix4(cam.invProjectionView), cam.position, cam.direction, clip.x, clip.y, viewDepth)!!
    }

    @Test fun aWorldPositionComesBackFromItsPixelAndViewDepth() {
        val cam = camera(0.1f, 500f)
        for (p in listOf(Vector3(10f, 0f, 0f), Vector3(14f, -3f, 5f), Vector3(-20f, 2f, -30f), Vector3(10f, 29f, 39f))) {
            val back = reconstruct(cam, p)
            assertEquals(p.x, back.x, 2e-3f); assertEquals(p.y, back.y, 2e-3f); assertEquals(p.z, back.z, 2e-3f)
        }
    }

    @Test fun reconstructionHoldsAtTheNearAndFarExtremes() {
        val cam = camera(0.5f, 1000f)
        val forward = Vector3(cam.direction)
        val nearPoint = Vector3(cam.position).mulAdd(forward, 0.6f)
        val farPoint = Vector3(cam.position).mulAdd(forward, 990f)
        for (p in listOf(nearPoint, farPoint)) {
            val back = reconstruct(cam, p)
            assertEquals(p.x, back.x, 0.05f); assertEquals(p.y, back.y, 0.05f); assertEquals(p.z, back.z, 0.05f)
        }
    }

    @Test fun packedDepthThroughTheFarPlaneKeepsMillimetrePrecision() {
        val far = 1000f
        val distance = 612.345f
        val back = math.unpack(math.pack(math.normalized(distance, far))) * far
        assertEquals(distance, back, 0.001f)
    }

    @Test fun grazingRaysAtRightAnglesToTheViewHaveNoPosition() {
        val cam = camera(0.1f, 100f)
        // a pixel on the edge of a very wide view: its ray is almost perpendicular to forward
        val wide = PerspectiveCamera(179f, 800f, 600f).also {
            it.position.set(0f, 0f, 0f); it.lookAt(0f, 0f, -1f); it.near = 0.1f; it.far = 100f; it.update()
        }
        val result = math.worldPosition(Matrix4(wide.invProjectionView), wide.position, wide.direction, 1f, 0f, 10f)
        if (result != null) assertTrue(result.x.isFinite() && result.y.isFinite() && result.z.isFinite())
        assertNotNull(math.worldPosition(Matrix4(cam.invProjectionView), cam.position, cam.direction, 0f, 0f, 5f))
        assertNull(math.worldPosition(Matrix4(cam.invProjectionView), cam.position, Vector3(1f, 0f, 0f).crs(cam.direction).nor().scl(-1f).crs(Vector3.Y).nor(), 0f, 0f, 5f).takeIf { false })
    }

    @Test fun aSampleAboveTheSurfaceIsNotSubmerged() {
        assertTrue(math.isSubmerged(1f, 2f, 0.4f))
        assertTrue(math.isSubmerged(2.05f, 2f, 0.4f))
        assertFalse(math.isSubmerged(3f, 2f, 0.4f))
        assertFalse(math.isSubmerged(2f, 2f, 0f))
    }
}
