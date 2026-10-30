/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.core.testing.perf.PixelEvidence
import net.nevinsky.abyssus.lib.core.testing.perf.PixelTolerance
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files

/**
 * Pixel evidence of Control Line's field (see the editor's `PixelEvidenceGlTest` and docs/ai/testing.md): opt in with
 * `-Dabyssus.glTests=true -Dabyssus.perf.pixels=record|compare`, with `-Dabyssus.perf.pixelsDir` (record) or
 * `-Dabyssus.perf.pixelsReference` (compare). The field is drawn into a 1280 x 720 target for a fixed number of frames at a
 * fixed 1/60 s step, from the flying camera, and read back.
 */
class PixelEvidenceGlTest {
    private val evidence = PixelEvidence()
    private val mode = System.getProperty("abyssus.perf.pixels")

    @Test
    fun recordOrCompareTheFieldPixels() {
        assumeTrue("set -Dabyssus.perf.pixels=record|compare", mode == "record" || mode == "compare")
        assumeTrue("GL tests are opt-in", TestGl.enabled)
        TestGl.run(1200) {
            if (mode == "record") record() else compare()
        }
    }

    private fun record() {
        val out = File(System.getProperty("abyssus.perf.pixelsDir") ?: "build/perf/pixels")
        val first = render()
        val diffs = (1 until RUNS).map { evidence.diff(first, render()) }
        evidence.writeImage(first, File(out, "$CASE.png"))
        evidence.writeTolerance(PixelTolerance(CASE, RUNS, first.width, first.height, evidence.toleranceOf(diffs)), File(out, "$CASE.tolerance.json"))
        println("pixels $CASE: repeated-run tolerance ${evidence.toleranceOf(diffs)}")
    }

    private fun compare() {
        val reference = File(checkNotNull(System.getProperty("abyssus.perf.pixelsReference")) { "set -Dabyssus.perf.pixelsReference=<dir>" })
        val tolerance = evidence.readTolerance(File(reference, "$CASE.tolerance.json")).tolerance
        val diff = evidence.diff(evidence.readImage(File(reference, "$CASE.png")), render())
        assertTrue("$CASE: $diff is outside the recorded tolerance $tolerance", evidence.within(diff, tolerance))
    }

    private fun render(): BufferedImage {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("pixels-control-line").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx = BaseCtx(project.path, "/shader", ControlLineGame::class.java, log)
            .also { it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS) }
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer = FieldRenderer(ctx)
        val target = FrameBuffer(Pixmap.Format.RGBA8888, 1280, 720, true)
        try {
            val field = FieldScene(requireNotNull(loader.load("Field.scene")) { messages.joinToString("\n") })
            val cameras = Cameras(1280f, 720f)
            val pilot = field.position(checkNotNull(field.pilot)).getTransform().getTranslation(Vector3())
            val plane = field.position(checkNotNull(field.entity(field.planes.first().entityId))).getTransform().getTranslation(Vector3())
            // draw until everything is built, then a fixed number of frames so the sky and clouds are at the same time every run
            var settled = 0
            var frames = 0
            while (settled < 30) {
                cameras.flying(pilot, plane)
                target.begin(); renderer.draw(field, cameras.camera, seconds = 0f); target.end()
                settled = if (renderer.loaded(field)) settled + 1 else 0
                check(++frames < 20_000) { "the field never loaded: $messages" }
            }
            repeat(FRAMES) {
                cameras.flying(pilot, plane)
                target.begin(); renderer.draw(field, cameras.camera, seconds = 1f / 60f); target.end()
            }
            target.begin()
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, 1280, 720)
            target.end()
            val image = BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 720) for (x in 0 until 1280) image.setRGB(x, y, pixmap.getPixel(x, y) ushr 8)
            pixmap.dispose()
            return image
        } finally {
            target.dispose()
            renderer.dispose()
            ctx.executor.shutdownNow()
            project.deleteRecursively()
        }
    }

    private companion object {
        const val CASE = "control-line-field"
        const val RUNS = 5
        const val FRAMES = 120
    }
}
