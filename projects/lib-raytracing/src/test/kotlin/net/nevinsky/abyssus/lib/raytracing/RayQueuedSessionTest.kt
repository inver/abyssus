/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayQueuedSessionTest {
    private val camera = RaySliceCamera(listOf(0f, 0f, 1f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
    private val scene = RaySceneSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun request(revision: Long) = RaySceneRequest(RayFrameKey(1, 1, revision, 1), 1, 1, camera, scene)

    /** Completes each submission on the next poll; [reject] makes that completion fail the way a native frame error does. */
    private class Driver : RaySession {
        var reject = false
        var submitted = 0
        private var pending: RaySceneRequest? = null
        override val geometryBuilds = 0L
        override fun submit(request: RayRequest) = error("scene requests only")
        override fun submit(request: RaySceneRequest) { submitted++; pending = request }
        override fun poll(): RayFrame? {
            val request = pending ?: return null
            pending = null
            if (reject) throw UnsupportedOperationException("Nested, intersecting or unmatched dielectric boundary")
            return RayFrame(request.key, 1, 1, floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(1f))
        }
        override fun dispose() {}
    }

    @Test fun aRejectedFrameDoesNotBlockLaterSubmissions() {
        val driver = Driver()
        val session = RayQueuedSession(driver, RayDeviceHealth())
        driver.reject = true
        session.submit(request(1))
        session.submit(request(2)) // queued behind the frame that will be rejected
        assertThrows(UnsupportedOperationException::class.java) { session.poll() }
        driver.reject = false
        session.submit(request(3))
        assertEquals("the next request reaches the driver at once", 2, driver.submitted)
        assertEquals(3L, session.poll()!!.key.cameraRevision)
    }
}
