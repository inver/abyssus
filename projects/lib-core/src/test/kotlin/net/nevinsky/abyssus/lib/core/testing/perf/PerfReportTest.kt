/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PerfReportTest {
    private val env = PerfEnvironment("abc", false, "macOS 27 arm64", "Zulu 21", "Apple M1 Pro", "4.1 Metal", "OpenGL")

    private val measured = PerfWorkload(
        "untitled", PerfStatus.MEASURED, sceneHash = "h", settings = mapOf("size" to "1280x720"),
        stats = PerfStats(900, 1.0, 2.0, 3.0, listOf(2.0, 2.1), 1024.0, 2, 5), uploadWork = 4,
        assetCounts = mapOf("models" to 3), coldLoadMs = 120.0, reloadMs = 30.0,
    )
    private val skipped = PerfWorkload("stress", PerfStatus.SKIPPED, reason = "GL tests are opt-in")

    @Test
    fun aReportWithMeasuredSkippedAndUnavailableWorkloadsRoundTripsThroughItsFile() {
        val unavailable = PerfWorkload("game", PerfStatus.UNAVAILABLE, reason = "no GL context")
        val report = PerfReport(host = "editor", environment = env, workloads = listOf(measured, skipped, unavailable))
        val file = File(Files.createTempDirectory("perf").toFile(), "r/report.json")
        try {
            PerfReportFiles().write(report, file)
            assertEquals(report, PerfReportFiles().read(file))
            val text = file.readText()
            for (key in listOf("commit", "dirty", "os", "jdk", "gpu", "driver", "backend", "sceneHash", "settings", "p50Ms", "p95Ms", "p99Ms",
                "allocatedBytesPerFrame", "gcCount", "uploadWork", "assetCounts", "coldLoadMs", "reloadMs", "status")) {
                assertTrue("missing $key", text.contains("\"$key\""))
            }
            assertTrue(text.contains("\"SKIPPED\"") && text.contains("\"UNAVAILABLE\""))
        } finally {
            file.parentFile.parentFile.deleteRecursively()
        }
    }

    @Test
    fun aSkippedWorkloadNeedsAReasonAndNoStatsAndAMeasuredOneNeedsStats() {
        assertThrows(IllegalArgumentException::class.java) { PerfWorkload("x", PerfStatus.SKIPPED) }
        assertThrows(IllegalArgumentException::class.java) { PerfWorkload("x", PerfStatus.MEASURED) }
        assertThrows(IllegalArgumentException::class.java) { measured.copy(status = PerfStatus.SKIPPED, reason = "r") }
    }

    @Test
    fun anotherSchemaVersionIsRefused() {
        val file = File(Files.createTempDirectory("perf").toFile(), "old.json").also { it.writeText("""{"schema":99}""") }
        try {
            assertThrows(IllegalArgumentException::class.java) { PerfReportFiles().read(file) }
        } finally {
            file.parentFile.deleteRecursively()
        }
    }

    @Test
    fun theCurrentEnvironmentNamesTheMachineAndTheCommit() {
        val current = PerfEnvironment.current(gpu = "g", driver = "d", repo = File("."))
        assertTrue(current.os.isNotBlank() && current.jdk.isNotBlank())
        assertEquals("g", current.gpu)
        assertFalse(current.sameMachineAs(env))
    }
}
