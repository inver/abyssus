/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import jdk.jfr.Configuration
import jdk.jfr.Recording
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.CrashReporter
import net.nevinsky.abyssus.app.game.controlline.GameEnvironment
import net.nevinsky.abyssus.app.game.controlline.controller.FakeControllerSource
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.lib.core.testing.perf.FrameSampler
import net.nevinsky.abyssus.lib.core.testing.perf.PerfEnvironment
import net.nevinsky.abyssus.lib.core.testing.perf.PerfHitch
import net.nevinsky.abyssus.lib.core.testing.perf.PerfReport
import net.nevinsky.abyssus.lib.core.testing.perf.PerfReportFiles
import net.nevinsky.abyssus.lib.core.testing.perf.PerfStats
import net.nevinsky.abyssus.lib.core.testing.perf.PerfStatus
import net.nevinsky.abyssus.lib.core.testing.perf.PerfWorkload
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Control Line's whole frame (docs/ai/testing.md "Graphics performance evidence", design decision D4 of
 * `improve-control-line-performance-stability`): the real [ControlLineGame] frame body, in a 1280 x 720 window, with a
 * fixed 1/60 s step and a Watch AI F2B flight (Mid) on a copy of the bundled project.
 *
 * - CPU spans `frame`, `flight`, `field` and `ui` and the field's passes, with allocation per frame, from the sample
 *   run.
 * - GPU time per pass from `GL_TIME_ELAPSED` queries in the same run (read back without blocking), or why there is
 *   none.
 * - `GLProfiler` counters for the main view, the shadow pass and the UI, from a separate run (`counters`).
 * - Five flight starts from plane select and five debrief openings after a warm one of each: request to the end of the
 *   first frame drawn with it.
 *
 * `-Dabyssus.perf.jfr=<file.jfr>` records a JFR `profile` of the samples alone (task 6.1).
 *
 * Opt in with `-Dabyssus.glTests=true -Dabyssus.perf.report=<file>`; `abyssus.perf.warmupSeconds` / `sampleSeconds` /
 * `samples` shorten a smoke run, whose report is never compared with the committed baseline. Without a display the
 * report is still written, with the workload marked skipped.
 */
class PerfWorkloadsGlTest {
    private val warmupSeconds = System.getProperty("abyssus.perf.warmupSeconds")?.toDouble() ?: 10.0
    private val sampleSeconds = System.getProperty("abyssus.perf.sampleSeconds")?.toDouble() ?: 30.0
    private val samples = System.getProperty("abyssus.perf.samples")?.toInt() ?: 5
    private val step = 1f / 60f

    /** `legacy` (flat ambient, no display transform) or `pbr` (the default), chosen with `-Dabyssus.perf.pipeline`. */
    private val pipelineName = System.getProperty("abyssus.perf.pipeline") ?: "pbr"
    private val pipeline
        get() =
            if (pipelineName == "legacy") {
                FieldRenderSettings.LEGACY
            } else {
                FieldRenderSettings()
            }

    private val settings
        get() =
            mapOf(
                "resolution" to "1280x720",
                "warmupSeconds" to warmupSeconds.toString(),
                "sampleSeconds" to sampleSeconds.toString(),
                "samples" to samples.toString(),
                "fixedDeltaSeconds" to step.toString(),
                "pipeline" to pipelineName,
                "frameTime" to "CPU time of ControlLineGame.render (whole frame), not GPU time",
                "flight" to "Watch AI, F2B pattern, Mid, the first stunt plane",
                "hitches" to "$HITCHES after a warm one",
                "target" to "the default framebuffer of a 1280x720 window (the game's own)",
            )

    @Test
    fun measureTheControlLineFrame() {
        val file = System.getProperty("abyssus.perf.report")?.let(::File)
        assumeTrue("set -Dabyssus.perf.report=<file> to measure", file != null)
        val repo = File("../..")
        if (!TestGl.enabled) {
            PerfReportFiles()
                .write(
                    PerfReport(
                        host = "game",
                        environment = PerfEnvironment.current(repo = repo),
                        workloads =
                            listOf(
                                PerfWorkload(
                                    WORKLOAD,
                                    PerfStatus.SKIPPED,
                                    reason = "GL tests are opt-in (-Dabyssus.glTests=true) and need a display",
                                )
                            ),
                    ),
                    file!!,
                )
            return
        }
        val total = (warmupSeconds + sampleSeconds * samples).toLong() + 900
        var gpu = "unavailable"
        var driver = "unavailable"
        val workload =
            TestGl.run(total, 1280, 720) {
                gpu = Gdx.gl.glGetString(GL20.GL_RENDERER) ?: "unknown"
                driver = Gdx.gl.glGetString(GL20.GL_VERSION) ?: "unknown"
                measure()
            }
        PerfReportFiles()
            .write(
                PerfReport(
                    host = "game",
                    environment = PerfEnvironment.current(gpu, driver, repo = repo),
                    workloads = listOf(workload),
                ),
                file!!,
            )
        // spec "Allocation per frame stays within budget": the run fails, naming the measured value, after its report
        // is written
        val measured = workload.stats?.allocatedBytesPerFrame ?: return
        check(measured <= allocationBudget) {
            "allocation per frame %.0f bytes is over the budget of %d bytes (report %s)"
                .format(java.util.Locale.ROOT, measured, allocationBudget, file)
        }
    }

