/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ai.BotPhase
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flow.Control
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreEntry
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Instant

/** A Watch AI demonstration through the game's own flight lifecycle, as `ControlLineGame` runs it, without a window. */
class AiFlightLifecycleTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val runners = ArrayList<FlightRunner>()

    @After
    fun closeRunners() = runners.forEach(FlightRunner::close)

    private class Game(val flow: GameFlow, val flights: FlightRunner) {
        /** [seconds] of 60 Hz frames, as the game renders them: the flow first, then the flight. */
        fun frames(seconds: Float) = repeat(Math.round(seconds * 60f)) {
            flights.follow()
            flights.advance(1f / 60f)
        }

        val time: Float get() = checkNotNull(flights.session).flight.time

        val planePosition: Vector3
            get() = Vector3(checkNotNull(flights.session).plane.getComponent(PositionComponent::class.java).localPosition)
    }

    private fun game(): Game {
        val planes = loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) }
        val dir = temp.root.toPath()
        val flow = GameFlow(planes, ScoreTable(dir.resolve("scores.json")), PatternScoreTable(dir.resolve("f2b-scores.json")), ScaleScoreTable(dir.resolve("scale-scores.json"))) {
            Instant.parse("2026-10-20T12:00:00Z")
        }
        val runner = FlightRunner(flow, { FieldScene(loadField()) }, { PhysicsAssets(bundledProject().toFile()) }, failOnWarnings(), JoltNatives())
        runners += runner
        return Game(flow, runner)
    }

    private fun Game.watch(level: AssistLevel = AssistLevel.MID): Game {
        flow.f2bPattern()
        flow.chooseAssist(level)
        flow.watchAi()
        flights.follow()
        return this
    }

    @Test
    fun aDemonstrationIsFlownByTheBot() {
        val g = game().watch()
        assertTrue(g.flights.demonstration)
        assertNotNull(g.flights.bot)
        g.frames(25f)
        assertTrue("took off, on the base: ${g.planePosition}", g.planePosition.y > 1f)
        assertEquals(BotPhase.BASE, g.flights.bot!!.planner.phase)
    }

    @Test
    fun pauseFreezesThePlaneTheClockAndTheBotAndResumeGoesOnFromThere() {
        val g = game().watch()
        g.frames(46f)
        val bot = g.flights.bot!!
        g.flow.pause()
        val time = g.time
        val place = g.planePosition
        val phase = bot.planner.phase to bot.planner.figure
        val fuel = g.flights.session!!.flight.fuelLeft
        g.frames(10f)
        assertEquals(time, g.time, 0f)
        assertEquals(place, g.planePosition)
        assertEquals(phase, bot.planner.phase to bot.planner.figure)
        assertEquals(fuel, g.flights.session!!.flight.fuelLeft, 0f)
        g.flow.resume()
        g.frames(1f / 60f)
        assertTrue("one frame on: ${g.time}", g.time > time && g.time < time + 0.03f)
        assertTrue(g.planePosition.dst(place) < 1f)
        assertTrue(bot === g.flights.bot)
    }

    @Test
    fun heldKeysAndTheMouseDoNotMoveTheHandleOfADemonstration() {
        val quiet = game().watch()
        val busy = game().watch()
        repeat(40 * 60) { i ->
            // the spectator holds W, then S, and waves the mouse about
            busy.flights.key(if (i % 120 < 60) "W" else "S", true)
            busy.flights.mouseMoved((i % 720).toFloat(), 720f)
            if (i % 60 == 59) busy.flights.key(if (i % 120 < 60) "W" else "S", false)
            quiet.frames(1f / 60f)
            busy.frames(1f / 60f)
        }
        assertEquals(0f, busy.flights.input.tilt, 0f)
        assertEquals(quiet.time, busy.time, 0f)
        assertTrue("${quiet.planePosition} and ${busy.planePosition}", quiet.planePosition.epsilonEquals(busy.planePosition, 1e-4f))
        assertTrue("the handle keys are taken", busy.flights.key("W", true))
        assertFalse(busy.flights.key("Q", true))
    }

    @Test
    fun mainMenuEndsTheDemonstrationAndTheNextFlightIsTheirs() {
        val g = game().watch()
        g.frames(5f)
        g.flow.pause()
        g.flow.mainMenu()
        g.frames(1f / 60f)
        assertNull(g.flights.session)
        assertNull(g.flights.bot)
        assertFalse(g.flights.demonstration)
        // a key held as it ended does not carry over
        g.flow.f2bPattern()
        g.flow.chooseAssist(AssistLevel.PRO)
        g.flow.choosePlane()
        g.frames(1f / 60f)
        assertEquals(Control.HUMAN, (g.flow.screen as Screen.Flying).control)
        assertNull(g.flights.bot)
        assertEquals(0f, g.flights.input.tilt, 0f)
        g.flights.key("W", true)
        g.frames(0.2f)
        assertEquals(1f, g.flights.session!!.rig.tilt, 0f)
    }

    @Test
    fun retryStartsAFreshDemonstrationFromRest() {
        val g = game().watch()
        g.frames(30f)
        val first = g.flights.session
        val bot = g.flights.bot
        val judge = g.flights.judge
        val report = net.nevinsky.abyssus.app.game.controlline.flow.reportOf(first!!, (g.flow.screen as Screen.Flying).mode, judge, "Stunter")
        assertNull("still flying", report)
        // the attempt ends as a crash would
        g.flow.flightEnded(net.nevinsky.abyssus.app.game.controlline.flow.FlightReport("Stunter", FlightEnd.CRASHED, g.time,
            FlightResult.Pattern(AssistLevel.MID, judge!!.sheet, judge.recording("Stunter", AssistLevel.MID))))
        assertTrue(g.flow.screen is Screen.GameOver)
        g.flow.retry()
        g.frames(1f / 60f)
        assertNotSame(first, g.flights.session)
        assertNotSame(bot, g.flights.bot)
        assertNotSame(judge, g.flights.judge)
        assertEquals(BotPhase.TAKEOFF, g.flights.bot!!.planner.phase)
        assertTrue("from rest: ${g.time}", g.time < 0.05f)
        assertEquals(360f, g.flights.session!!.flight.fuelLeft, 0.05f)
        assertEquals(Control.AI, (g.flow.screen as Screen.Flying).control)
    }

    @Test
    fun aWholeDemonstrationEndsOnGameOverWithItsSheetAndDebriefAndSavesNothing() {
        val g = game()
        // a board with an entry already
        g.flow.patternTable.add(AssistLevel.MID, PatternScoreEntry("Ann", "Stunter", 12.5, 300f, "2026-10-19T12:00:00Z", List(15) { 0.0 }), null)
        val dir = temp.root.toPath()
        val boards = Files.readAllBytes(dir.resolve("f2b-scores.json"))
        val recordings = dir.resolve("f2b-flights").toFile().list()?.sorted()

        g.watch()
        var frames = 0
        while (g.flow.screen is Screen.Flying && frames++ < 60 * 430) g.frames(1f / 60f)
        val over = g.flow.screen as Screen.GameOver
        assertEquals(Control.AI, over.flying.control)
        assertNull(over.rank)
        assertEquals(FlightEnd.LANDED, over.report.end)
        val pattern = over.report.result as FlightResult.Pattern
        for (figure in SCHEDULE) assertTrue(figure.label, (pattern.sheet[figure]?.tenths ?: 0) >= 10)
        assertTrue(Files.readAllBytes(dir.resolve("f2b-scores.json")).contentEquals(boards))
        assertEquals(recordings, dir.resolve("f2b-flights").toFile().list()?.sorted())
        assertEquals(1, g.flow.patternTable.board(AssistLevel.MID).size)
        // the plane comes to rest on GAME OVER; the debrief replays this flight from memory and returns
        g.frames(1f)
        g.flow.debrief()
        val debrief = g.flow.screen as Screen.Debrief
        assertTrue(debrief.model.recording === pattern.recording)
        g.flow.back()
        assertEquals(over, g.flow.screen)
        assertNotNull(g.flights.session)
    }
}
