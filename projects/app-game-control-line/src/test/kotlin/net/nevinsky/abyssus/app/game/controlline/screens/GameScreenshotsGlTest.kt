/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.ScreenUtils
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.helpers.NOPLogger
import java.io.File

/**
 * Draws every step of the screen walk over a sky-blue stand-in for the field and writes the pictures to
 * `build/screenshots/<step>.png`, for a human to look at. Opt-in: `-Dabyssus.glTests=true`.
 */
class GameScreenshotsGlTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val out = File("build/screenshots")

    /** The host driver, taking a picture each time the walk reads what is shown. */
    private class Shooter(private val host: GameUiHost, private val inner: GameUiHostDriver, private val pictures: MutableList<File>, private val dir: File) : ScreenDriver {
        override fun update(session: FlightSession?, judge: PatternJudge?, seconds: Float) = inner.update(session, judge, seconds)

        override fun keyDown(keycode: Int) = inner.keyDown(keycode)

        override fun dump(): List<String> {
            val fbo = FrameBuffer(Pixmap.Format.RGBA8888, 1280, 720, false)
            fbo.begin()
            Gdx.gl.glClearColor(0.44f, 0.66f, 0.78f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            host.draw(0f)
            val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, 1280, 720)
            fbo.end()
            fbo.dispose()
            val file = File(dir, "step-%02d.png".format(pictures.size))
            // the frame buffer reads bottom-up
            val flipped = Pixmap(1280, 720, Pixmap.Format.RGBA8888)
            for (y in 0 until 720) for (x in 0 until 1280) flipped.drawPixel(x, y, pixmap.getPixel(x, 719 - y))
            PixmapIO.writePNG(Gdx.files.absolute(file.absolutePath), flipped)
            pixmap.dispose(); flipped.dispose()
            pictures += file
            return inner.dump()
        }
    }

    @Test fun everyStepIsWrittenAsAPicture() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        out.deleteRecursively(); out.mkdirs()
        val hosts = mutableListOf<GameUiHost>()
        val pictures = mutableListOf<File>()
        try {
            val steps = ScreenWalk(loadField(), temp.root.toPath()).run { flow ->
                val host = createGameUi(bundledProject(), "ControlLine.abss", flow, NOPLogger.NOP_LOGGER) {}.also(hosts::add)
                Shooter(host, GameUiHostDriver(host), pictures, out)
            }
            steps.keys.forEachIndexed { i, name -> pictures[i].renameTo(File(out, "$name.png")) }
            assertTrue(steps.size == pictures.size)
        } finally {
            hosts.forEach { it.dispose() }
        }
    }
}
