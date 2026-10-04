/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.raytracing.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Temporary feasibility driver: one native submission and one replaceable CPU request, with no EDT waits. */
internal class RayFeasibilityLoop(private val probe: () -> RayCapability) : AutoCloseable {
    class Completed(val frame: RayFrame, val offeredNanos: Long)
    private class Offered(val request: RayRequest, val nanos: Long)
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "abyssus-metal-feasibility").apply { isDaemon = true }
    }
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val pending = AtomicReference<Offered?>()
    private val desired = AtomicReference<RayRequest?>()
    private val completed = AtomicReference<Completed?>()
    private var backend: RayBackend? = null
    private var session: RaySession? = null
    private var inFlight: Offered? = null
    @Volatile var failure: Throwable? = null
        private set
    @Volatile var info: RayBackendInfo? = null
        private set

    fun offer(request: RayRequest) {
        if (closed.get()) return
        desired.set(request)
        pending.set(Offered(request, System.nanoTime()))
        if (started.compareAndSet(false, true)) worker.scheduleWithFixedDelay(::tick, 0, 1, TimeUnit.MILLISECONDS)
    }

    fun latest(): Completed? {
        if (closed.get()) return null
        val value = completed.get() ?: return null
        val wanted = desired.get() ?: return null
        return value.takeIf { compatible(it.frame, wanted) }
    }

    private fun compatible(frame: RayFrame, request: RayRequest) =
        frame.key.sceneGeneration == request.key.sceneGeneration &&
            frame.key.contextGeneration == request.key.contextGeneration &&
            frame.width == request.width && frame.height == request.height

    private fun tick() {
        if (!closed.get()) runCatchingKeepingCancellation { pump() }.onFailure {
            failure = it
            closed.set(true)
        }
        if (closed.get()) {
            pending.set(null)
            completed.set(null)
            runCatchingKeepingCancellation { session?.dispose() }.onFailure { failure = failure ?: it }
            runCatchingKeepingCancellation { backend?.dispose() }.onFailure { failure = failure ?: it }
            session = null
            backend = null
            inFlight = null
            worker.shutdown()
        }
    }

    private fun pump() {
        if (session == null) {
            val result = probe()
            check(result is RayCapability.Available) { "Metal feasibility probe: $result" }
            backend = result.backend
            info = result.backend.info
            session = result.backend.openSession("feasibility", RayLimits())
        }
        val active = checkNotNull(session)
        inFlight?.let { offered ->
            val frame = active.poll() ?: return
            inFlight = null
            if (!closed.get() && desired.get()?.let { compatible(frame, it) } == true) {
                completed.set(Completed(frame, offered.nanos))
            }
        }
        if (closed.get()) return
        val next = pending.getAndSet(null) ?: return
        active.submit(next.request)
        inFlight = next
    }

    /** Owner-worker disposal may finish later; neither hiding nor closing a view waits for native work. */
    override fun close() {
        closed.set(true)
        pending.set(null)
        completed.set(null)
        if (!started.get()) worker.shutdown()
    }
}
