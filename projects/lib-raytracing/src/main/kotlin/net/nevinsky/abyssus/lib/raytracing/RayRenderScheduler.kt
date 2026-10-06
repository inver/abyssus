/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.util.concurrent.atomic.AtomicReference

/** Output framebuffer size and active camera identity, independent of adaptive internal dimensions. */
data class RayDisplayKey(val width: Int, val height: Int, val cameraId: String) {
    init { require(width > 0 && height > 0) }
}

/** Immutable scene/slice requests share scheduling metadata, while retaining their concrete native submit API. */
interface RayRenderRequest {
    val key: RayFrameKey
    val width: Int
    val height: Int
    val camera: RaySliceCamera
    fun withRenderPlan(width: Int, height: Int, samples: Int, sampleOffset: Int, accumulationEpoch: Long): RayRenderRequest
}

/** Saved limits carried by the mailbox; adaptive policy and native work stay on the serial worker. */
data class RayRenderSettings(val targetSamples: Int = 256, val maxRaysPerFrame: Long = 2097152) {
    init { require(targetSamples in 1..4096 && maxRaysPerFrame in 1..67108864) }
}

/** Content revision covers transforms/lights/materials/environment/geometry beyond camera/pose keys.
 * The scene adapter supplies a conservative primary+shadow+reflection ray cost per sample.
 */
data class RayRenderInput(
    val request: RayRenderRequest, val display: RayDisplayKey, val contentRevision: Long,
    val raysPerSample: Int = 1,
    /** Caller-owned immutable presentation metadata, never inspected by native work or modified by this mailbox. */
    val metadata: Any? = null,
    val settings: RayRenderSettings? = null, val settingsRevision: Long = 0,
) {
    init { require(raysPerSample > 0) }
}
data class RayAccumulation(val epoch: Long, val sampleOffset: Int, val sampleCount: Int)

/** Worker adapter applies the sample/history plan; camera/geometry metadata travels with completed frames. */
data class RayRenderBatch(val input: RayRenderInput, val request: RayRenderRequest, val quality: RayRenderQuality, val accumulation: RayAccumulation)
data class RayRenderCompleted(val frame: RayFrame, val batch: RayRenderBatch, val offeredNanos: Long)

/**
 * CPU mailbox and serial-worker pump. Offers/latest/cancel may run on EDT, and invoke no native callbacks.
 * The caller owns worker scheduling, backend failures and eventual session disposal (lifecycle integration).
 */
class RayRenderScheduler(
    private val submit: (RayRenderBatch) -> Unit,
    private val poll: () -> RayFrame?,
    private val policy: RayQualityPolicy,
    private val clock: () -> Long,
    private val maxMotionAgeNanos: Long = 100_000_000,
    private val log: Logger = NOPLogger.NOP_LOGGER,
) {
    private data class History(val key: RayFrameKey, val display: RayDisplayKey, val content: Long, val raysPerSample: Int, val settings: RayRenderSettings?, val settingsRevision: Long)
    private data class Offered(val input: RayRenderInput, val nanos: Long)
    private data class Flight(val batch: RayRenderBatch, val offeredNanos: Long, val submittedNanos: Long, val lifetime: Long)
    private val lock = Any()
    private var desired: RayRenderInput? = null
    private var pending: Offered? = null
    private var completed: RayRenderCompleted? = null
    private var history: History? = null
    private var epoch = 0L
    private var lifetime = 0L
    private var samples = 0
    private var stableFrames = 0
    private var dimensions: Pair<Int, Int>? = null
    // These two fields belong exclusively to the caller's serial worker.
    private val worker = AtomicReference<Thread?>()
    private var inFlight: Flight? = null
    init { require(maxMotionAgeNanos > 0) }

    fun offer(input: RayRenderInput) = synchronized(lock) {
        if (history(input) != history) {
            history = history(input)
            epoch++
            samples = 0
            stableFrames = 0
            dimensions = null
        }
        desired = input
        pending = Offered(input, clock())
        if (completed?.let { !compatible(it.batch.input, input) } == true) completed = null
    }

    fun latest(): RayRenderCompleted? = synchronized(lock) {
        val current = desired ?: return@synchronized null
        completed?.takeIf { compatible(it.batch.input, current) && timely(it, current) }
    }

    /** Drops requested/publication state immediately; the worker still drains any already selected native work. */
    fun cancel() = synchronized(lock) {
        lifetime++
        epoch++
        desired = null
        pending = null
        completed = null
        history = null
        dimensions = null
        samples = 0
        stableFrames = 0
    }

    fun pump() {
        val thread = Thread.currentThread()
        worker.compareAndSet(null, thread)
        check(worker.get() === thread) { "Ray scheduler pump must stay on its serial worker" }
        inFlight?.let { flight ->
            val frame = poll() ?: return
            check(frame.key == flight.batch.request.key && frame.width == flight.batch.request.width && frame.height == flight.batch.request.height) {
                "Backend completion does not match the submitted ray batch"
            }
            inFlight = null
            val now = clock()
            policy.observe((now - flight.submittedNanos).coerceAtLeast(0))
            synchronized(lock) {
                val current = desired
                if (flight.lifetime == lifetime && current != null && compatible(flight.batch.input, current)) {
                    val value = RayRenderCompleted(frame, flight.batch, flight.offeredNanos)
                    if (timely(value, current)) completed = value
                    if (history(flight.batch.input) == history && flight.batch.accumulation.epoch == epoch) {
                        samples += flight.batch.accumulation.sampleCount
                        stableFrames = minOf(stableFrames + 1, 64)
                    }
                }
            }
        }
        val next = synchronized(lock) {
            val offered = pending ?: return@synchronized null
            pending = null
            val quality = policy.choose(offered.input.display.width, offered.input.display.height, stableFrames, offered.input.raysPerSample, offered.input.settings?.maxRaysPerFrame ?: policy.limits.maxRaysPerFrame)
            val size = quality.width to quality.height
            if (dimensions != size) log.atDebug().log { "Ray frame ${offered.input.display.width}x${offered.input.display.height} renders at ${size.first}x${size.second}, ${quality.samples} samples per batch, ${offered.input.raysPerSample} rays per sample" }
            if (dimensions != null && dimensions != size) {
                epoch++
                samples = 0
            }
            dimensions = size
            val count = minOf(quality.samples, (offered.input.settings?.targetSamples ?: policy.limits.maxAccumulatedSamples) - samples)
            if (count <= 0) return@synchronized null
            val original = offered.input.request
            val request = original.withRenderPlan(quality.width, quality.height, count, samples, epoch)
            val batch = RayRenderBatch(offered.input, request, quality.copy(samples = count), RayAccumulation(epoch, samples, count))
            Flight(batch, offered.nanos, clock(), lifetime)
        } ?: return
        inFlight = next
        try { submit(next.batch) } catch (failure: Throwable) { inFlight = null; throw failure }
    }

    private fun history(input: RayRenderInput) = History(input.request.key, input.display, input.contentRevision, input.raysPerSample, input.settings, input.settingsRevision)
    private fun compatible(rendered: RayRenderInput, desired: RayRenderInput) = rendered.display == desired.display &&
        rendered.request.key.sceneGeneration == desired.request.key.sceneGeneration &&
        rendered.request.key.contextGeneration == desired.request.key.contextGeneration &&
        rendered.settingsRevision == desired.settingsRevision && rendered.settings == desired.settings
    private fun timely(completed: RayRenderCompleted, desired: RayRenderInput) =
        history(completed.batch.input) == history(desired) || clock() - completed.offeredNanos <= maxMotionAgeNanos
}
