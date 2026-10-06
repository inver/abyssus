/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.ray.RayBackendSelector
import net.nevinsky.abyssus.editor.ray.RayBackendService
import net.nevinsky.abyssus.editor.ResourceEditorMessages
import net.nevinsky.abyssus.editor.ray.RayModePhase

import net.nevinsky.abyssus.raytracing.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

class RayDeviceLossTest {
    @Test fun lossClearsEveryViewWithinOneSecondAndExplicitRetryReprobesOnSharedWorker() {
        val device = Device()
        val service = service(device)
        val first = service.newView<String>("first")
        val second = service.newView<String>("second")
        try {
            onEdt {
                first.setRequested(true); second.setRequested(true)
                first.offer(input(1), "first camera/selection/drag")
                second.offer(input(1), "second camera/selection/drag")
            }
            await { first.latest() != null && second.latest() != null }
            assertEquals(1, device.probes.get())
            assertEquals("first camera/selection/drag", first.latest()!!.metadata)
            device.loss.set("Injected GPU loss")
            val began = System.nanoTime()
            onEdt { first.offer(input(2), "first camera/selection/drag") }
            await(1000) { first.mode.phase == RayModePhase.Failed && second.mode.phase == RayModePhase.Failed }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 1000)
            assertNull(first.latest()); assertNull(second.latest())
            assertFalse(first.mode.requested); assertFalse(second.mode.requested)
            onEdt { repeat(100) { first.offer(input(3), "first camera/selection/drag") } }
            assertEquals(1, device.probes.get())
            onEdt {
                first.retry()
                first.offer(input(3), "first camera/selection/drag")
            }
            await { first.latest() != null }
            assertEquals(2, device.probes.get())
            assertEquals("first camera/selection/drag", first.latest()!!.metadata)
            assertEquals(RayModePhase.Failed, second.mode.phase)
            assertEquals(1, device.threads.distinct().size)
            assertTrue(device.threads.none { it.startsWith("AWT-EventQueue") })
        } finally { first.close(); second.close(); service.close() }
    }

    @Test fun hiddenAndClosedViewsDiscardPendingFramesWithoutBlockingTheEdtOnNativeCleanup() {
        val device = Device()
        val service = service(device)
        val view = service.newView<String>("view")
        try {
            onEdt { view.setRequested(true); view.offer(input(1), "frame") }
            await { view.latest() != null }
            device.cleanupEntered = CountDownLatch(1)
            device.cleanupRelease = CountDownLatch(1)
            onEdt { view.setVisible(false) }
            assertNull(view.latest())
            assertTrue(device.cleanupEntered!!.await(2, TimeUnit.SECONDS))
            val responsive = CountDownLatch(1)
            SwingUtilities.invokeLater { responsive.countDown() }
            assertTrue("Native cleanup cannot hold EDT", responsive.await(500, TimeUnit.MILLISECONDS))
            onEdt { view.offer(input(2), "hidden stale frame"); view.setVisible(true) }
            device.cleanupRelease!!.countDown()
            onEdt { view.offer(input(3), "replacement frame") }
            await { view.latest()?.metadata == "replacement frame" }
            assertEquals(1, device.probes.get())
            onEdt { view.close() }
            assertNull(view.latest())
        } finally {
            device.cleanupRelease?.countDown()
            view.close(); service.close()
        }
    }

    @Test fun ordinaryPreparationFailureDoesNotRetryOnOffers() {
        val device = Device().apply { failOpen = true }
        val service = service(device)
        val view = service.newView<String>("view")
        try {
            onEdt { view.setRequested(true); view.offer(input(1), "frame") }
            await { view.mode.phase == RayModePhase.Failed }
            onEdt { repeat(100) { view.offer(input(it.toLong()), "ignored") } }
            assertEquals(1, device.probes.get())
            assertEquals(1, device.opened.get())
            assertNull(view.latest())
            device.failOpen = false
            onEdt { view.retry(); view.offer(input(2), "retry") }
            await { view.latest() != null }
            assertEquals(2, device.opened.get())
        } finally { view.close(); service.close() }
    }

    private fun service(device: Device) = RayBackendService(
        RayBackendSelector("metal", "Mac OS X", mapOf("metal" to { device })),
        messages = ResourceEditorMessages(), publish = { SwingUtilities.invokeLater(it) },
    )

    private class Device : RayBackendProvider {
        val probes = AtomicInteger()
        val opened = AtomicInteger()
        val threads = CopyOnWriteArrayList<String>()
        val loss = AtomicReference<String?>()
        @Volatile var failOpen = false
        @Volatile var cleanupEntered: CountDownLatch? = null
        @Volatile var cleanupRelease: CountDownLatch? = null
        private fun owner() {
            assertFalse(SwingUtilities.isEventDispatchThread())
            threads += Thread.currentThread().name
        }
        override fun probe(): RayCapability {
            owner(); probes.incrementAndGet()
            val health = RayDeviceHealth()
            return RayCapability.Available(object : RayBackend {
                override val info = RayBackendInfo("Metal", "Fake GPU")
                override val capabilities = RayCapabilities(true, true, true, 4096, 128L * 1024 * 1024)
                override fun openSession(viewId: String, limits: RayLimits): RaySession {
                    owner(); opened.incrementAndGet()
                    check(!failOpen) { "Preparation failed" }
                    return RayQueuedSession(object : RaySession {
                        private var pending: RaySceneRequest? = null
                        override fun submit(request: RayRequest) = error("Tests offer complete scenes")
                        override fun submit(request: RaySceneRequest) { owner(); pending = request }
                        override fun poll(): RayFrame? {
                            owner()
                            loss.getAndSet(null)?.let(health::reportLost)
                            health.checkUsable()
                            val request = pending ?: return null
                            pending = null
                            return RayFrame(request.key, request.width, request.height,
                                FloatArray(request.width * request.height * 4), FloatArray(request.width * request.height) { 1f })
                        }
                        override fun dispose() {
                            owner()
                            cleanupEntered?.countDown()
                            check(cleanupRelease?.await(3, TimeUnit.SECONDS) != false) { "Cleanup was not released" }
                            pending = null
                        }
                    }, health)
                }
                override fun dispose() { owner() }
            })
        }
    }

    private fun input(revision: Long): RayRenderInput {
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        val scene = RaySceneSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return RayRenderInput(RaySceneRequest(RayFrameKey(1, 1, revision, revision), 4, 4, camera, scene), RayDisplayKey(4, 4, "orbit"), revision)
    }
    private fun onEdt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
    private fun await(timeoutMillis: Long = 3000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("Timed out waiting for ray lifecycle", condition())
    }
}
