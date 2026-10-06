/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.junit.Assert.*
import org.junit.Test

class RayBackendContractTest {
    @Test
    fun closingOneOwnerLeavesTheSharedBackendAndOtherSessionOpen() {
        val backend = RecordingBackend()
        val first = RaySessionOwner(backend, "first", RayLimits())
        val second = RaySessionOwner(backend, "second", RayLimits())
        assertSame(first.open(), first.open())
        assertNotSame(first.session, second.open())
        assertEquals(listOf("first", "second"), backend.views)
        first.close()
        first.close()
        assertEquals(1, backend.sessions[0].disposals)
        assertEquals(0, backend.sessions[1].disposals)
        assertEquals(0, backend.disposals)
        assertNull(first.session)
        second.close()
    }

    private class RecordingBackend : RayBackend {
        override val info = RayBackendInfo("test", "in-memory")
        override val capabilities = RayCapabilities(true, true, true, 4096, 1024)
        val views = mutableListOf<String>()
        val sessions = mutableListOf<RecordingSession>()
        var disposals = 0
        override fun openSession(viewId: String, limits: RayLimits): RaySession = RecordingSession().also {
            views += viewId
            sessions += it
        }
        override fun dispose() { disposals++ }
    }

    private class RecordingSession : RaySession {
        var disposals = 0
        override fun submit(request: RayRequest) = error("Ownership test does not render")
        override fun poll(): RayFrame? = null
        override fun dispose() { disposals++ }
    }

    @Test
    fun missingFeaturesPreventOpeningNativeSession() {
        var initializations = 0
        val provider = RayBackendProbe({ RayCapabilities(false, true, true, 1024, 1024) }, {
            initializations++
            error("Must not initialize an unsupported backend")
        })
        assertTrue(provider.probe() is RayCapability.Unavailable)
        assertEquals(0, initializations)
    }

    @Test
    fun everyRequiredFeatureIsChecked() {
        for (caps in listOf(
            RayCapabilities(true, false, true, 1024, 1024), RayCapabilities(true, true, false, 1024, 1024),
            RayCapabilities(true, true, true, 0, 1024), RayCapabilities(true, true, true, 1024, 0)
        )) {
            assertTrue(RayBackendProbe({ caps }, { error("Unsupported") }).probe() is RayCapability.Unavailable)
        }
    }

    @Test
    fun nativeLoadFailuresBecomeUnavailable() {
        val provider = RayBackendProbe({ throw UnsatisfiedLinkError("No runtime") }, { error("Must not initialize") })
        val result = provider.probe() as RayCapability.Unavailable
        assertEquals(RayUnavailableReason.RUNTIME_NOT_FOUND, result.reason)
    }

    @Test
    fun cancellationEscapesTheProbe() {
        val provider =
            RayBackendProbe({ throw java.util.concurrent.CancellationException() }, { error("Must not initialize") })
        assertThrows(java.util.concurrent.CancellationException::class.java) { provider.probe() }
    }

    @Test
    fun frameBuffersCannotBeMutatedAndRetainLinearHdrColor() {
        val color = floatArrayOf(4f, 0.2f, 0.3f, 1f)
        val depth = floatArrayOf(0.5f)
        val frame = RayFrame(RayFrameKey(1, 2, 3, 4), 1, 1, color, depth)
        color[0] = 99f
        depth[0] = 1f
        frame.colorValues()[0] = 88f
        frame.depthValues()[0] = 0f
        assertArrayEquals(floatArrayOf(4f, 0.2f, 0.3f, 1f), frame.colorValues(), 0f)
        assertArrayEquals(floatArrayOf(0.5f), frame.depthValues(), 0f)
    }

    @Test
    fun malformedFramesAreRejected() {
        val key = RayFrameKey(1, 2, 3, 4)
        assertThrows(IllegalArgumentException::class.java) { RayFrame(key, 0, 1, floatArrayOf(), floatArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { RayFrame(key, 1, 1, floatArrayOf(0f), floatArrayOf(1f)) }
        for (depth in listOf(Float.NaN, -0.1f, 1.1f)) {
            assertThrows(IllegalArgumentException::class.java) {
                RayFrame(
                    key,
                    1,
                    1,
                    FloatArray(4),
                    floatArrayOf(depth)
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RayFrame(
                key,
                1,
                1,
                floatArrayOf(Float.NaN, 0f, 0f, 1f),
                floatArrayOf(0.5f)
            )
        }
    }
}
