/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** A Scale flight through the game's own flight lifecycle, as `ControlLineGame` runs it, without a window. */
class ScaleFlightLifecycleTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val runners = ArrayList<FlightRunner>()

    @After
    fun closeRunners() = runners.forEach(FlightRunner::close)

    private inner class Game {
        val dir = temp.root.toPath()
        val free = ScoreTable(dir.resolve("scores.json"))
        val scale = ScaleScoreTable(dir.resolve("scale-scores.json"))
        val flow = GameFlow(
            loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) },
            free, PatternScoreTable(dir.resolve("f2b-scores.json")), scale,
        ) { Instant.parse("2026-10-20T12:00:00Z") }
        val flights = FlightRunner(flow, { FieldScene(loadField()) }, { PhysicsAssets(bundledProject().toFile()) }, failOnWarnings(), JoltNatives())
            .also { runners += it }

        /** [seconds] of 60 Hz frames, as the game renders them: the flow first, then the flight. */
        fun frames(seconds: Float) = repeat(Math.round(seconds * 60f)) {
            flights.follow()
            flights.advance(1f / 60f)
        }

        fun flyReplica() {
            flow.scale()
            flow.choosePlane()
            flights.follow()
        }
    }

    @Test
    fun aScaleFlightFliesTheReplicaWithTheRealismJudgeAndNoF2bJudge() {
        val g = Game()
        g.flyReplica()
        assertEquals(Mode.Scale, (g.flow.screen as Screen.Flying).mode)
        assertNotNull(g.flights.scaleJudge)
        assertNull(g.flights.judge)
        assertEquals("Mig-3", g.flights.flying!!.plane.name)
        g.frames(40f)
        assertTrue("laps ${g.flights.scaleJudge!!.laps}", g.flights.scaleJudge!!.laps >= 3)
        assertEquals("a replica scores no Free laps", 0, g.flights.session!!.flight.scoring.keeper.laps.coerceAtMost(0))
    }

    @Test
    fun aCrashEndsTheFlightReportsTheMarksAndEntersTheScaleBoardOnly() {
        val g = Game()
        g.flyReplica()
        g.frames(35f)
        val judge = checkNotNull(g.flights.scaleJudge)
        // the handle goes full down and stays there: the plane dives into the ground
        g.flights.key("S", true)
        var frames = 0
        while (g.flow.screen is Screen.Flying && frames++ < 120 * 60) g.frames(1f / 60f)
        val screen = g.flow.screen
        assertTrue("the flight did not end: $screen", screen is Screen.NameEntry)
        val report = (screen as Screen.NameEntry).report
        val result = report.result as FlightResult.Scale
        assertEquals(judge.marks, result.marks)
        assertEquals("a crash scores no landing", 0, result.marks.landing)
        assertTrue("the flew laps first: ${result.laps}", result.laps >= 2)

        g.flow.nameEntered("Ann")
        val entry = g.scale.entries.single()
        assertEquals("Ann", entry.name)
        assertEquals("Mig-3", entry.plane)
        assertEquals("MiG-3", entry.prototype)
        assertEquals(result.score, entry.score)
        assertTrue(g.free.entries.isEmpty())
    }

    @Test
    fun aFreeFlightAfterwardsHasNoScaleJudge() {
        val g = Game()
        g.flyReplica()
        g.frames(1f)
        g.flow.pause()
        g.flow.mainMenu()
        g.flights.follow()
        g.flow.freeFlight()
        g.flow.choosePlane()
        g.flights.follow()
        assertEquals(Mode.Free, (g.flow.screen as Screen.Flying).mode)
        assertNull(g.flights.scaleJudge)
    }
}
