/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class PerfComparisonTest {
    private val env = PerfEnvironment("abc", false, "macOS", "Zulu 21", "Apple M1 Pro", "4.1", "OpenGL")

    private fun workload(p95: Double, alloc: Double = 1000.0, samples: List<Double> = listOf(p95, p95 * 1.01), hash: String = "h") =
        PerfWorkload(
            "w", PerfStatus.MEASURED, sceneHash = hash, settings = mapOf("size" to "1280x720"),
            stats = PerfStats(1000, p95 / 2, p95, p95 * 1.5, samples, alloc, 0, 0),
        )

    private fun report(w: PerfWorkload, e: PerfEnvironment = env, host: String = "editor") = PerfReport(host = host, environment = e, workloads = listOf(w))

    private fun verdict(base: PerfReport, next: PerfReport) = PerfComparison().compare(base, next).single().verdict

    @Test
    fun withinTheThresholdPasses() = assertEquals(PerfVerdict.PASS, verdict(report(workload(10.0)), report(workload(10.9, alloc = 1050.0))))

    @Test
    fun aSlowerP95BeyondTheThresholdRegresses() = assertEquals(PerfVerdict.REGRESSION, verdict(report(workload(10.0)), report(workload(11.5))))

    @Test
    fun moreAllocationBeyondTheThresholdRegresses() =
        assertEquals(PerfVerdict.REGRESSION, verdict(report(workload(10.0)), report(workload(10.0, alloc = 1200.0))))

    @Test
    fun aNoisyBaselineIsInconclusiveEvenWhenTheCandidateLooksFaster() =
        assertEquals(PerfVerdict.INCONCLUSIVE, verdict(report(workload(10.0, samples = listOf(10.0, 12.0, 10.5))), report(workload(5.0))))

    @Test
    fun anotherGpuOrSceneOrHostIsMismatched() {
        val base = report(workload(10.0))
        assertEquals(PerfVerdict.MISMATCHED, verdict(base, report(workload(10.0), env.copy(gpu = "other"))))
        assertEquals(PerfVerdict.MISMATCHED, verdict(base, report(workload(10.0, hash = "changed"))))
        assertEquals(PerfVerdict.MISMATCHED, verdict(base, report(workload(10.0), host = "game")))
    }

    @Test
    fun aDifferentCommitOrDirtyStateStillCompares() =
        assertEquals(PerfVerdict.PASS, verdict(report(workload(10.0)), report(workload(10.0), env.copy(commit = "def", dirty = true))))

    @Test
    fun aSkippedWorkloadIsNeverAPass() {
        val skipped = PerfWorkload("w", PerfStatus.SKIPPED, reason = "opt-in")
        assertEquals(PerfVerdict.NOT_MEASURED, verdict(report(workload(10.0)), report(skipped)))
        assertEquals(PerfVerdict.NOT_MEASURED, verdict(report(skipped), report(workload(10.0))))
    }
}
