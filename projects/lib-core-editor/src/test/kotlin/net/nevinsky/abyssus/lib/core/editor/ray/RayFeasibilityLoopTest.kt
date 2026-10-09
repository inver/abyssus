/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.editor.ray

import net.nevinsky.abyssus.lib.gdx.editor.ray.RayFeasibilityLoop
import net.nevinsky.abyssus.lib.raytracing.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RayFeasibilityLoopTest {
    @Test fun nativeWorkStaysOnOneWorkerAndPendingMotionIsReplaced() {
        val entered = CountDownLatch(1)
        val release = AtomicBoolean(false)
        val disposed = CountDownLatch(1)
        val submissions = mutableListOf<Long>()
        val caller = Thread.currentThread()
        var worker: Thread? = null
        fun owner() {
            assertNotSame(caller, Thread.currentThread())
            if (worker == null) worker = Thread.currentThread()
            assertSame(worker, Thread.currentThread())
        }
        val session = object : RaySession {
            var request: RayRequest? = null
            override fun submit(request: RayRequest) {
                owner()
                assertNull(this.request)
                this.request = request
                synchronized(submissions) { submissions.add(request.key.cameraRevision) }
                entered.countDown()
            }
            override fun poll(): RayFrame? {
                owner()
                if (!release.get()) return null
                val current = request ?: return null
                request = null
                return RayFrame(current.key, 1, 1, floatArrayOf(1f, 0f, 0f, 1f), floatArrayOf(0.5f))
            }
            override fun dispose() { owner(); disposed.countDown() }
        }
        val backend = object : RayBackend {
            override val info = RayBackendInfo("test", "fake")
            override val capabilities = RayCapabilities(true, true, true, 4096, 1024)
            override fun openSession(viewId: String, limits: RayLimits): RaySession { owner(); return session }
            override fun dispose() { owner() }
        }
        val loop = RayFeasibilityLoop { owner(); RayCapability.Available(backend) }
        try {
            loop.offer(request(1))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            loop.offer(request(2))
            loop.offer(request(3))
            release.set(true)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (loop.latest()?.frame?.key?.cameraRevision != 3L && System.nanoTime() < deadline) Thread.sleep(1)
            assertEquals(3L, loop.latest()?.frame?.key?.cameraRevision)
            assertEquals(listOf(1L, 3L), synchronized(submissions) { submissions.toList() })
        } finally { loop.close() }
        assertTrue(disposed.await(5, TimeUnit.SECONDS))
        assertNull(loop.latest())
    }

    @Test fun ordinaryConstructionAndCloseDoNotProbe() {
        val probes = AtomicInteger()
        val loop = RayFeasibilityLoop { probes.incrementAndGet(); RayCapability.Unavailable(RayUnavailableReason.RUNTIME_NOT_FOUND) }
        loop.close()
        assertEquals(0, probes.get())
    }

    private fun request(revision: Long) = RayRequest(
        RayFrameKey(1, 1, revision, 1), 1, 1,
        RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f),
        listOf(RaySliceMesh(floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 0f, 1f, 0f), intArrayOf(0, 1, 2))),
        listOf(RaySliceInstance(0, listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), listOf(1f, 0f, 0f)))
    )
}
