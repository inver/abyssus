/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.controller.ControllerProfiles
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerService
import net.nevinsky.abyssus.app.game.controlline.controller.FakeControllerSource
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_AXIS_LEFT_Y
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_START
import net.nevinsky.abyssus.app.game.controlline.controller.drive
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** A flight flown with a fake pad through the game's own lifecycle and controller drive, as `ControlLineGame` runs them, without a window. */
class ControllerFlightLifecycleTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val runners = ArrayList<FlightRunner>()

    @After
    fun closeRunners() = runners.forEach(FlightRunner::close)

    private inner class Game {
        val dir = temp.root.toPath()
        val pad = FakeControllerSource().plug("pad", "Logitech F310", gamepad = true)
        val free = ScoreTable(dir.resolve("scores.json"))
        val flow = GameFlow(
            loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) },
            free, PatternScoreTable(dir.resolve("f2b-scores.json")), ScaleScoreTable(dir.resolve("scale-scores.json")),
        ) { Instant.parse("2026-10-20T12:00:00Z") }
        val flights = FlightRunner(flow, { FieldScene(loadField()) }, { PhysicsAssets(bundledProject().toFile()) }, failOnWarnings(), JoltNatives())
            .also { runners += it }
        val controllers = ControllerService(pad, ControllerProfiles(dir.resolve("controls.json")), flights.input)
        val keys = ArrayList<Int>()

        /** [seconds] of 60 Hz frames, as the game renders them: the controllers, then the flow, then the flight. */
        fun frames(seconds: Float) = repeat(Math.round(seconds * 60f)) {
            controllers.drive(flow, 1f / 60f) { keys += it }
            flights.follow()
            flights.advance(1f / 60f)
        }

        fun fly() {
            flow.freeFlight()
            flow.choosePlane()
            flights.follow()
        }

        val elevation: Float get() = flights.session?.flight?.lastSample?.elevation ?: 0f
    }

    @Test
    fun aStickPushedUpTiltsTheHandleAndThePlaneClimbs() {
        val neutral = Game().also { it.fly() }
        val pushed = Game().also { it.fly() }
        pushed.pad.axis("pad", GAMEPAD_AXIS_LEFT_Y, 1f)
        var neutralMax = -90f
        var pushedMax = -90f
        repeat(25) {
            neutral.frames(1f)
            pushed.frames(1f)
            neutralMax = maxOf(neutralMax, neutral.elevation)
            pushedMax = maxOf(pushedMax, pushed.elevation)
        }
        assertEquals(1f, pushed.flights.input.tilt, 0f)
        assertEquals(0f, neutral.flights.input.tilt, 0f)
        assertTrue("neutral reached $neutralMax degrees, pushed $pushedMax", pushedMax > neutralMax + 10f)
    }

    @Test
    fun thePauseButtonPausesTheFlightAndTheSticksMenuKeysAreSilentWhileFlying() {
        val g = Game().also { it.fly() }
        g.frames(1f)
        g.pad.press("pad", GAMEPAD_BUTTON_START)
        g.frames(1f / 30f)
        assertTrue("paused: ${g.flow.screen}", g.flow.screen is Screen.Paused)
        assertEquals(emptyList<Int>(), g.keys)
    }

    @Test
    fun aFlightFlownWithTheStickEndsAndEntersTheBoardAsWithTheKeys() {
        val g = Game().also { it.fly() }
        g.frames(25f)
        g.pad.axis("pad", GAMEPAD_AXIS_LEFT_Y, -1f)
        var frames = 0
        while (g.flow.screen is Screen.Flying && frames++ < 120 * 60) g.frames(1f / 60f)
        val screen = g.flow.screen
        assertTrue("the flight did not end: $screen", screen is Screen.NameEntry)
        g.flow.nameEntered("Ann")
        assertEquals(1, g.free.entries.size)
        assertEquals("Ann", g.free.entries.single().name)
    }
}
