/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("PerfCompareMain")

package net.nevinsky.abyssus.lib.core.testing.perf

import java.io.File
import kotlin.system.exitProcess

/**
 * Prints the verdict of every workload of a candidate report against a baseline: `PerfCompareMain <baseline> <candidate>`.
 * The exit status is 1 when any workload regressed and 0 otherwise; inconclusive, mismatched and unmeasured workloads
 * are printed and are never a pass. Run through `./gradlew :lib-core:comparePerf -Pbaseline=<file> -Pcandidate=<file>`.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: PerfCompareMain <baseline report> <candidate report>")
        exitProcess(2)
    }
    val files = PerfReportFiles()
    val findings = PerfComparison().compare(files.read(File(args[0])), files.read(File(args[1])))
    findings.forEach { println("%-22s %-12s %s".format(java.util.Locale.ROOT, it.workload, it.verdict, it.detail)) }
    if (findings.any { it.verdict == PerfVerdict.REGRESSION }) exitProcess(1)
}
