/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.perf

import net.nevinsky.abyssus.lib.core.testing.perf.PerfEnvironment
import net.nevinsky.abyssus.lib.core.testing.perf.PerfReport
import net.nevinsky.abyssus.lib.core.testing.perf.PerfReportFiles
import net.nevinsky.abyssus.lib.core.testing.perf.PerfStatus
import net.nevinsky.abyssus.lib.core.testing.perf.PerfWorkload
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The editor's graphics workloads (docs/ai/testing.md, "Graphics performance evidence"). Opt in with
 * `-Dabyssus.glTests=true -Dabyssus.perf.report=<file>`; `-Dabyssus.perf.workloads=untitled,stress` picks some, and the
 * `abyssus.perf.warmupSeconds` / `sampleSeconds` / `samples` properties shorten a smoke run (a report with other
 * settings is never compared with the committed baseline). Without a display the report is still written, with every
 * workload marked skipped.
 */
class PerfWorkloadsGlTest {
    private val report get() = System.getProperty("abyssus.perf.report")?.let(::File)

    private fun selected() = System.getProperty("abyssus.perf.workloads")?.split(',')?.map(String::trim)?.toSet()
        .let { names -> EditorPerfRunner.workloads().filter { names == null || it.name in names } }

    @Test
    fun measureTheEditorWorkloads() {
        val file = report
        assumeTrue("set -Dabyssus.perf.report=<file> to measure", file != null)
        val workloads = selected()
        if (!GlHarness.enabled) {
            PerfReportFiles().write(
                PerfReport(
                    host = "editor", environment = PerfEnvironment.current(repo = File("../..")),
                    workloads = workloads.map { PerfWorkload(it.name, PerfStatus.SKIPPED, reason = "GL tests are opt-in (-Dabyssus.glTests=true) and need a display") },
                ), file!!,
            )
            return
        }
        val runner = EditorPerfRunner()
        val measured = workloads.map { runner.run(it) }
        val stress = EditorPerfRunner.stressInfo
        val finished = measured.map {
            if (it.name == "stress" && stress != null && it.status == PerfStatus.MEASURED) {
                it.copy(
                    sceneHash = stress.hash,
                    settings = it.settings + mapOf("modelTriangles" to stress.modelTriangles.toString(), "foliageCopies" to stress.foliageCopies.toString()),
                )
            } else it
        }
        PerfReportFiles().write(PerfReport(host = "editor", environment = runner.environment(), workloads = finished), file!!)
    }
}
