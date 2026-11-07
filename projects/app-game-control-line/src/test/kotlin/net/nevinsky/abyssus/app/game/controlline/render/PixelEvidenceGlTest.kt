/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
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

/**
 * Pixel evidence of Control Line's field and hangar (see the editor's `PixelEvidenceGlTest` and docs/ai/testing.md):
 * opt in with `-Dabyssus.glTests=true -Dabyssus.perf.pixels=record|compare`, with `-Dabyssus.perf.pixelsDir` (record)
 * or `-Dabyssus.perf.pixelsReference` (compare). Each case is drawn into a 1280 x 720 target for a fixed number of
 * frames at a fixed 1/60 s step, the field from the flying camera and the hangar from its own camera, and read back. A
 * case with no picture in the reference folder is not compared.
 */
class PixelEvidenceGlTest {
    private val evidence = PixelEvidence()
    private val mode = System.getProperty("abyssus.perf.pixels")

    @Test
    fun recordOrCompareTheFieldPixels() {
        assumeTrue("set -Dabyssus.perf.pixels=record|compare", mode == "record" || mode == "compare")
        assumeTrue("GL tests are opt-in", TestGl.enabled)
        TestGl.run(1200, 1280, 720) {
            for (case in CASES) {
                if (mode == "record") {
                    record(case)
                } else {
                    compare(case)
                }
            }
        }
    }

    private fun record(case: Case) {
        val out = File(System.getProperty("abyssus.perf.pixelsDir") ?: "build/perf/pixels")
        val first = render(case)
        val diffs = (1 until RUNS).map { evidence.diff(first, render(case)) }
        evidence.writeImage(first, File(out, "${case.name}.png"))
        evidence.writeTolerance(
            PixelTolerance(case.name, RUNS, first.width, first.height, evidence.toleranceOf(diffs)),
            File(out, "${case.name}.tolerance.json"),
        )
        println("pixels ${case.name}: repeated-run tolerance ${evidence.toleranceOf(diffs)}")
    }

    private fun compare(case: Case) {
        val reference =
            File(
                checkNotNull(System.getProperty("abyssus.perf.pixelsReference")) {
                    "set -Dabyssus.perf.pixelsReference=<dir>"
                }
            )
        val picture = File(reference, "${case.name}.png")
        if (!picture.isFile) {
            println("pixels ${case.name}: no reference in $reference, not compared")
            return
        }
        val tolerance = evidence.readTolerance(File(reference, "${case.name}.tolerance.json")).tolerance
        val diff = evidence.diff(evidence.readImage(picture), render(case))
        assertTrue("${case.name}: $diff is outside the recorded tolerance $tolerance", evidence.within(diff, tolerance))
    }

    private fun render(case: Case): BufferedImage {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("pixels-control-line").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx =
            BaseCtx(project.path, "/shader", ControlLineGame::class.java, log).also {
                it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS)
            }
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer =
            FieldRenderer(
                ctx,
                requested =
                    if (System.getProperty("abyssus.perf.pipeline") == "legacy") {
                        FieldRenderSettings.LEGACY
                    } else {
                        FieldRenderSettings()
                    },
            )
        try {
            val field = FieldScene(requireNotNull(loader.load(case.scene)) { messages.joinToString("\n") })
            val cameras = Cameras(1280f, 720f)
            val aim = case.aim(field)
            // draw until everything is built, then a fixed number of frames so the sky and clouds are at the same time
            // every run
            var settled = 0
            var frames = 0
            while (settled < 30) {
                aim(cameras)
                Gdx.gl.glViewport(0, 0, 1280, 720)
                renderer.draw(field, cameras.camera, seconds = 0f)
                // everything built and nothing still loading (textures arrive after the models and terrains)
                settled =
                    if (renderer.loaded(field) && !ctx.assetStorage.isLoading()) {
                        settled + 1
                    } else {
                        0
                    }
                if (settled == 0) {
                    Thread.sleep(5)
                }
                check(++frames < 20_000) { "the field never loaded: $messages" }
            }
            repeat(FRAMES) {
                aim(cameras)
                Gdx.gl.glViewport(0, 0, 1280, 720)
                renderer.draw(field, cameras.camera, seconds = 1f / 60f)
            }
            // the game's own target is the default framebuffer, read bottom-up
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, 1280, 720)
            val image = BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 720) for (x in 0 until 1280) image.setRGB(x, y, pixmap.getPixel(x, 719 - y) ushr 8)
            pixmap.dispose()
            return image
        } finally {
            renderer.dispose()
            ctx.close()
            project.deleteRecursively()
        }
    }

    /** A picture: [name] of the files, the [scene] drawn and how [aim] points the camera at it. */
    private class Case(val name: String, val scene: String, val aim: (FieldScene) -> (Cameras) -> Unit)

    private companion object {
        /** The field from the flying camera, and the hangar (plane select) from its own camera. */
        val CASES =
            listOf(
                Case("control-line-field", "Field.scene") { field ->
                    val pilot = field.position(checkNotNull(field.pilot)).getTransform().getTranslation(Vector3())
                    val plane =
                        field
                            .position(checkNotNull(field.entity(field.planes.first().entityId)))
                            .getTransform()
                            .getTranslation(Vector3())
                    val aim: (Cameras) -> Unit = { cameras -> cameras.flying(pilot, plane) }
                    aim
                },
                Case("control-line-hangar", "Hangar.scene") { field ->
                    val (at, camera) = checkNotNull(field.camera) { "the hangar has no camera" }
                    val aim: (Cameras) -> Unit = { cameras ->
                        cameras.fixed(at.localPosition, camera.direction, camera.fieldOfView)
                    }
                    aim
                },
            )
        const val RUNS = 5
        const val FRAMES = 120
    }
}
