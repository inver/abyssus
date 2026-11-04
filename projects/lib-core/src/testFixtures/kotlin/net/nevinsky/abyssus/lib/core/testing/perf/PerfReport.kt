/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.testing.perf

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/** Bumped when a field of the report changes meaning; [PerfReportFiles.read] refuses another version. */
const val PERF_REPORT_SCHEMA = 1

/** Whether a workload was measured; a skipped or unavailable one never counts as a pass. */
enum class PerfStatus {
    MEASURED,
    SKIPPED,
    UNAVAILABLE,
}

/**
 * Where a report was made. Two reports compare only when these match ([PerfEnvironment.sameMachineAs]); [commit] and
 * [dirty] say which code was measured and never take part in the match.
 */
data class PerfEnvironment(
    val commit: String,
    val dirty: Boolean,
    val os: String,
    val jdk: String,
    /** The GL_RENDERER string, or "unavailable" when no context was made. */
    val gpu: String,
    /** The GL_VERSION string, which carries the driver. */
    val driver: String,
    /** "OpenGL" for these tests; kept as a field so a later backend is told apart. */
    val backend: String,
) {
    fun sameMachineAs(other: PerfEnvironment) =
        os == other.os && jdk == other.jdk && gpu == other.gpu && driver == other.driver && backend == other.backend

    companion object {
        /** The machine running now; [gpu] and [driver] come from the GL context, when there is one. */
        fun current(
            gpu: String = "unavailable",
            driver: String = "unavailable",
            backend: String = "OpenGL",
            repo: File? = null,
        ) =
            PerfEnvironment(
                commit = git(repo, "rev-parse", "HEAD") ?: "unknown",
                dirty = git(repo, "status", "--porcelain")?.isNotBlank() ?: true,
                os =
                    "${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
                        "${System.getProperty("os.arch")}",
                jdk = "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
                gpu = gpu,
                driver = driver,
                backend = backend,
            )

        private fun git(repo: File?, vararg args: String): String? =
            try {
                val process =
                    ProcessBuilder(listOf("git") + args)
                        .apply { repo?.let(::directory) }
                        .redirectErrorStream(true)
                        .start()
                val out = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0) {
                    out
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
    }
}

/** Frame-time and allocation figures of one measured workload. Times are CPU milliseconds per frame. */
data class PerfStats(
    val frames: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    /** The p95 of each separate sample, to judge how steady the run was. */
    val samplesP95Ms: List<Double> = emptyList(),
    /** Bytes the render thread allocated per frame, steady state. */
    val allocatedBytesPerFrame: Double,
    val gcCount: Long,
    val gcMillis: Long,
    /**
     * CPU time and allocation of named parts of each frame (a game reports `frame`, `flight`, `field` and `ui`); empty
     * in a report that measured only the frame as a whole.
     */
    val spans: Map<String, PerfSpan> = emptyMap(),
)

/** CPU time percentiles of one named part of the frame, and what it allocated per frame (null when not measured). */
data class PerfSpan(
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val allocatedBytesPerFrame: Double? = null,
)

/**
 * GPU time per pass from timer queries ([passes], with the whole frame under `total`), measured independently of
 * submission. When the context has no usable timer queries, [passes] is empty and [unavailable] says why: CPU time is
 * never put in its place.
 */
data class PerfGpu(
    val passes: Map<String, PerfSpan> = emptyMap(),
    val unavailable: String? = null,
) {
    init {
        require(passes.isNotEmpty() != (unavailable != null)) {
            "GPU times are either measured or unavailable with a reason"
        }
    }
}

/**
 * Render counters per frame of one pass (`GLProfiler`), averaged over the samples. [run] names the run they came from:
 * the profiler wraps every GL call, so its run's CPU times are never the report's.
 */
data class PerfCounters(
    val drawCalls: Double,
    val shaderSwitches: Double,
    val textureBindings: Double,
    val vertices: Double,
    val run: String,
)

/** The wall time of a one-off event (a flight start, the debrief opening) over its [samplesMs] repetitions. */
data class PerfHitch(
    val medianMs: Double,
    val maxMs: Double,
    val samplesMs: List<Double>,
)

/** One workload of a report. [stats] is null unless the status is [PerfStatus.MEASURED]. */
data class PerfWorkload(
    val name: String,
    val status: PerfStatus,
    /** Why a workload was skipped or unavailable. */
    val reason: String? = null,
    /** A hash of the scene (and bake) that was drawn, so a changed scene is never compared. */
    val sceneHash: String? = null,
    /** Resolution, warmup, samples, camera and the like; two workloads compare only with equal settings. */
    val settings: Map<String, String> = emptyMap(),
    val stats: PerfStats? = null,
    /** Textures and buffers uploaded while measuring (count), and what was uploaded during warmup. */
    val uploadWork: Long? = null,
    val assetCounts: Map<String, Int> = emptyMap(),
    val coldLoadMs: Double? = null,
    val reloadMs: Double? = null,
    /** Set only when the GPU time was measured independently of submission (a timer query); never inferred. */
    val gpuMs: Double? = null,
    /** GPU time per pass, or why it is unavailable; null in a report that did not try. */
    val gpu: PerfGpu? = null,
    /** Render counters per pass (a game reports `main` and `shadow`). */
    val counters: Map<String, PerfCounters> = emptyMap(),
    /** One-off events (a game reports `flightStart` and `debriefOpen`). */
    val hitches: Map<String, PerfHitch> = emptyMap(),
    /**
     * Why this run's CPU times may not be compared (the screen was locked or the window hidden, so presentation was
     * throttled), or null. GPU times, counters, allocation and hitches stay valid.
     */
    val cpuNotComparable: String? = null,
    /**
     * The coverage or profiling agents attached to the measuring JVM; a run with agents never compares with one
     * without.
     */
    val agents: List<String> = emptyList(),
) {
    init {
        require((status == PerfStatus.MEASURED) == (stats != null)) { "stats belong to measured workloads only: $name" }
        require(status == PerfStatus.MEASURED || reason != null) { "a $status workload needs a reason: $name" }
    }
}

/** What one run measured, for [host] "editor" or "game". */
data class PerfReport(
    val schema: Int = PERF_REPORT_SCHEMA,
    val host: String,
    val environment: PerfEnvironment,
    val workloads: List<PerfWorkload>,
) {
    fun workload(name: String) = workloads.firstOrNull { it.name == name }
}

/** Reads and writes reports as JSON files, stable enough to commit. */
class PerfReportFiles {
    private val mapper: ObjectMapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    fun toJson(report: PerfReport): String = mapper.writeValueAsString(report) + "\n"

    fun write(report: PerfReport, file: File) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(toJson(report))
    }

    fun read(file: File): PerfReport {
        val tree: JsonNode = mapper.readTree(file)
        require(tree.path("schema").asInt() == PERF_REPORT_SCHEMA) { "unsupported report schema in ${file.name}" }
        return mapper.readValue(file)
    }
}
