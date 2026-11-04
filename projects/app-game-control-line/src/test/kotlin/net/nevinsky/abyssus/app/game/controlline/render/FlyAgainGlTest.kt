/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.app.game.controlline.CrashReporter
import net.nevinsky.abyssus.app.game.controlline.GameEnvironment
import net.nevinsky.abyssus.app.game.controlline.controller.FakeControllerSource
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.time.Clock

/**
 * Spec `control-line-performance`, "Starting a flight stays within its hitch budget", scenario "Fly again": the real game
 * flies a Watch AI flight, goes back to the menu and flies again, and no frame between shows the field without its models.
 * `-Dabyssus.glTests=true`.
 */
class FlyAgainGlTest {
    @Test
    fun noFrameBetweenTwoFlightsDrawsTheFieldWithoutItsModels() {
        assumeTrue("GL tests are opt-in: -Dabyssus.glTests=true", TestGl.enabled)
        TestGl.run(300, 640, 360) {
            Gdx.files = Lwjgl3Files()
            val project = Files.createTempDirectory("fly-again").toFile()
            val home = Files.createTempDirectory("fly-again-home").toFile()
            File(checkNotNull(System.getProperty("controlLine.project"))).copyRecursively(project, overwrite = true)
            val log = RecordingLogger(echo = true)
            val crashes = CrashReporter(home.toPath().resolve("logs"), GameEnvironment.current(), Clock.systemUTC(), PrintStream(ByteArrayOutputStream()))
            lateinit var flow: GameFlow
            val game = ControlLineGame(
                project.toPath(), home.toPath().resolve("scores.json"), controllerSource = FakeControllerSource(),
                crashes = crashes, log = log, exit = {}, frameSeconds = { 1f / 60f }, onStart = { flow = it },
            )
            try {
                game.create()
                game.resize(640, 360)
                fun frame() {
                    game.render()
                    check(!crashes.crashed) { "the game crashed: ${log.entries.lastOrNull()}" }
                }
                // the parked field, loaded
                var settled = 0
                var frames = 0
                while (settled < 20) {
                    check(frames++ < 6000) { "the field did not load" }
                    frame()
                    settled = if (!game.assetsLoading && game.lastDrawnModels > 0) settled + 1 else 0
                }
                val seen = ArrayList<String>()
                fun watch(what: String, n: Int) = repeat(n) {
                    frame()
                    seen += "$what ${flow.screen::class.simpleName}: ${game.lastDrawnModels}"
                }
                repeat(2) { flight ->
                    flow.mainMenu()
                    flow.f2bPattern()
                    flow.chooseAssist(AssistLevel.MID)
                    watch("plane select $flight", 10)
                    flow.watchAi()
                    watch("flight $flight", 60)
                    assertTrue(flow.screen is Screen.Flying)
                    flow.pause()
                    watch("paused $flight", 5)
                }
                flow.mainMenu()
                watch("menu", 10)
                assertTrue("every frame drew models:\n${seen.joinToString("\n")}", seen.none { it.endsWith(": 0") })
            } finally {
                game.dispose()
                project.deleteRecursively()
                home.deleteRecursively()
            }
        }
    }
}
