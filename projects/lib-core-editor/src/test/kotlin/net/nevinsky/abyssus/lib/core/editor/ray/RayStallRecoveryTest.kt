/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.raytracing.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Stall → raster fallback → re-probe → resume, on a fake backend and an injected clock (nothing sleeps for the deadline). */
class RayStallRecoveryTest {
    private val now = AtomicLong(1_000_000_000L)

    private class Device : RayBackendProvider {
        val probes = AtomicInteger()
        val submitted = AtomicInteger()
        val hold = AtomicBoolean(true)
        override fun probe(): RayCapability {
            probes.incrementAndGet()
            val health = RayDeviceHealth()
            return RayCapability.Available(object : RayBackend {
                override val info = RayBackendInfo("Fake", "Fake GPU")
                override val capabilities = RayCapabilities(true, true, true, 4096, 128L * 1024 * 1024)
                override fun openSession(viewId: String, limits: RayLimits): RaySession = RayQueuedSession(object : RaySession {
                    private var pending: RaySceneRequest? = null
                    override fun submit(request: RayRequest) = error("scenes only")
                    override fun submit(request: RaySceneRequest) { pending = request; submitted.incrementAndGet() }
                    override fun poll(): RayFrame? {
                        health.checkUsable()
                        if (hold.get()) return null
                        val request = pending ?: return null
                        pending = null
                        return RayFrame(request.key, request.width, request.height,
                            FloatArray(request.width * request.height * 4), FloatArray(request.width * request.height) { 1f })
                    }
                    override fun dispose() { pending = null }
                }, health)
                override fun dispose() {}
            })
        }
    }

    private fun service(device: Device) = RayBackendService(
        RayBackendSelector("vulkan", "Linux", mapOf("vulkan" to { device })),
        messages = ResourceEditorMessages(), publish = { it() }, clock = now::get,
    )

    @Test fun stalledFrameFallsBackThenRetryReprobesAndResumes() {
        val device = Device()
        val service = service(device)
        val view = service.newView<String>("v")
        try {
            view.setRequested(true)
            view.offer(input(1), "frame")
            await { device.submitted.get() == 1 }
            now.addAndGet(TimeUnit.SECONDS.toNanos(11))
            await { view.mode.phase == RayModePhase.Failed }
            assertNull(view.latest())
            assertFalse(view.mode.requested)
            assertEquals(1, device.probes.get())

            device.hold.set(false)
            view.retry()
            view.offer(input(2), "again")
            await { view.latest() != null }
            assertEquals("a stall re-probes rather than reusing the stalled backend", 2, device.probes.get())
        } finally { view.close(); service.close() }
    }

    @Test fun slowFrameWithinTheDeadlineIsNotAStall() {
        val device = Device()
        val service = service(device)
        val view = service.newView<String>("v")
        try {
            view.setRequested(true)
            view.offer(input(1), "frame")
            await { device.submitted.get() == 1 }
            now.addAndGet(TimeUnit.SECONDS.toNanos(5))
            Thread.sleep(50)
            assertNotEquals(RayModePhase.Failed, view.mode.phase)
            device.hold.set(false)
            await { view.latest() != null }
            assertNotEquals(RayModePhase.Failed, view.mode.phase)
        } finally { view.close(); service.close() }
    }

    private fun input(revision: Long): RayRenderInput {
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        val scene = RaySceneSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return RayRenderInput(RaySceneRequest(RayFrameKey(1, 1, revision, revision), 4, 4, camera, scene), RayDisplayKey(4, 4, "orbit"), revision)
    }

    private fun await(timeoutMillis: Long = 3000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("Timed out waiting for ray lifecycle", condition())
    }
}
