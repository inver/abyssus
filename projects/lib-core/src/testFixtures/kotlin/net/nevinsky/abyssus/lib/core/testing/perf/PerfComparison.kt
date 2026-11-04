/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

/** The outcome for one workload of comparing a candidate run with a baseline. */
enum class PerfVerdict {
    PASS,
    REGRESSION,

    /** The baseline's own samples differ by more than the threshold, so no change can be told from noise. */
    INCONCLUSIVE,

    /** Another machine, driver, JDK, scene or setting: the two numbers are not comparable. */
    MISMATCHED,

    /** One side was skipped or unavailable, or the workload is missing: nothing was measured to compare. */
    NOT_MEASURED,
}

data class PerfFinding(val workload: String, val verdict: PerfVerdict, val detail: String)

/**
 * Compares a candidate with its baseline on matched environments only. The p95 frame time and the steady-state
 * allocation per frame may grow by [threshold] (a provisional 10%); beyond that is a regression. If the baseline's
 * own sample p95s spread by more than [threshold] the answer is inconclusive, never a pass. The optional fields are
 * compared only where both reports hold them: the p95 of each span and of each GPU pass. A run with other agents
 * attached, or whose CPU times are marked not comparable, is mismatched.
 */
class PerfComparison(private val threshold: Double = 0.10) {
    init {
        require(threshold > 0.0)
    }

    fun compare(baseline: PerfReport, candidate: PerfReport): List<PerfFinding> {
        if (baseline.host != candidate.host) {
            return baseline.workloads.map { PerfFinding(it.name, PerfVerdict.MISMATCHED, "host ${baseline.host} vs ${candidate.host}") }
        }
        val machine = baseline.environment.sameMachineAs(candidate.environment)
        return baseline.workloads.map { base ->
            val next = candidate.workload(base.name)
            when {
                !machine -> PerfFinding(base.name, PerfVerdict.MISMATCHED, "different environment")
                next == null -> PerfFinding(base.name, PerfVerdict.NOT_MEASURED, "missing from the candidate")
                base.status != PerfStatus.MEASURED || next.status != PerfStatus.MEASURED ->
                    PerfFinding(base.name, PerfVerdict.NOT_MEASURED, "baseline ${base.status}, candidate ${next.status}")
                base.sceneHash != next.sceneHash -> PerfFinding(base.name, PerfVerdict.MISMATCHED, "scene hash differs")
                base.settings != next.settings -> PerfFinding(base.name, PerfVerdict.MISMATCHED, "settings differ")
                base.agents != next.agents -> PerfFinding(base.name, PerfVerdict.MISMATCHED, "agents ${base.agents} vs ${next.agents}")
                (base.cpuNotComparable ?: next.cpuNotComparable) != null ->
                    PerfFinding(base.name, PerfVerdict.MISMATCHED, "CPU times not comparable: ${base.cpuNotComparable ?: next.cpuNotComparable}")
                else -> compareStats(base.name, base.stats!!, next.stats!!, base.gpu?.passes.orEmpty(), next.gpu?.passes.orEmpty())
            }
        }
    }

    private fun compareStats(
        name: String, base: PerfStats, next: PerfStats, baseGpu: Map<String, PerfSpan>, nextGpu: Map<String, PerfSpan>,
    ): PerfFinding {
        val spread = spread(base.samplesP95Ms)
        if (spread > threshold) {
            return PerfFinding(name, PerfVerdict.INCONCLUSIVE, "baseline p95 samples spread %.1f%%".format(java.util.Locale.ROOT, spread * 100))
        }
        val frame = growth(base.p95Ms, next.p95Ms)
        val alloc = growth(base.allocatedBytesPerFrame, next.allocatedBytesPerFrame)
        // the optional fields count only where both sides hold them
        val parts = base.spans.keys.intersect(next.spans.keys).sorted().map { "span $it" to growth(base.spans.getValue(it).p95Ms, next.spans.getValue(it).p95Ms) } +
            baseGpu.keys.intersect(nextGpu.keys).sorted().map { "gpu $it" to growth(baseGpu.getValue(it).p95Ms, nextGpu.getValue(it).p95Ms) }
        val detail = ("p95 %+.1f%%, allocation %+.1f%%".format(java.util.Locale.ROOT, frame * 100, alloc * 100) +
            parts.joinToString("") { (part, g) -> ", %s p95 %+.1f%%".format(java.util.Locale.ROOT, part, g * 100) })
        return if (frame > threshold || alloc > threshold || parts.any { it.second > threshold }) PerfFinding(name, PerfVerdict.REGRESSION, detail)
        else PerfFinding(name, PerfVerdict.PASS, detail)
    }

    /** (max - min) / min of [samples]; 0 for fewer than two. */
    private fun spread(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        val low = samples.min()
        return if (low <= 0.0) Double.POSITIVE_INFINITY else (samples.max() - low) / low
    }

    private fun growth(before: Double, after: Double): Double = when {
        before > 0.0 -> (after - before) / before
        after > 0.0 -> Double.POSITIVE_INFINITY
        else -> 0.0
    }
}
