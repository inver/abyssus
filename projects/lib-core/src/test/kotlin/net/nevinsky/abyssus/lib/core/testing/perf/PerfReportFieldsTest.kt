/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The optional fields of design decision D4 (spans, GPU passes, counters, hitches), and old reports beside them. */
class PerfReportFieldsTest {
    /** The committed game baseline, written before these fields existed (the test runs in `projects/lib-core`). */
    private val oldBaseline = File("../../docs/perf/baseline/game.json")

    private fun span(p95: Double) = PerfSpan(p95 / 2, p95, p95 * 1.5, 4096.0)

    private fun withFields(old: PerfReport): PerfReport {
        val w = old.workloads.single()
        val stats = checkNotNull(w.stats).copy(spans = mapOf("frame" to span(12.0), "flight" to span(0.6), "field" to span(8.0), "ui" to span(1.0)))
        return old.copy(workloads = listOf(w.copy(
            stats = stats,
            gpu = PerfGpu(mapOf("sky" to span(10.0), "shadow" to span(1.0), "total" to span(14.0))),
            counters = mapOf("main" to PerfCounters(300.0, 150.0, 110.0, 1.5e6, "counters"), "shadow" to PerfCounters(423.0, 4.0, 86.0, 2.0e6, "counters")),
            hitches = mapOf("flightStart" to PerfHitch(1400.0, 1460.0, listOf(1390.0, 1400.0, 1410.0, 1450.0, 1460.0))),
            agents = emptyList(),
        )))
    }

    @Test fun theCommittedBaselineStillReads() {
        val old = PerfReportFiles().read(oldBaseline)
        val w = old.workloads.single()
        assertEquals(PerfStatus.MEASURED, w.status)
        assertTrue("old reports hold no spans", w.stats!!.spans.isEmpty())
        assertNull(w.gpu)
        assertTrue(w.counters.isEmpty() && w.hitches.isEmpty() && w.agents.isEmpty())
        assertNull(w.cpuNotComparable)
    }

    @Test fun theNewFieldsRoundTrip() {
        val report = withFields(PerfReportFiles().read(oldBaseline))
        val file = File(Files.createTempDirectory("perf").toFile(), "new.json")
        try {
            PerfReportFiles().write(report, file)
            assertEquals(report, PerfReportFiles().read(file))
            val text = file.readText()
            for (key in listOf("spans", "gpu", "passes", "counters", "drawCalls", "shaderSwitches", "textureBindings", "vertices", "hitches", "medianMs", "maxMs")) {
                assertTrue("missing $key", text.contains("\"$key\""))
            }
        } finally {
            file.parentFile.deleteRecursively()
        }
    }

    @Test fun anOldBaselineComparesWithANewReportOnTheFieldsBothHold() {
        val old = PerfReportFiles().read(oldBaseline)
        val finding = PerfComparison().compare(old, withFields(old)).single()
        // the same frame times: the new spans and GPU passes have no counterpart in the old report, so nothing else counts
        assertNotEquals(PerfVerdict.REGRESSION, finding.verdict)
        assertFalse(finding.detail, finding.detail.contains("span"))
    }

    @Test fun spansAndGpuPassesBothSidesHoldAreCompared() {
        val base = withFields(PerfReportFiles().read(oldBaseline))
        val w = base.workloads.single()
        val flat = base.copy(workloads = listOf(w.copy(stats = w.stats!!.copy(samplesP95Ms = listOf(w.stats.p95Ms, w.stats.p95Ms)))))
        val slowerUi = flat.copy(workloads = listOf(flat.workloads.single().let { x ->
            x.copy(stats = x.stats!!.copy(spans = x.stats.spans + ("ui" to span(2.0))))
        }))
        val finding = PerfComparison().compare(flat, slowerUi).single()
        assertEquals(finding.detail, PerfVerdict.REGRESSION, finding.verdict)
        assertTrue(finding.detail, finding.detail.contains("span ui p95 +100.0%"))

        val slowerSky = flat.copy(workloads = listOf(flat.workloads.single().let { x -> x.copy(gpu = PerfGpu(x.gpu!!.passes + ("sky" to span(20.0)))) }))
        assertEquals(PerfVerdict.REGRESSION, PerfComparison().compare(flat, slowerSky).single().verdict)
    }

    @Test fun agentsOrAThrottledScreenMismatch() {
        val base = withFields(PerfReportFiles().read(oldBaseline))
        val w = base.workloads.single()
        val kover = base.copy(workloads = listOf(w.copy(agents = listOf("kover"))))
        assertEquals(PerfVerdict.MISMATCHED, PerfComparison().compare(base, kover).single().verdict)
        val locked = base.copy(workloads = listOf(w.copy(cpuNotComparable = "the screen was locked")))
        val finding = PerfComparison().compare(base, locked).single()
        assertEquals(PerfVerdict.MISMATCHED, finding.verdict)
        assertTrue(finding.detail.contains("locked"))
    }

    @Test fun gpuTimesAreMeasuredOrUnavailableWithAReason() {
        assertThrows(IllegalArgumentException::class.java) { PerfGpu() }
        assertThrows(IllegalArgumentException::class.java) { PerfGpu(mapOf("sky" to span(1.0)), "no queries") }
        assertEquals("no timer queries", PerfGpu(unavailable = "no timer queries").unavailable)
    }
}
