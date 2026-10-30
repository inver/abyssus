/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.perf

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.MainCamera
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.core.testing.perf.FrameSampler
import net.nevinsky.abyssus.lib.core.testing.perf.PerfEnvironment
import net.nevinsky.abyssus.lib.core.testing.perf.PerfStatus
import net.nevinsky.abyssus.lib.core.testing.perf.PerfWorkload
import net.nevinsky.abyssus.lib.core.testing.perf.StressScene
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import net.nevinsky.abyssus.plugin.sceneview.SceneRenderer
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.math.sqrt

/** How long a run lasts; the defaults are the committed measurement protocol, shorter values are for smoke checks only. */
data class PerfOptions(
    val warmupSeconds: Double = 10.0,
    val sampleSeconds: Double = 30.0,
    val samples: Int = 5,
    val width: Int = 1280,
    val height: Int = 720,
    /** Per-frame step of the sky, animation and water, so every run draws the same sequence. */
    val fixedDeltaSeconds: Float = 1f / 60f,
) {
    val settings: Map<String, String>
        get() = mapOf(
            "resolution" to "${width}x$height", "warmupSeconds" to warmupSeconds.toString(), "sampleSeconds" to sampleSeconds.toString(),
            "samples" to samples.toString(), "fixedDeltaSeconds" to fixedDeltaSeconds.toString(),
        )

    companion object {
        fun fromProperties() = PerfOptions().let { d ->
            d.copy(
                warmupSeconds = System.getProperty("abyssus.perf.warmupSeconds")?.toDouble() ?: d.warmupSeconds,
                sampleSeconds = System.getProperty("abyssus.perf.sampleSeconds")?.toDouble() ?: d.sampleSeconds,
                samples = System.getProperty("abyssus.perf.samples")?.toInt() ?: d.samples,
            )
        }
    }
}

/** One scene of the editor's workloads, drawn from a disposable copy of [source] ([prepare] may rebuild the copy). */
class EditorWorkload(
    val name: String,
    val sceneFile: String,
    val camera: (File) -> CameraParams,
    val prepare: (File) -> Unit = {},
    val extraSettings: Map<String, String> = emptyMap(),
    val source: () -> File,
)

/**
 * Draws [EditorWorkload]s through the real scene renderer in a GL canvas and measures the CPU time of each frame:
 * a cold load, a warmup, then samples of steady frames, then a reload. Frame time is the time [SceneRenderer.render]
 * takes on the AWT thread (command submission), not GPU time, and is labeled so in the report.
 */
class EditorPerfRunner(private val options: PerfOptions = PerfOptions.fromProperties()) {
    /** GL_RENDERER and GL_VERSION of the context, once any workload has run. */
    var gpu = "unavailable"
        private set
    var driver = "unavailable"
        private set

    fun environment() = PerfEnvironment.current(gpu, driver, repo = File("../.."))

    fun run(workload: EditorWorkload): PerfWorkload {
        val dir = Files.createTempDirectory("perf-${workload.name}").toFile()
        try {
            workload.source().copyRecursively(dir, overwrite = true)
            workload.prepare(dir)
            val sceneText = File(dir, workload.sceneFile).readText()
            val params = renderParamsOf(parseScene(sceneText), workload.camera(dir), dir, SceneJson().parse(sceneText))
            val hash = sha256(sceneText, File(dir, "assets"))
            return measure(workload, params, hash)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun measure(workload: EditorWorkload, params: SceneRenderParams, hash: String): PerfWorkload {
        val sampler = FrameSampler()
        val finished = mutableListOf<net.nevinsky.abyssus.lib.core.testing.perf.PerfStats>()
        var phase = Phase.LOAD
        var phaseStart = System.nanoTime()
        val runStart = phaseStart
        var lastFrameNanos = 0L
        var loadFrames = 0
        var coldLoadMs: Double? = null
        var coldFrames = 0
        var settled = 0
        var reloadMs: Double? = null
        var models = 0
        var terrains = 0
        var failure: String? = null
        val limit = runStart + ((options.warmupSeconds + options.sampleSeconds * options.samples) * 1e9).toLong() + LOAD_LIMIT_NANOS * 2

        fun enter(next: Phase, now: Long) {
            phase = next
            phaseStart = now
        }

        val result = GlHarness.render(
            params, frames = Int.MAX_VALUE, orbit = OrbitCamera(params.camera),
            executor = java.util.concurrent.Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } },
            framebufferSize = options.width to options.height, log = org.slf4j.helpers.NOPLogger.NOP_LOGGER,
            timeoutSeconds = ((limit - runStart) / 1_000_000_000L) + 30, fixedDeltaSeconds = options.fixedDeltaSeconds,
            onGlInfo = { renderer, version -> gpu = renderer; driver = version },
            onFrameNanos = { _, nanos -> lastFrameNanos = nanos },
            shouldStop = { phase == Phase.DONE || failure != null },
            afterFrame = { renderer: SceneRenderer, _ ->
                val now = System.nanoTime()
                when (phase) {
                    Phase.LOAD -> {
                        loadFrames++
                        settled = if (renderer.loading) 0 else settled + 1
                        if (settled >= SETTLED_FRAMES) {
                            coldLoadMs = (now - runStart) / 1e6 - SETTLED_FRAMES * options.fixedDeltaSeconds * 1000.0
                            coldFrames = loadFrames - SETTLED_FRAMES
                            enter(Phase.WARMUP, now)
                        } else if (now - runStart > LOAD_LIMIT_NANOS) failure = "the scene did not finish loading in ${LOAD_LIMIT_NANOS / 1_000_000_000L} s"
                    }
                    Phase.WARMUP -> if (now - phaseStart >= options.warmupSeconds * 1e9) {
                        models = renderer.drawnModels.size
                        terrains = renderer.drawnTerrains.size
                        sampler.start()
                        enter(Phase.SAMPLE, now)
                    }
                    Phase.SAMPLE -> {
                        sampler.record(lastFrameNanos)
                        if (now - phaseStart >= options.sampleSeconds * 1e9) {
                            finished += sampler.stats()
                            if (finished.size == options.samples) {
                                renderer.params = renderer.params.copy()
                                loadFrames = 0
                                settled = 0
                                enter(Phase.RELOAD, now)
                            } else {
                                sampler.start()
                                enter(Phase.SAMPLE, now)
                            }
                        }
                    }
                    Phase.RELOAD -> {
                        loadFrames++
                        settled = if (renderer.loading) 0 else settled + 1
                        if (settled >= SETTLED_FRAMES) {
                            reloadMs = (now - phaseStart) / 1e6 - SETTLED_FRAMES * options.fixedDeltaSeconds * 1000.0
                            enter(Phase.DONE, now)
                        } else if (now - phaseStart > LOAD_LIMIT_NANOS) failure = "the scene did not finish reloading"
                    }
                    Phase.DONE -> Unit
                }
            },
        )
        val error = result.error?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: failure
        if (error != null || phase != Phase.DONE) {
            return PerfWorkload(workload.name, PerfStatus.UNAVAILABLE, reason = error ?: "the run ended in phase $phase", sceneHash = hash)
        }
        return PerfWorkload(
            workload.name, PerfStatus.MEASURED, sceneHash = hash,
            settings = options.settings + workload.extraSettings + ("frameTime" to "CPU time of SceneRenderer.render (submission), not GPU time"),
            stats = FrameSampler.combine(finished),
            // uploads are sliced per frame, so the frames the cold load took are its upload work
            uploadWork = coldFrames.toLong(),
            assetCounts = mapOf("models" to models, "terrains" to terrains),
            coldLoadMs = coldLoadMs, reloadMs = reloadMs,
        )
    }

