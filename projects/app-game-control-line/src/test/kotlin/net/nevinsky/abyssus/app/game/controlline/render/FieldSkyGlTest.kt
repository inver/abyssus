/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.components.CONTROL_LINE_COMPONENTS
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import net.nevinsky.abyssus.lib.core.assets.sky.SkyRenderer
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The field's sky (its own atmosphere and cloud asset, without the terrain) drawn with each cloud technique into a 1280 x 720 target: a picture of each to
 * `build/sky-shots/<technique>-<view>.png` for a person to compare with the reference photo, and the milliseconds a
 * frame takes. Opt-in: `-Dabyssus.glTests=true`.
 */
class FieldSkyGlTest {
    private class Shot(val image: Pixmap, val millis: Double)

    @Test
    fun everyTechniqueIsPicturedAndCumulusIsFasterThanShells() {
        assumeTrue("GL tests are opt-in", TestGl.enabled)
        val out = File("build/sky-shots").apply { deleteRecursively(); mkdirs() }
        val times = HashMap<CloudTechnique, Double>()
        TestGl.run(1200) {
            for (technique in listOf(CloudTechnique.LAYERED, CloudTechnique.CUMULUS, CloudTechnique.SHELLS, CloudTechnique.VOLUMETRIC)) {
                for ((view, shot) in render(technique)) {
                    PixmapIO.writePNG(Gdx.files.absolute(File(out, "${technique.key}-$view.png").absolutePath), shot.image)
                    println("sky ${technique.key} $view: %.2f ms a frame".format(shot.millis))
                    if (view == "sky") times[technique] = shot.millis
                    shot.image.dispose()
                }
            }
        }
        val cumulus = times.getValue(CloudTechnique.CUMULUS)
        val shells = times.getValue(CloudTechnique.SHELLS)
        assertTrue("cumulus %.2f ms against shells %.2f ms".format(cumulus, shells), cumulus < shells)
    }

    /** Draws just the field's sky (its foliage needs instancing, which the test context lacks) with [technique]. */
    private fun render(technique: CloudTechnique): Map<String, Shot> {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("sky-control-line").toFile()
        File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx = BaseCtx(project.path, "/shader", ControlLineGame::class.java, log)
            .also { it.componentRegistry.registerAll(CONTROL_LINE_COMPONENTS) }
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val target = FrameBuffer(Pixmap.Format.RGBA8888, 1280, 720, true)
        try {
            val field = FieldScene(requireNotNull(loader.load("Field.scene")) { messages.joinToString("\n") })
            val name = checkNotNull(field.skyName) { "the field has no sky" }
            var sky: SkyRenderer? = null
            var frames = 0
            while (sky == null) {
                ctx.assetStorage.request(name)
                ctx.assetStorage.update()
                sky = ctx.assetStorage.getAs<SkyRenderer>(name)
                if (sky == null) Thread.sleep(10)
                check(++frames < 2_000) { "the sky never loaded: $messages" }
            }
            fun view(elevation: Float) = PerspectiveCamera(60f, 1280f, 720f).apply {
                position.set(0f, 1.6f, 0f)
                direction.set(0.3f, 0f, -1f).nor().rotate(Vector3(1f, 0f, 0.3f).nor(), elevation)
                up.set(Vector3.Y)
                near = 0.5f
                far = 20_000f
                update()
            }
            fun draw(camera: PerspectiveCamera, time: Double) {
                target.begin()
                Gdx.gl.glClearColor(0.6f, 0.7f, 0.85f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
                Gdx.gl.glDepthMask(false)
                Gdx.gl.glDisable(GL20.GL_CULL_FACE)
                sky.draw(camera, SkyFrame(field.sunDirection, time, technique = technique))
                Gdx.gl.glDepthMask(true)
                target.end()
            }
            fun shot(camera: PerspectiveCamera): Shot {
                repeat(10) { draw(camera, 30.0) }
                target.begin(); Gdx.gl.glFinish(); target.end()
                val start = System.nanoTime()
                repeat(60) { draw(camera, 30.0) }
                target.begin(); Gdx.gl.glFinish()
                val millis = (System.nanoTime() - start) / 60e6
                val pixmap = Pixmap.createFromFrameBuffer(0, 0, 1280, 720)
                target.end()
                // the frame buffer reads bottom-up
                val upright = Pixmap(1280, 720, Pixmap.Format.RGBA8888)
                for (y in 0 until 720) for (x in 0 until 1280) upright.drawPixel(x, y, pixmap.getPixel(x, 719 - y))
                pixmap.dispose()
                return Shot(upright, millis)
            }
            return mapOf("sky" to shot(view(25f)), "low" to shot(view(8f)))
        } finally {
            target.dispose()
            ctx.executor.shutdownNow()
            project.deleteRecursively()
        }
    }
}
