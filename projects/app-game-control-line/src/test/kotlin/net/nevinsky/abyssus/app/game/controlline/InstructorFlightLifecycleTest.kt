/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
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

/** An instructed flight through the game's own flight lifecycle, as `ControlLineGame` runs it, without a window. */
class InstructorFlightLifecycleTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val runners = ArrayList<FlightRunner>()

    @After
    fun closeRunners() = runners.forEach(FlightRunner::close)

    private inner class Game(instructed: Boolean) {
        val dir = temp.root.toPath()
        val flow = GameFlow(
            loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) },
            ScoreTable(dir.resolve("scores.json")), PatternScoreTable(dir.resolve("f2b-scores.json")), ScaleScoreTable(dir.resolve("scale-scores.json")),
        ) { Instant.parse("2026-10-20T12:00:00Z") }
        val flights = FlightRunner(flow, { FieldScene(loadField()) }, { PhysicsAssets(bundledProject().toFile()) }, failOnWarnings(), JoltNatives())
            .also { runners += it }

        init {
            flow.freeFlight()
            if (instructed) flow.toggleInstructor()
            flow.choosePlane()
            flights.follow()
        }

        fun frames(seconds: Float) = repeat(Math.round(seconds * 60f)) {
            flights.follow()
            flights.advance(1f / 60f)
        }
    }

    @Test
    fun aFlightWithoutTheInstructorHasNone() {
        assertNull(Game(instructed = false).flights.instructor)
    }

    @Test
    fun keysAndTheMouseStillReachTheHandleOfAnInstructedFlight() {
        val g = Game(instructed = true)
        assertNotNull(g.flights.instructor)
        assertTrue(g.flights.key("W", true))
        g.frames(1f)
        assertTrue("player tilt ${g.flights.instructor!!.playerTilt}", g.flights.instructor!!.playerTilt > 0.5f)
        assertEquals(g.flights.input.tilt, g.flights.instructor!!.playerTilt, 1e-4f)
    }

    @Test
    fun pausingFreezesTheInstructor() {
        val g = Game(instructed = true)
        // the handle full down from the start of the take-off roll: the plane gets into trouble in the air
        g.flights.key("S", true)
        g.frames(30f)
        val instructor = g.flights.instructor!!
        g.flow.pause()
        val state = Triple(instructor.share, instructor.danger, g.flights.session!!.flight.time)
        g.frames(5f)
        assertEquals(state, Triple(instructor.share, instructor.danger, g.flights.session!!.flight.time))
    }
}
