/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.loadField
import org.slf4j.helpers.NOPLogger
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins what the game's screens show, step by step through a whole session, against `characterization/screens.golden`
 * recorded from the hand-written Scene2D screens that the authored `screens` asset replaced. Opt-in with the other GL
 * tests (`-Dabyssus.glTests=true`); re-record with `-Dabyssus.recordScreens=true` only when a screen is meant to change.
 */
class GameScreensCharacterizationTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val golden = File("src/test/resources/characterization/screens.golden")

    @Test fun theAuthoredScreensShowWhatWasRecorded() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val hosts = mutableListOf<GameUiHost>()
        try {
            val steps = ScreenWalk(loadField(), temp.root.toPath()).run { flow ->
                GameUiHostDriver(createGameUi(bundledProject(), "ControlLine.abss", flow, NOPLogger.NOP_LOGGER) {}.also(hosts::add))
            }
            val text = renderSteps(steps)
            if (System.getProperty("abyssus.recordScreens") == "true") {
                golden.parentFile.mkdirs(); golden.writeText(text)
            }
            assertEquals(golden.readText(), text)
        } finally {
            hosts.forEach { it.dispose() }
        }
    }
}
