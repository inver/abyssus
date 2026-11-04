/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.PerspectiveCamera
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import net.nevinsky.abyssus.app.game.controlline.controller.FakeControllerSource
import net.nevinsky.abyssus.app.game.controlline.render.Cable
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.render.FieldView
import net.nevinsky.abyssus.app.game.controlline.render.LineSegment
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Headless: a failing frame leaves a crash record and ends the game (spec `control-line-runtime-stability`). */
class ControlLineGameCrashTest {
    @get:Rule val temp = TemporaryFolder()

    private class ThrowingView : FieldView {
        var draws = 0
        override val kind: String
            get() = "throwing"

        override fun draw(
            field: FieldScene,
            camera: Camera,
            lines: List<LineSegment>,
            hidden: Entity?,
            clips: Map<Entity, String>,
            seconds: Float,
            cables: List<Cable>,
            poses: Map<Entity, com.badlogic.gdx.math.Matrix4>,
        ) {
            draws++
            throw IllegalStateException("the injected view failed")
        }

        override fun resize(width: Float, height: Float) = Unit

        override fun dispose() = Unit
    }

    private fun reporter(folder: Path) =
        CrashReporter(
            folder,
            GameEnvironment.current().copy(glRenderer = "Test GPU", glVersion = "3.3", fieldView = "throwing"),
            Clock.systemUTC(),
            PrintStream(ByteArrayOutputStream()),
        )

    private fun records(folder: Path): List<Path> =
        if (!Files.isDirectory(folder)) {
            emptyList()
        } else {
            Files.list(folder).use { it.toList() }
        }

    @Test
    fun aThrowingViewWritesARecordAndEndsTheGameOnce() {
        val folder = temp.root.toPath().resolve("logs")
        val crashes = reporter(folder)
        var exits = 0
        val guard = FrameGuard(crashes, exit = { exits++ })
        val view = ThrowingView()
        val field = FieldScene(loadField())
        val frame = {
            view.draw(field, PerspectiveCamera(), emptyList(), null, emptyMap(), 0f, emptyList(), emptyMap())
        }

        guard.run(frame)
        guard.run(frame)

        assertEquals("no frame runs after the failure", 1, view.draws)
        assertEquals(1, exits)
        assertTrue("main exits with status 1", crashes.crashed)
        val text = Files.readString(records(folder).single())
        assertTrue(text, text.contains("java.lang.IllegalStateException: the injected view failed"))
        assertTrue(text, text.contains("gl renderer: Test GPU"))
        assertTrue(text, text.contains("field view: throwing"))
    }

    @Test
    fun theGameGuardsItsOwnFrame() {
        // no window: the game's frame fails on its first line, and the guard in ControlLineGame.render records it
        val folder = temp.root.toPath().resolve("logs")
        val crashes = reporter(folder)
        var exits = 0
        val graphics = Gdx.graphics
        Gdx.graphics = null
        try {
            val game =
                ControlLineGame(
                    bundledProject(),
                    temp.root.toPath().resolve("scores.json"),
                    fieldView = ThrowingView(),
                    controllerSource = FakeControllerSource(),
                    crashes = crashes,
                    log = RecordingLogger(),
                    exit = { exits++ },
                )
            game.render()
            game.render()
            game.dispose()
        } finally {
            Gdx.graphics = graphics
        }

        assertEquals(1, exits)
        assertTrue(crashes.crashed)
        assertEquals(1, records(folder).size)
    }

    @Test
    fun aSuccessfulFrameReportsNothing() {
        val folder = temp.root.toPath().resolve("logs")
        val crashes = reporter(folder)
        var exits = 0
        val guard = FrameGuard(crashes, exit = { exits++ })
        var frames = 0
        repeat(3) { guard.run { frames++ } }
        assertEquals(3, frames)
        assertEquals(0, exits)
        assertFalse(crashes.crashed)
        assertTrue(records(folder).isEmpty())
    }

    @Test
    fun aReleaseFailureAfterACrashIsOnlyLogged() {
        val folder = temp.root.toPath().resolve("logs")
        val crashes = reporter(folder)
        val guard = FrameGuard(crashes, exit = {})
        val log = RecordingLogger()
        guard.run { throw IllegalStateException("first") }
        guard.release(log) { throw IllegalStateException("second") }
        assertEquals("the record keeps the failure that ended the game", 1, records(folder).size)
        assertEquals(1, log.warnings.size)
    }
}
