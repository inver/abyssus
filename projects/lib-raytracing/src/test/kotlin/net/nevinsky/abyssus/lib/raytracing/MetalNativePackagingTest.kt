/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class MetalNativePackagingTest {
    @Test fun packagedLibraryLoadsAndCreatesIndependentSessions() {
        assumeTrue(java.lang.Boolean.getBoolean("abyssus.metalTests"))
        val factory = MetalRayBackendFactory()
        val result = factory.probe()
        assertTrue("Metal must support the feasibility slice: $result",result is RayCapability.Available)
        (result as RayCapability.Available).backend.use { backend ->
            assertEquals("Metal",backend.info.name)
            assertTrue(backend.info.gpu.isNotBlank())
            val first = backend.openSession("first",RayLimits())
            val second = backend.openSession("second",RayLimits())
            assertNotSame(first,second)
            first.dispose()
            first.dispose()
            val mesh = RaySliceMesh(floatArrayOf(-2f,-2f,0f, 2f,-2f,0f, 0f,2f,0f),intArrayOf(0,1,2))
            val instance = RaySliceInstance(0,listOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f),listOf(1f,1f,1f))
            val camera = RaySliceCamera(listOf(0f,0f,2f),listOf(0f,0f,-1f),listOf(0f,1f,0f),60f,0.1f,100f)
            second.submit(RayRequest(RayFrameKey(1,1,1,1),1,1,camera,listOf(mesh),listOf(instance)))
            val deadline = System.nanoTime()+5_000_000_000L
            var frame: RayFrame? = null
            while (frame == null && System.nanoTime() < deadline) {
                frame = second.poll()
                if (frame == null) Thread.sleep(1)
            }
            assertNotNull("Packaged shader must render a frame",frame)
            assertTrue(frame!!.depthValues()[0] < 1f)
            second.dispose()
            backend.openSession("optics",RayLimits()).use(::assertPackagedSceneOptics)
        }
    }
}
