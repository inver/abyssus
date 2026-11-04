/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.profiling.GL30Interceptor
import com.badlogic.gdx.graphics.profiling.GLProfiler
import net.nevinsky.abyssus.lib.core.testing.perf.FrameSampler
import net.nevinsky.abyssus.lib.core.testing.perf.PerfCounters
import net.nevinsky.abyssus.lib.core.testing.perf.PerfGpu
import net.nevinsky.abyssus.lib.core.testing.perf.PerfSpan
import org.lwjgl.opengl.GL15C
import org.lwjgl.opengl.GL33C
import java.lang.management.ManagementFactory

/**
 * The probes of the performance workload (design decision D4), in the game's test sources only: the shipped game keeps
 * [FrameProbe.NONE]. Each has [frameEnd], called after a whole frame; [recording] turns a probe's sampling on and off.
 */
abstract class WorkloadProbe : FrameProbe {
    /** Whether begin and end are counted; off between samples. */
    var recording = false

    open fun frameEnd() {}
    open fun reset() {}
}

/** Forwards to [probes] in order on begin and in reverse on end, so nested measurements stay nested. */
class Probes(private val probes: List<WorkloadProbe>) : FrameProbe {
    override fun begin(span: FrameSpan) = probes.forEach { it.begin(span) }
    override fun end(span: FrameSpan) {
        for (i in probes.indices.reversed()) probes[i].end(span)
    }

    fun frameEnd() = probes.forEach(WorkloadProbe::frameEnd)
}

/** A growable list of longs, so recording a frame allocates nothing in steady state. */
class LongSamples(capacity: Int = 4096) {
    private var values = LongArray(capacity)
    var size = 0
        private set

    fun add(v: Long) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = v
    }

    fun clear() {
        size = 0
    }

    fun sorted(): LongArray = values.copyOf(size).also { it.sort() }
    fun sum(): Long {
        var s = 0L
        for (i in 0 until size) s += values[i]
        return s
    }
}

/** p50, p95 and p99 of [nanos] in milliseconds, and [bytes] per frame when given. */
fun spanOf(nanos: LongSamples, bytes: Double? = null): PerfSpan? {
    if (nanos.size == 0) return null
    val sorted = nanos.sorted()
    return PerfSpan(
        FrameSampler.percentile(sorted, 0.50), FrameSampler.percentile(sorted, 0.95), FrameSampler.percentile(sorted, 0.99), bytes,
    )
}

/** CPU time and allocation of every [FrameSpan] on the calling (main) thread, one value per frame and span. */
class SpanRecorder : WorkloadProbe() {
    private val threads = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    private val count = FrameSpan.entries.size
    private val startedAt = LongArray(count)
    private val allocatedAt = LongArray(count)
    private val frameNanos = LongArray(count)
    private val frameBytes = LongArray(count)
    private val seen = BooleanArray(count)
    private val nanos = Array(count) { LongSamples() }
    private val bytes = LongArray(count)
    private var frames = 0

    private fun allocated(): Long = threads?.currentThreadAllocatedBytes ?: 0L

    override fun begin(span: FrameSpan) {
        if (!recording) return
        startedAt[span.ordinal] = System.nanoTime()
        allocatedAt[span.ordinal] = allocated()
    }

    override fun end(span: FrameSpan) {
        if (!recording) return
        val i = span.ordinal
        frameNanos[i] += System.nanoTime() - startedAt[i]
        frameBytes[i] += allocated() - allocatedAt[i]
        seen[i] = true
    }

    override fun frameEnd() {
        if (!recording) return
        frames++
        for (i in 0 until count) if (seen[i]) {
            nanos[i].add(frameNanos[i])
            bytes[i] += frameBytes[i]
            frameNanos[i] = 0
            frameBytes[i] = 0
            seen[i] = false
        }
    }

    override fun reset() {
        nanos.forEach(LongSamples::clear)
        bytes.fill(0)
        frames = 0
    }

