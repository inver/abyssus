/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The workload's probes over the bundled field (task 5.3 of `improve-control-line-performance-stability`): timer queries
 * give the sky pass a positive GPU time, or say why they cannot, and the counters see both the main view and the
 * shadow pass. `-Dabyssus.glTests=true`.
 */
class FrameProbesGlTest {
    @Test
    fun theSkyPassHasAGpuTimeAndBothPassesHaveCounters() {
        assumeTrue("GL tests are opt-in: -Dabyssus.glTests=true", TestGl.enabled)
        TestGl.run(240, 640, 360) {
            Gdx.files = Lwjgl3Files()
            ShaderProgram.pedantic = false
            val project = Files.createTempDirectory("probes-control-line").toFile()
            File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
            val messages = mutableListOf<String>()
            val log = warningsTo(messages)
            val ctx = BaseCtx(project.path, "/shader", ControlLineGame::class.java, log)
                .also { it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS) }
            val timer = GpuPassTimer()
            val counters = CounterProbe()
            val spans = SpanRecorder()
            val probes = Probes(listOf(spans, timer, counters))
            val renderer = FieldRenderer(ctx, probe = probes)
            try {
                val field = FieldScene(requireNotNull(RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log).load("Field.scene")))
                val cameras = Cameras(640f, 360f)
                fun frame() {
                    val pilot = field.position(checkNotNull(field.pilot)).localPosition
                    cameras.flying(pilot, Vector3(pilot).add(0f, 3f, -15f))
                    Gdx.gl.glViewport(0, 0, 640, 360)
                    renderer.draw(field, cameras.camera, seconds = 1f / 60f)
                    probes.frameEnd()
                }
                var settled = 0
                var frames = 0
                while (settled < 10 && frames++ < 3000) {
                    frame()
                    settled = if (renderer.loaded(field) && !ctx.assetStorage.isLoading()) settled + 1 else 0
                }
                assertTrue("the field loaded: $messages", settled >= 10)

                timer.start()
                listOf(timer, spans).forEach { it.recording = true }
                repeat(30) { frame() }
                Gdx.gl.glFinish()
                repeat(5) { frame() }
                timer.recording = false
                spans.recording = false

                counters.install()
                counters.recording = true
                try {
                    repeat(10) { frame() }
                } finally {
                    counters.uninstall()
                }

                val gpu = timer.result()
                if (gpu.unavailable == null) {
                    val sky = checkNotNull(gpu.passes["sky"]) { "no sky time in ${gpu.passes.keys}" }
                    assertTrue("the sky takes GPU time: $sky", sky.p50Ms > 0.0)
                    assertTrue("a total is reported", gpu.passes.containsKey("total"))
                } else {
                    println("GPU times unavailable here: ${gpu.unavailable}")
                }
                val cpu = spans.spans()
                listOf("sky", "shadow", "terrain", "models", "lines").forEach { assertTrue("CPU span $it in ${cpu.keys}", it in cpu) }
                val count = counters.result("counters")
                assertTrue("main view draws: ${count["main"]}", count.getValue("main").drawCalls > 0)
                assertTrue("shadow pass draws: ${count["shadow"]}", count.getValue("shadow").drawCalls > 0)
                // design decision D12: each depth shader is bound once per frame (spec "Shadow casters are drawn in shader order")
                val depthShaders = renderer.sunShadows.depthShaders
                assertTrue(
                    "shadow switches ${count.getValue("shadow").shaderSwitches} for $depthShaders depth shaders",
                    count.getValue("shadow").shaderSwitches <= depthShaders + 1,
                )
                assertTrue(count.getValue("main").vertices > 0)
                timer.dispose()
            } finally {
                renderer.dispose()
                ctx.close()
                project.deleteRecursively()
            }
        }
    }
}
