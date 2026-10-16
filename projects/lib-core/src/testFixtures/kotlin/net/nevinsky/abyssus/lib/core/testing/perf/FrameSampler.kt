/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import java.lang.management.ManagementFactory

/**
 * Collects CPU frame times and render-thread allocation of one thread over a sample. [record] is called once per frame
 * on the owning (render) thread, with the frame's duration; aggregation happens in [stats], outside the hot path.
 */
class FrameSampler(expectedFrames: Int = 4096) {
    private var count = 0
    private val thread = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    private val gcs = ManagementFactory.getGarbageCollectorMXBeans()
    private var allocationAtStart = 0L
    private var gcCountAtStart = 0L
    private var gcMillisAtStart = 0L
    private var growing = LongArray(expectedFrames.coerceAtLeast(16))

    /** Starts a sample on the calling thread. */
    fun start() {
        count = 0
        allocationAtStart = allocated()
        gcCountAtStart = gcs.sumOf { it.collectionCount.coerceAtLeast(0) }
        gcMillisAtStart = gcs.sumOf { it.collectionTime.coerceAtLeast(0) }
    }

    fun record(nanos: Long) {
        if (count == growing.size) growing = growing.copyOf(count * 2)
        growing[count++] = nanos
    }

    /** The figures since [start]. Call on the same thread, after the last [record]. */
    fun stats(): PerfStats {
        require(count > 0) { "no frame was recorded" }
        val sorted = growing.copyOf(count).also { it.sort() }
        val allocated = allocated() - allocationAtStart
        return PerfStats(
            frames = count,
            p50Ms = percentile(sorted, 0.50), p95Ms = percentile(sorted, 0.95), p99Ms = percentile(sorted, 0.99),
            allocatedBytesPerFrame = if (thread == null) 0.0 else allocated.toDouble() / count,
            gcCount = gcs.sumOf { it.collectionCount.coerceAtLeast(0) } - gcCountAtStart,
            gcMillis = gcs.sumOf { it.collectionTime.coerceAtLeast(0) } - gcMillisAtStart,
        )
    }

    private fun allocated(): Long = thread?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0L

    companion object {
        /** The nearest-rank percentile of [sortedNanos], in milliseconds. */
        fun percentile(sortedNanos: LongArray, fraction: Double): Double {
            require(sortedNanos.isNotEmpty())
            val rank = Math.ceil(fraction * sortedNanos.size).toInt().coerceIn(1, sortedNanos.size)
            return sortedNanos[rank - 1] / 1_000_000.0
        }

        /** Folds several samples into one: pooled percentiles, the per-sample p95s, summed GC, mean allocation. */
        fun combine(samples: List<PerfStats>): PerfStats {
            require(samples.isNotEmpty())
            val frames = samples.sumOf { it.frames }
            fun weighted(f: (PerfStats) -> Double) = samples.sumOf { f(it) * it.frames } / frames
            // percentiles of the pooled run are approximated by the frame-weighted mean of each sample's
            return PerfStats(
                frames, weighted { it.p50Ms }, weighted { it.p95Ms }, weighted { it.p99Ms },
                samples.map { it.p95Ms }, weighted { it.allocatedBytesPerFrame },
                samples.sumOf { it.gcCount }, samples.sumOf { it.gcMillis },
            )
        }
    }
}
