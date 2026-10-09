/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.gdx.editor.ray.RayFeasibilityLoop
import net.nevinsky.abyssus.lib.gdx.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3

import net.nevinsky.abyssus.lib.raytracing.*
import org.lwjgl.opengl.GL32C.*
import kotlin.math.sin

/** Developer-only synthetic scene for the runIde feasibility gate, independent of project assets and edits. */
internal class RayFeasibilityPreview(private val report: (String) -> Unit) {
    val orbit = OrbitCamera(Vec3(0f, 1f, 0f), 8f, 0f, 0.4f)
    private val geometry = listOf(
        floor(10f), floor(2f),
        RaySliceMesh(floatArrayOf(-1f, 0f, -2f, 1f, 0f, -2f, 0f, 3f, -2f), intArrayOf(0, 1, 2))
    )
    private var loop: RayFeasibilityLoop? = null
    private var presenter: RayFramePresenter? = null
    private val started = System.nanoTime()
    private var revision = 0L
    private var lastFrame: RayFrame? = null
    private var measureStarted = 0L
    private val uploads = mutableListOf<Double>()
    private val latencies = mutableListOf<Double>()
    private val submissions = mutableListOf<Double>()
    private var reportedFailure = false
    val failure: Throwable? get() = loop?.failure

    fun draw(width: Int, height: Int) {
        val driver = loop ?: RayFeasibilityLoop { MetalRayBackendFactory().probe() }.also { loop = it }
        val began = System.nanoTime()
        val time = (began - started) / 1_000_000_000.0
        driver.offer(request(time, ++revision))
        val offered = System.nanoTime()
        glViewport(0, 0, width, height)
        glClearColor(0.05f, 0.1f, 0.2f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val ready = driver.latest()
        if (ready != null) {
            val pass = presenter ?: RayFramePresenter().also { presenter = it }
            pass.draw(ready.frame)
            val drawn = System.nanoTime()
            if (ready.frame !== lastFrame) {
                lastFrame = ready.frame
                if (measureStarted == 0L) measureStarted = drawn
                uploads.add((drawn - offered) / 1_000_000.0)
                latencies.add((drawn - ready.offeredNanos) / 1_000_000.0)
                submissions.add((offered - began) / 1_000_000.0)
                if (drawn - measureStarted >= 30_000_000_000L || uploads.size >= 4096) {
                    val seconds = (drawn - measureStarted) / 1_000_000_000.0
                    fun p95(values: List<Double>) = values.sorted()[((values.size - 1) * 0.95).toInt()]
                    report("Metal feasibility ${driver.info}: output=${width}x$height internal=640x360 " +
                        "seconds=$seconds frames=${uploads.size} fps=${(uploads.size - 1) / seconds} " +
                        "p95 offer=${p95(submissions)}ms upload/draw=${p95(uploads)}ms " +
                        "offer-to-draw=${p95(latencies)}ms; swap/input latency requires the manual check")
                    uploads.clear(); latencies.clear(); submissions.clear()
                    measureStarted = 0
                }
            }
        }
        if (driver.failure != null && !reportedFailure) {
            reportedFailure = true
            report("Metal feasibility failed: ${driver.failure}")
        }
    }

    /** Same immutable reference scene used by the live preview and the native image regression. */
    internal fun request(time: Double, revision: Long): RayRequest {
        val eye = orbit.position()
        val target = orbit.target
        val camera = RaySliceCamera(
            listOf(eye.x, eye.y, eye.z), listOf(target.x - eye.x, target.y - eye.y, target.z - eye.z),
            listOf(0f, 1f, 0f), 60f, 0.1f, 100f, listOf(2f, 2f, 1f)
        )
        // Fixed 640x360 internal dimensions make transfer cost and the gate's minimum resolution explicit.
        return RayRequest(RayFrameKey(1, 1, revision, revision), 640, 360, camera, geometry, listOf(
            instance(0, 0f, 0f, listOf(0.7f, 0.7f, 0.7f)),
            instance(1, 0f, 0.02f, listOf(1f, 1f, 1f), true),
            instance(2, sin(time).toFloat() * 2f, 0f, listOf(1f, 0.2f, 0.1f))
        ))
    }

    fun stop() {
        loop?.close()
        loop = null
        lastFrame = null
        uploads.clear(); latencies.clear(); submissions.clear()
        measureStarted = 0
        reportedFailure = false
    }

    /** Called only with the old GL context current. */
    fun dispose() { stop(); presenter?.dispose(); presenter = null }

    /** The abandoned context owns the old handles; never delete them in the replacement context. */
    fun abandon() { stop(); presenter = null }

    private fun floor(size: Float) = RaySliceMesh(
        floatArrayOf(-size, 0f, -size, size, 0f, -size, size, 0f, size, -size, 0f, size), intArrayOf(0, 2, 1, 0, 3, 2)
    )
    private fun instance(mesh: Int, x: Float, y: Float, color: List<Float>, reflective: Boolean = false) =
        RaySliceInstance(mesh, listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, 0f, 1f), color, reflective)
}