    /** The spans measured since [reset]; a pass that never ran is left out. */
    fun spans(): Map<String, PerfSpan> = FrameSpan.entries.mapNotNull { span ->
        spanOf(nanos[span.ordinal], bytes[span.ordinal].toDouble() / frames.coerceAtLeast(1))?.let { span.key to it }
    }.toMap()
}

/**
 * GPU time of [timed] spans from `GL_TIME_ELAPSED` queries: a ring of [RING] queries per span, read back only once
 * `GL_QUERY_RESULT_AVAILABLE` says so, never blocking. The spans must not nest (the passes and the UI follow each other);
 * `total` is the sum of one frame's timed spans. On a context without timer queries, or when no result arrives within
 * [PATIENCE] frames, [unavailable] says why and no time is reported.
 */
class GpuPassTimer(private val timed: Set<FrameSpan> = FrameSpan.entries.filter { it.pass }.toSet() + FrameSpan.UI) : WorkloadProbe() {
    private val count = FrameSpan.entries.size
    private val ids = IntArray(count * RING)
    private val frameOf = LongArray(count * RING)
    private val pending = BooleanArray(count * RING)
    private val next = IntArray(count)
    private val active = IntArray(count) { -1 }
    private val nanos = Array(count) { LongSamples() }
    private val totals = LongSamples()
    // per frame in flight (a query's result comes back a few frames late): [frame, sum, issued, read], no allocation
    private val frameSum = Array(FRAMES_IN_FLIGHT) { LongArray(4).also { it[0] = -1 } }

    private fun sumOf(frame: Long): LongArray {
        val entry = frameSum[(frame % FRAMES_IN_FLIGHT).toInt()]
        if (entry[0] != frame) {
            entry[0] = frame; entry[1] = 0; entry[2] = 0; entry[3] = 0
        }
        return entry
    }
    private var frame = 0L
    private var framesWithoutResult = 0
    private var anyResult = false

    var unavailable: String? = null
        private set

    /** Makes the queries; call with the context current, before the first frame. */
    fun start() {
        val version = Gdx.gl.glGetString(GL20.GL_VERSION) ?: ""
        val major = Regex("""(\d+)\.(\d+)""").find(version)?.groupValues?.let { it[1].toInt() * 10 + it[2].toInt() } ?: 0
        if (major < 33) {
            unavailable = "GL_TIME_ELAPSED needs OpenGL 3.3; the context is '$version'"
            return
        }
        while (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) Unit
        GL15C.glGenQueries(ids)
        val error = Gdx.gl.glGetError()
        if (error != GL20.GL_NO_ERROR || ids.any { it == 0 }) unavailable = "glGenQueries failed (GL error $error)"
    }

    override fun begin(span: FrameSpan) {
        if (!recording || unavailable != null || span !in timed) return
        val slot = span.ordinal * RING + next[span.ordinal]
        // the ring is full of results not yet back: this frame's time of the span is not taken
        if (pending[slot] && !read(slot)) return
        GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, ids[slot])
        active[span.ordinal] = slot
    }

    override fun end(span: FrameSpan) {
        val slot = active[span.ordinal]
        if (slot < 0) return
        GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED)
        active[span.ordinal] = -1
        pending[slot] = true
        frameOf[slot] = frame
        sumOf(frame)[2]++
        next[span.ordinal] = (next[span.ordinal] + 1) % RING
    }

    /** Reads [slot] when its result is there; false when it is not yet. */
    private fun read(slot: Int): Boolean {
        if (GL15C.glGetQueryObjecti(ids[slot], GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) return false
        val elapsed = GL33C.glGetQueryObjecti64(ids[slot], GL15C.GL_QUERY_RESULT)
        pending[slot] = false
        anyResult = true
        nanos[slot / RING].add(elapsed)
        val sum = frameSum[(frameOf[slot] % FRAMES_IN_FLIGHT).toInt()]
        // a frame's slot is reused only after FRAMES_IN_FLIGHT frames: older results than that are not summed
        if (sum[0] == frameOf[slot]) {
            sum[1] += elapsed
            if (++sum[3] == sum[2]) totals.add(sum[1])
        }
        return true
    }

    override fun frameEnd() {
        if (!recording || unavailable != null) return
        for (slot in pending.indices) if (pending[slot]) read(slot)
        frame++
        if (!anyResult && ++framesWithoutResult >= PATIENCE) {
            unavailable = "no GL_TIME_ELAPSED result became available within $PATIENCE frames"
        }
    }

    override fun reset() {
        nanos.forEach(LongSamples::clear)
        totals.clear()
        frameSum.forEach { it.fill(-1) }
    }

    /** The passes' GPU times and their per-frame `total`, or why they are unavailable. */
    fun result(): PerfGpu {
        unavailable?.let { return PerfGpu(unavailable = it) }
        val passes = FrameSpan.entries.filter { it in timed }.mapNotNull { span -> spanOf(nanos[span.ordinal])?.let { span.key to it } }.toMap()
        val total = spanOf(totals)
        if (passes.isEmpty()) return PerfGpu(unavailable = "no pass was timed")
        return PerfGpu(if (total == null) passes else passes + ("total" to total))
    }

    fun dispose() {
        if (ids.any { it != 0 }) GL15C.glDeleteQueries(ids)
    }

    private companion object {
        const val RING = 3
        const val PATIENCE = 10
        const val FRAMES_IN_FLIGHT = 8
    }
}

