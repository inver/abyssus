/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.Gdx
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.time.Clock
import net.nevinsky.abyssus.app.game.controlline.controller.FakeControllerSource
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Headless: spec `control-line-runtime-stability`, "The game releases what it holds on exit". */
class ControlLineGameDisposeTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun disposeBuildsNoRasterRendererItNeverDrewWith() {
        val logs = temp.root.toPath().resolve("logs")
        val crashes =
            CrashReporter(logs, GameEnvironment.current(), Clock.systemUTC(), PrintStream(ByteArrayOutputStream()))
        val log = RecordingLogger()
        val graphics = Gdx.graphics
        // no window: making a FieldRenderer would fail on Gdx.graphics, which the guard would record as a crash
        Gdx.graphics = null
        try {
            val game =
                ControlLineGame(
                    bundledProject(),
                    temp.root.toPath().resolve("scores.json"),
                    controllerSource = FakeControllerSource(),
                    crashes = crashes,
                    log = log,
                    exit = { fail("no exit is asked for") },
                )
            val prepare = Thread.getAllStackTraces().keys.filter { it.name == "asset-prepare" }.toSet()
            game.dispose()
            assertTrue(
                "the game's own context closed its threads",
                (Thread.getAllStackTraces().keys - prepare).none { it.name == "asset-prepare" },
            )
        } finally {
            Gdx.graphics = graphics
        }

        assertFalse("dispose made nothing that failed", crashes.crashed)
        assertFalse(Files.exists(logs))
        assertTrue(log.warnings.toString(), log.warnings.isEmpty())
    }
}
