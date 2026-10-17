/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.core.testing.perf.FrameSampler
import net.nevinsky.abyssus.lib.core.testing.perf.PerfEnvironment
import net.nevinsky.abyssus.lib.core.testing.perf.PerfReport
import net.nevinsky.abyssus.lib.core.testing.perf.PerfReportFiles
import net.nevinsky.abyssus.lib.core.testing.perf.PerfStats
import net.nevinsky.abyssus.lib.core.testing.perf.PerfStatus
import net.nevinsky.abyssus.lib.core.testing.perf.PerfWorkload
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Control Line's field through [FieldRenderer] (the game's graphics workload, docs/ai/testing.md "Graphics performance
 * evidence"). Opt in with `-Dabyssus.glTests=true -Dabyssus.perf.report=<file>`; `abyssus.perf.warmupSeconds` /
 * `sampleSeconds` / `samples` shorten a smoke run, whose report is never compared with the committed baseline. Without a
 * display the report is still written, with the workload marked skipped. Frame time is the CPU time of
 * [FieldRenderer.draw] into a 1280 x 720 target, not GPU time.
 */
class PerfWorkloadsGlTest {
    private val warmupSeconds = System.getProperty("abyssus.perf.warmupSeconds")?.toDouble() ?: 10.0
    private val sampleSeconds = System.getProperty("abyssus.perf.sampleSeconds")?.toDouble() ?: 30.0
    private val samples = System.getProperty("abyssus.perf.samples")?.toInt() ?: 5
    private val step = 1f / 60f

    private val settings
        get() = mapOf(
            "resolution" to "1280x720", "warmupSeconds" to warmupSeconds.toString(), "sampleSeconds" to sampleSeconds.toString(),
            "samples" to samples.toString(), "fixedDeltaSeconds" to step.toString(),
            "frameTime" to "CPU time of FieldRenderer.draw (submission), not GPU time",
        )

    @Test
    fun measureTheControlLineField() {
        val file = System.getProperty("abyssus.perf.report")?.let(::File)
        assumeTrue("set -Dabyssus.perf.report=<file> to measure", file != null)
        val repo = File("../..")
        if (!TestGl.enabled) {
            PerfReportFiles().write(
                PerfReport(
                    host = "game", environment = PerfEnvironment.current(repo = repo),
                    workloads = listOf(PerfWorkload(WORKLOAD, PerfStatus.SKIPPED, reason = "GL tests are opt-in (-Dabyssus.glTests=true) and need a display")),
                ), file!!,
            )
            return
        }
        val total = (warmupSeconds + sampleSeconds * samples).toLong() + 400
        var gpu = "unavailable"
        var driver = "unavailable"
        val workload = TestGl.run(total) {
            gpu = Gdx.gl.glGetString(GL20.GL_RENDERER) ?: "unknown"
            driver = Gdx.gl.glGetString(GL20.GL_VERSION) ?: "unknown"
            measure()
        }
        PerfReportFiles().write(PerfReport(host = "game", environment = PerfEnvironment.current(gpu, driver, repo = repo), workloads = listOf(workload)), file!!)
    }

    private fun measure(): PerfWorkload {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("perf-control-line").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx = BaseCtx(project.path, "/shader", ControlLineGame::class.java, log)
            .also { it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS) }
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer = FieldRenderer(ctx)
        val target = FrameBuffer(Pixmap.Format.RGBA8888, 1280, 720, true)
        try {
            val hash = hashOf(File(project, "scenes/Field.scene"))
            fun load() = FieldScene(requireNotNull(loader.load("Field.scene")) { messages.joinToString("\n") })
            var field = load()
            val cameras = Cameras(1280f, 720f)
            fun frame(): Long {
                val pilot = field.position(checkNotNull(field.pilot)).getTransform().getTranslation(Vector3())
                val plane = field.position(checkNotNull(field.entity(field.planes.first().entityId))).getTransform().getTranslation(Vector3())
                cameras.flying(pilot, plane)
                target.begin()
                val started = System.nanoTime()
                renderer.draw(field, cameras.camera, seconds = step)
                val nanos = System.nanoTime() - started
                target.end()
                Gdx.gl.glFinish()
                return nanos
            }

            /** Draws until the scene's assets are built and stayed built for [SETTLED] frames; returns the frames and the milliseconds. */
            fun settle(limitSeconds: Long): Pair<Int, Double>? {
                val start = System.nanoTime()
                var frames = 0
                var settled = 0
                while (settled < SETTLED) {
                    if (System.nanoTime() - start > limitSeconds * 1_000_000_000L) return null
                    frame()
                    frames++
                    settled = if (renderer.loaded(field)) settled + 1 else 0
                }
                return (frames - SETTLED) to ((System.nanoTime() - start) / 1e6 - SETTLED * step * 1000.0)
            }

            val cold = settle(120) ?: return PerfWorkload(WORKLOAD, PerfStatus.UNAVAILABLE, reason = "the field did not finish loading: $messages", sceneHash = hash)
            val warmupEnd = System.nanoTime() + (warmupSeconds * 1e9).toLong()
            while (System.nanoTime() < warmupEnd) frame()
            val models = field.models.size
            val terrains = field.terrains.size

            val sampler = FrameSampler()
            val finished = mutableListOf<PerfStats>()
            repeat(samples) {
                sampler.start()
                val end = System.nanoTime() + (sampleSeconds * 1e9).toLong()
                while (System.nanoTime() < end) sampler.record(frame())
                finished += sampler.stats()
            }

            field = load()
            val reload = settle(120) ?: return PerfWorkload(WORKLOAD, PerfStatus.UNAVAILABLE, reason = "the field did not finish reloading", sceneHash = hash)
            return PerfWorkload(
                WORKLOAD, PerfStatus.MEASURED, sceneHash = hash, settings = settings, stats = FrameSampler.combine(finished),
                uploadWork = cold.first.toLong(), assetCounts = mapOf("models" to models, "terrains" to terrains),
                coldLoadMs = cold.second, reloadMs = reload.second,
            )
        } finally {
            target.dispose()
            renderer.dispose()
            ctx.executor.shutdownNow()
            project.deleteRecursively()
        }
    }

    private fun hashOf(scene: File): String =
        MessageDigest.getInstance("SHA-256").digest(scene.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val WORKLOAD = "control-line-field"
        const val SETTLED = 20
    }
}
