/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.raytracing.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/** A provider with injectable unavailability, preparation/render failure, device loss and delayed completion. */
internal class RayFakeDevice(private val name: String = "Metal", private val gpu: String = "Fake GPU") : RayBackendProvider {
    val probes = AtomicInteger()
    val opened = AtomicInteger()
    val disposedSessions = AtomicInteger()
    val submitted = AtomicInteger()
    /** The scene of the most recent submission, for tests that check what the plugin converted. */
    @Volatile var lastScene: RaySceneSnapshot? = null
    val threads = CopyOnWriteArrayList<String>()
    val loss = AtomicReference<String?>()
    @Volatile var unavailable: RayUnavailableReason? = null
    @Volatile var failOpen = false
    @Volatile var failRender = false
    @Volatile var completionGate: CountDownLatch? = null
    @Volatile var sceneOptics = false

    private fun owner() { threads += Thread.currentThread().name }

    override fun probe(): RayCapability {
        owner(); probes.incrementAndGet()
        unavailable?.let { return RayCapability.Unavailable(it, "$gpu: test") }
        val health = RayDeviceHealth()
        return RayCapability.Available(object : RayBackend {
            override val info = RayBackendInfo(name, gpu)
            override val capabilities = RayCapabilities(true, true, true, 4096, 128L * 1024 * 1024, sceneOptics = sceneOptics)
            override fun openSession(viewId: String, limits: RayLimits): RaySession {
                owner(); opened.incrementAndGet()
                check(!failOpen) { "Preparation failed" }
                return RayQueuedSession(object : RaySession {
                    private var pending: RaySceneRequest? = null
                    override fun submit(request: RayRequest) = error("Tests offer complete scenes")
                    override fun submit(request: RaySceneRequest) { owner(); submitted.incrementAndGet(); lastScene = request.scene; pending = request }
                    override fun poll(): RayFrame? {
                        owner()
                        loss.getAndSet(null)?.let(health::reportLost)
                        health.checkUsable()
                        check(!failRender) { "Render failed" }
                        completionGate?.let { gate -> if (!gate.await(0, TimeUnit.MILLISECONDS)) return null }
                        val request = pending ?: return null
                        pending = null
                        return RayFrame(request.key, request.width, request.height,
                            FloatArray(request.width * request.height * 4), FloatArray(request.width * request.height) { 1f })
                    }
                    override fun dispose() { owner(); disposedSessions.incrementAndGet(); pending = null }
                }, health)
            }
            override fun dispose() { owner() }
        })
    }

    companion object {
        fun service(vararg devices: Pair<String, RayFakeDevice>, backend: String = "auto", os: String = "Mac OS X") = RayBackendService(
            RayBackendSelector(backend, os, devices.associate { (name, device) -> name to { device } }),
            publish = { SwingUtilities.invokeLater(it) },
        )

        fun await(timeoutMillis: Long = 3000, what: String = "ray state", condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (!condition() && System.nanoTime() < deadline) {
                // publication to the mode goes through the EDT, so let it run
                SwingUtilities.invokeAndWait { }
                Thread.sleep(2)
            }
            org.junit.Assert.assertTrue("Timed out waiting for $what", condition())
        }
    }
}