/**
 * libGDX's `GLProfiler` counters (draw calls, shader switches, texture bindings, vertices) per frame, for the main view
 * (sky, terrain, models, lines), the shadow pass and the UI. [install] puts a counting GL in `Gdx.gl*`: it checks for
 * GL errors after every call, so a run with it installed is never the one whose CPU times are reported.
 */
class CounterProbe : WorkloadProbe() {
    private class Totals {
        var drawCalls = 0L
        var shaderSwitches = 0L
        var textureBindings = 0L
        var vertices = 0.0
    }

    private var interceptor: GL30Interceptor? = null
    private var original: GL30? = null
    private val at = IntArray(3)
    private var vertexAt = 0f
    private val totals = mapOf("main" to Totals(), "shadow" to Totals(), "ui" to Totals())
    private var frames = 0

    private fun group(span: FrameSpan): Totals? = when (span) {
        FrameSpan.SHADOW -> totals["shadow"]
        FrameSpan.UI -> totals["ui"]
        FrameSpan.SKY, FrameSpan.TERRAIN, FrameSpan.MODELS, FrameSpan.LINES -> totals["main"]
        else -> null
    }

    fun install() {
        val gl = Gdx.gl30
        original = gl
        val counting = object : GL30Interceptor(GLProfiler(Gdx.graphics), gl) {}
        interceptor = counting
        Gdx.gl = counting
        Gdx.gl20 = counting
        Gdx.gl30 = counting
    }

    fun uninstall() {
        original?.let { Gdx.gl = it; Gdx.gl20 = it; Gdx.gl30 = it }
        interceptor = null
    }

    override fun begin(span: FrameSpan) {
        val gl = interceptor ?: return
        if (!recording || group(span) == null) return
        at[0] = gl.drawCalls; at[1] = gl.shaderSwitches; at[2] = gl.textureBindings
        vertexAt = gl.vertexCount.total
    }

    override fun end(span: FrameSpan) {
        val gl = interceptor ?: return
        val t = group(span) ?: return
        if (!recording) return
        t.drawCalls += gl.drawCalls - at[0]
        t.shaderSwitches += gl.shaderSwitches - at[1]
        t.textureBindings += gl.textureBindings - at[2]
        t.vertices += (gl.vertexCount.total - vertexAt).toDouble()
    }

    override fun frameEnd() {
        if (recording && interceptor != null) frames++
    }

    /** The counters per frame of each group, named [run] after the run they came from. */
    fun result(run: String): Map<String, PerfCounters> {
        val n = frames.coerceAtLeast(1).toDouble()
        return totals.mapValues { (_, t) -> PerfCounters(t.drawCalls / n, t.shaderSwitches / n, t.textureBindings / n, t.vertices / n, run) }
    }
}