    private fun sha256(sceneText: String, assets: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sceneText.toByteArray())
        assets.walkTopDown().filter { it.isFile && it.name == "foliage.data" }.sortedBy { it.path }.forEach { digest.update(it.readBytes()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private enum class Phase { LOAD, WARMUP, SAMPLE, RELOAD, DONE }

    companion object {
        /** Frames in a row without loading before a scene counts as loaded (work is requested a frame or two after the first). */
        private const val SETTLED_FRAMES = 20
        private const val LOAD_LIMIT_NANOS = 120_000_000_000L

        /** The camera the project's `.abss` holds, or the editor's default when it has none. */
        fun projectCamera(dir: File): CameraParams =
            dir.listFiles { f -> f.extension == "abss" }?.firstOrNull()?.let { MainCamera().parse(it.readText()) } ?: CameraParams.DEFAULT

        /** High over the middle of the 1600 m stress terrain, looking at it. */
        fun stressCamera(@Suppress("UNUSED_PARAMETER") dir: File): CameraParams {
            val from = Vec3(0f, 450f, -1100f)
            val to = Vec3(0f, 0f, 0f)
            val d = Vec3(to.x - from.x, to.y - from.y, to.z - from.z)
            val len = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
            return CameraParams(from, Vec3(d.x / len, d.y / len, d.z / len), 1f, 6000f, 60f)
        }

        fun workloads(): List<EditorWorkload> = listOf(
            EditorWorkload("untitled", "scenes/Main Scene.scene", ::projectCamera) { testProject("Untitled") },
            EditorWorkload("animated", "scenes/Main.scene", ::projectCamera) { testProject("Animated") },
            EditorWorkload("foliage", "scenes/Main Scene.scene", ::projectCamera) { testProject("Foliage") },
            EditorWorkload(
                "stress", "scenes/${StressScene.SCENE_NAME}.scene", ::stressCamera,
                prepare = { dir ->
                    // regenerate over the plain copy run() made; the source project is only read
                    dir.listFiles()?.forEach(File::deleteRecursively)
                    stressInfo = StressScene().generate(testProject("Untitled"), dir, triangles(testProject("Untitled")))
                },
                extraSettings = mapOf("seed" to StressScene.SEED.toString(), "placements" to StressScene.MODELS.toString()),
            ) { testProject("Untitled") },
        )

        /** What the last stress copy held; its hash and counts go into the report's settings. */
        @Volatile
        var stressInfo: net.nevinsky.abyssus.lib.core.testing.perf.StressSceneInfo? = null

        private fun triangles(project: File): Long {
            GdxNativesLoader.load()
            val model = File(project, "assets").listFiles { f -> f.name.startsWith("model_") && File(f, "model.gltf").isFile }!!.minBy { it.name }
            val data = AssimpModelLoader().loadData(FileHandle(File(model, "model.gltf")))
            return data.meshes.sumOf { mesh -> mesh.parts.sumOf { it.indices.size.toLong() / 3 } }
        }
    }
}