    /**
     * The steady allocation budget per whole frame in bytes: the game's README ("Performance", 256 KB, `proposed` until
     * the maintainer approves it), or `-Dabyssus.perf.allocationBudget`.
     */
    private val allocationBudget: Long =
        System.getProperty("abyssus.perf.allocationBudget")?.toLong() ?: ALLOCATION_BUDGET_BYTES

    private class Run(
        val game: ControlLineGame,
        val crashes: CrashReporter,
        val probes: Probes,
        val log: RecordingLogger,
    ) {
        lateinit var flow: GameFlow
        var clock = 1f / 60f

        /** One frame of the game; returns the CPU nanoseconds of `render`, the GPU work finished outside them. */
        fun frame(): Long {
            val started = System.nanoTime()
            game.render()
            val nanos = System.nanoTime() - started
            probes.frameEnd()
            Gdx.gl.glFinish()
            check(!crashes.crashed) { "the game crashed: ${log.entries.lastOrNull()}" }
            return nanos
        }

        /** Wall milliseconds from now to the end of the next frame, its GPU work included. */
        fun timedFrame(): Double {
            val started = System.nanoTime()
            game.render()
            Gdx.gl.glFinish()
            probes.frameEnd()
            check(!crashes.crashed) { "the game crashed: ${log.entries.lastOrNull()}" }
            return (System.nanoTime() - started) / 1e6
        }

        /** Frames until no asset is loading for [SETTLED] frames in a row, at most [limitSeconds]. */
        fun settle(limitSeconds: Long = 120) {
            val end = System.nanoTime() + limitSeconds * 1_000_000_000L
            var settled = 0
            while (settled < SETTLED) {
                check(System.nanoTime() < end) { "the field did not finish loading within $limitSeconds s" }
                frame()
                settled =
                    if (game.assetsLoading) {
                        0
                    } else {
                        settled + 1
                    }
            }
        }

        fun framesFor(seconds: Double, stopWhen: () -> Boolean = { false }, each: (Long) -> Unit = {}) {
            val end = System.nanoTime() + (seconds * 1e9).toLong()
            while (System.nanoTime() < end && !stopWhen()) each(frame())
        }

        /** From any screen of the flow to F2B (Mid) plane select. */
        fun toPlaneSelect() {
            when (flow.screen) {
                is Screen.Flying -> flow.pause()
                else -> Unit
            }
            flow.mainMenu()
            flow.f2bPattern()
            flow.chooseAssist(AssistLevel.MID)
            check(flow.screen is Screen.PlaneSelect) { "not on plane select: ${flow.screen}" }
        }
    }

