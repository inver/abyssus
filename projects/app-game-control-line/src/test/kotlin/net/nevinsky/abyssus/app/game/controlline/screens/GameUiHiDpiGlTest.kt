/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.ui.state.UiScreenRef
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.helpers.NOPLogger
import java.lang.reflect.Proxy

/**
 * The screens are drawn and clickable in a window whose back buffer is [SCALE] times its logical size, as on a HiDPI
 * display (libGDX scales the viewport and the scissor by that factor itself). Opt-in: `-Dabyssus.glTests=true`.
 */
class GameUiHiDpiGlTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val width = 1280
    private val height = 720

    /** A graphics stub with a back buffer [SCALE] times the window, which is all the host and `HdpiUtils` read. */
    private fun hiDpiGraphics(): Graphics =
        Proxy.newProxyInstance(Graphics::class.java.classLoader, arrayOf(Graphics::class.java)) { _, m, _ ->
            when (m.name) {
                "getWidth" -> width
                "getHeight" -> height
                "getBackBufferWidth" -> width * SCALE
                "getBackBufferHeight" -> height * SCALE
                "getDeltaTime" -> 0f
                "isGL30Available" -> true
                else -> when (m.returnType) {
                    java.lang.Boolean.TYPE -> false
                    Integer.TYPE -> 0
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as Graphics

    @Test
    fun theMainMenuIsDrawnAndClickableOnAHiDpiWindow() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val scene = loadField()
        val planes = planeChoices(scene.engine.ids.ids.associateWith { scene.engine.ids[it]!! })
        val flow = GameFlow(
            planes,
            ScoreTable(temp.root.toPath().resolve("s.json")),
            PatternScoreTable(temp.root.toPath().resolve("p.json")),
            ScaleScoreTable(temp.root.toPath().resolve("sc.json"))
        )
        val real = Gdx.graphics
        Gdx.graphics = hiDpiGraphics()
        val ui = createGameUi(bundledProject(), "ControlLine.abss", flow, NOPLogger.NOP_LOGGER) {}
        val target = FrameBuffer(Pixmap.Format.RGBA8888, width * SCALE, height * SCALE, false)
        try {
            ui.resize()
            ui.update(null, null, 0f)
            val clear = Color(0.5f, 0f, 0.5f, 1f)
            target.begin()
            Gdx.gl.glClearColor(clear.r, clear.g, clear.b, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            ui.draw(0f)
            val ref = UiScreenRef(SCREENS_ASSET, SCREEN_MAIN_MENU)
            val button = ui.presenter.layout(ref)!!.nodes.getValue("mm-free").bounds
            assertEquals("the menu column is the design's left column", 96f, button.x, 1f)
            assertTrue(
                "and sits in the middle of the window's height, not against the top edge",
                button.y > height / 4f && button.y < height * 3f / 4f
            )
            val cx = (button.x + 6f).toInt()
            val cy = (button.y + button.height / 2f).toInt()
            fun pixelAt(x: Int, yTop: Int): Int = Pixmap.createFromFrameBuffer(
                x * SCALE, height * SCALE - yTop * SCALE - 1, 1, 1
            ).getPixel(0, 0)
            // the clear colour as the buffer stores it, read from a corner nothing is drawn on
            val background = pixelAt(width - 4, height - 4)
            assertNotEquals("the button is drawn over the window's own area", background, pixelAt(cx, cy))
            target.end()

            // a click at the button's logical position presses it
            val x = (button.x + button.width / 2f).toInt()
            val y = (button.y + button.height / 2f).toInt()
            ui.inputProcessor.touchDown(x, y, 0, 0)
            ui.inputProcessor.touchUp(x, y, 0, 0)
            assertEquals(Screen.PlaneSelect(0), flow.screen)
        } finally {
            target.dispose()
            ui.dispose()
            Gdx.graphics = real
        }
    }

    private companion object {
        const val SCALE = 2
    }
}