    private fun measure(): PerfWorkload {
        Gdx.files = Lwjgl3Files()
        val project = Files.createTempDirectory("perf-control-line").toFile()
        val home = Files.createTempDirectory("perf-control-line-home").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        val spans = SpanRecorder()
        val timer = GpuPassTimer()
        val counters = CounterProbe()
        val probes = Probes(listOf(spans, timer, counters))
        val log = RecordingLogger(echo = true)
        val crashes =
            CrashReporter(
                home.toPath().resolve("logs"),
                GameEnvironment.current(),
                Clock.systemUTC(),
                PrintStream(ByteArrayOutputStream()),
            )
        lateinit var run: Run
        val game =
            ControlLineGame(
                project.toPath(),
                home.toPath().resolve("scores.json"),
                controllerSource = FakeControllerSource(),
                crashes = crashes,
                log = log,
                exit = {},
                probe = probes,
                frameSeconds = { run.clock },
                onStart = { run.flow = it },
                fieldSettings = pipeline,
            )
        run = Run(game, crashes, probes, log)
        val hash = hashOf(File(project, "scenes/Field.scene"))
        try {
            game.create()
            game.resize(1280, 720)
            check(!crashes.crashed) { "the game failed to start: ${log.entries}" }
            run.settle()

            // the warm flight: its start is not one of the measured hitches
            run.toPlaneSelect()
            run.frame()
            run.flow.watchAi()
            run.frame()
            run.settle()
            run.framesFor(warmupSeconds)

            // the sample run: CPU spans and GPU passes while the flight is in progress
            timer.start()
            spans.recording = true
            timer.recording = true
            val sampler = FrameSampler()
            val finished = mutableListOf<PerfStats>()
            // an allocation and CPU profile of the samples alone (`-Dabyssus.perf.jfr=<file.jfr>`): no load, no hitch
            val profile =
                System.getProperty("abyssus.perf.jfr")?.let {
                    Recording(Configuration.getConfiguration("profile")).also(Recording::start) to it
                }
            repeat(samples) {
                if (run.flow.screen !is Screen.Flying) {
                    run.flow.retry()
                    spans.recording = false
                    timer.recording = false
                    run.settle()
                    spans.recording = true
                    timer.recording = true
                }
                sampler.start()
                run.framesFor(sampleSeconds, { run.flow.screen !is Screen.Flying }) { sampler.record(it) }
                finished += sampler.stats()
            }
            spans.recording = false
            timer.recording = false
            profile?.let { (recording, path) ->
                recording.stop()
                recording.dump(Path.of(path))
                recording.close()
            }
            // the queries still on their way back
            repeat(4) { run.frame() }
            val gpu = timer.result()

            // the counter run: the counting GL checks for errors after every call, so its CPU times are not the
            // report's
            counters.install()
            counters.recording = true
            try {
                run.framesFor(3.0, { run.flow.screen !is Screen.Flying })
            } finally {
                counters.recording = false
                counters.uninstall()
            }

            // to the end of the flight, fast: then its debrief
            run.clock = 0.1f
            val ends = System.nanoTime() + 600_000_000_000L
            while (run.flow.screen !is Screen.GameOver) {
                check(System.nanoTime() < ends) { "the Watch AI flight did not end: ${run.flow.screen}" }
                run.frame()
            }
            run.clock = step
            run.framesFor(1.0)
            val debrief =
                (0..HITCHES)
                    .map {
                        check(run.flow.screen is Screen.GameOver) { "not on GAME OVER: ${run.flow.screen}" }
                        run.flow.debrief()
                        check(run.flow.screen is Screen.Debrief) { "the debrief did not open: ${run.flow.screen}" }
                        val ms = run.timedFrame()
                        run.framesFor(0.5)
                        run.flow.back()
                        run.framesFor(0.5)
                        ms
                    }
                    .drop(1)

            val flightStart =
                (0 until HITCHES).map {
                    run.toPlaneSelect()
                    run.framesFor(0.5)
                    run.flow.watchAi()
                    val ms = run.timedFrame()
                    run.framesFor(0.5)
                    ms
                }

            val combined = FrameSampler.combine(finished).copy(spans = spans.spans())
            return PerfWorkload(
                WORKLOAD,
                PerfStatus.MEASURED,
                sceneHash = hash,
                settings = settings,
                stats = combined,
                assetCounts = emptyMap(),
                gpu = gpu,
                counters = counters.result("counters"),
                hitches = mapOf("flightStart" to hitchOf(flightStart), "debriefOpen" to hitchOf(debrief)),
                cpuNotComparable = presentationThrottled(),
                agents = agents(),
            )
        } catch (failed: IllegalStateException) {
            return PerfWorkload(
                WORKLOAD,
                PerfStatus.UNAVAILABLE,
                reason = failed.message ?: failed.toString(),
                sceneHash = hash,
            )
        } finally {
            game.dispose()
            timer.dispose()
            project.deleteRecursively()
            home.deleteRecursively()
        }
    }

    private fun hitchOf(ms: List<Double>): PerfHitch {
        val sorted = ms.sorted()
        return PerfHitch(sorted[sorted.size / 2], sorted.last(), ms)
    }

    /**
     * Why CPU times may not be compared: a locked macOS session throttles presentation (evidence M2). Null when not
     * seen.
     */
    private fun presentationThrottled(): String? {
        if (!System.getProperty("os.name").lowercase().contains("mac")) {
            return null
        }
        return try {
            val out =
                ProcessBuilder("ioreg", "-n", "Root", "-d1")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .readText()
            if (Regex(""""CGSSessionScreenIsLocked"\s*=\s*Yes""").containsMatchIn(out)) {
                "the screen was locked (CGSSessionScreenIsLocked)"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The `-javaagent` jars of this JVM (the game's `test` task attaches Kover's): such a run is never compared without
     * them.
     */
    private fun agents(): List<String> =
        ManagementFactory.getRuntimeMXBean()
            .inputArguments
            .filter { it.startsWith("-javaagent:") }
            .map { File(it.removePrefix("-javaagent:").substringBefore('=')).name }

    private fun hashOf(scene: File): String =
        MessageDigest.getInstance("SHA-256").digest(scene.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val WORKLOAD = "control-line-flight"
        const val SETTLED = 20
        const val HITCHES = 5
        const val ALLOCATION_BUDGET_BYTES = 256L * 1024
    }
}
