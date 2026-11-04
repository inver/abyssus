/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.ai.DangerGauge
import net.nevinsky.abyssus.app.game.controlline.ai.Handling
import net.nevinsky.abyssus.app.game.controlline.ai.InstructorControl
import net.nevinsky.abyssus.app.game.controlline.ai.RescueSource
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.flight.Takeover
import net.nevinsky.abyssus.app.game.controlline.flow.FlightReport
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.state.UiWrite
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The instructor's toggle, HUD values and GAME OVER row as the screens publish them, without graphics. */
class InstructorUiModelTest {
    @get:Rule val temp = TemporaryFolder()

    private val planes =
        loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) }
    private val flow by lazy {
        GameFlow(
            planes,
            ScoreTable(temp.root.toPath().resolve("s.json")),
            PatternScoreTable(temp.root.toPath().resolve("p.json")),
            ScaleScoreTable(temp.root.toPath().resolve("sc.json")),
        )
    }
    private val model by lazy { GameUiModel(flow) {} }

    private val stunter =
        PlaneComponent().apply {
            lineLength = 18f
            wingArea = 0.325f
            liftSlope = 8f
            elevatorEffect = 3f
        }
    private var student = 0f
    private var rescue = 0f

    private val instructor =
        InstructorControl(
            FlightControlSource { student },
            DangerGauge(Handling(stunter, 1.1f)),
            object : RescueSource {
                override fun tilt(observation: FlightObservation) = rescue

                override fun release() = Unit
            },
        )

    private var time = 10f

    private fun observation(height: Float, vy: Float = 0f) =
        FlightObservation(
                time,
                Vector3(18f, height, 0f),
                Vector3(0f, vy, -22f),
                Vector3(),
                Vector3(0f, 0f, -1f),
                Vector3(0f, 1f, 0f),
                height,
                25f,
                100f,
                true,
                18f,
                null,
            )
            .also { time += 1f / 120f }

    private fun values(instructor: InstructorControl? = null) = model.snapshot(null, null, 0f, null, instructor).toMap()

    private fun flag(name: String, instructor: InstructorControl? = null) =
        (values(instructor).getValue(name) as UiValue.Bool).value

    private fun number(name: String, instructor: InstructorControl? = null) =
        (values(instructor).getValue(name) as UiValue.Num).value

    private fun text(name: String, instructor: InstructorControl? = null) =
        (values(instructor).getValue(name) as UiValue.Text).value

    private fun flying() {
        flow.freeFlight()
        flow.toggleInstructor()
        flow.choosePlane()
    }

    @Test
    fun theCheckboxIsOfferedAndKeptOnPlaneSelectOnly() {
        assertFalse(flag("instructorOffered"))
        flow.freeFlight()
        assertTrue(flag("instructorOffered"))
        assertFalse(flag("instructorOn"))
        assertTrue(model.key(Input.Keys.I))
        assertTrue(flag("instructorOn"))
        flow.choosePlane()
        assertFalse(flag("instructorOffered"))
        assertTrue("kept for the flight", flag("instructorOn"))
        assertFalse("not a key outside plane select", model.key(Input.Keys.I))
    }

    @Test
    fun theCheckboxWritesAndEnterOnItTogglesInsteadOfFlying() {
        flow.freeFlight()
        assertTrue(model.write(UiWrite("instructorOn", UiValue.Bool(true), "ps-instructor")) is UiWriteResult.Accepted)
        assertTrue(flow.instructor)
        assertTrue(model.write(UiWrite("instructorOn", UiValue.Bool(false), "ps-instructor")) is UiWriteResult.Accepted)
        assertFalse(flow.instructor)
        assertTrue(model.key(Input.Keys.ENTER, focus = INSTRUCTOR_CHECKBOX))
        assertTrue(flow.instructor)
        assertTrue("still on plane select", flow.screen is Screen.PlaneSelect)
        flow.choosePlane()
        assertTrue(model.write(UiWrite("instructorOn", UiValue.Bool(false), "ps-instructor")) is UiWriteResult.Rejected)
    }

    @Test
    fun aSafeFlightShowsOnlyThePlayersMarker() {
        flying()
        student = 0.4f
        instructor.tilt(observation(1.5f))
        assertTrue(flag("isInstructed", instructor))
        assertEquals(0.7, number("handleTilt", instructor), 1e-6)
        assertFalse(flag("ghostVisible", instructor))
        assertEquals("", text("ghostCue", instructor))
        assertFalse(flag("instructorHolds", instructor))
    }

    @Test
    fun aWarningShowsTheGhostAndTheCueBeforeAnyTakeover() {
        flying()
        student = -1f
        rescue = 0.5f
        instructor.tilt(observation(1.5f))
        // a danger of about 0.2: under half the handle
        instructor.tilt(observation(1.5f, vy = -3f))
        assertTrue("danger ${instructor.danger}, share ${instructor.share}", instructor.ghost)
        assertFalse(instructor.holding)
        assertTrue(flag("ghostVisible", instructor))
        assertEquals(0.75, number("ghostTilt", instructor), 1e-6)
        assertEquals("Up!", text("ghostCue", instructor))
        assertFalse(flag("instructorHolds", instructor))
    }

    @Test
    fun aTakeoverShowsTheFlagAndTheCueFollowsTheThreshold() {
        flying()
        student = -1f
        rescue = 0.5f
        instructor.tilt(observation(1.5f))
        instructor.tilt(observation(0.4f, vy = -8f))
        assertTrue(flag("instructorHolds", instructor))
        assertEquals("Up!", text("ghostCue", instructor))
        rescue = -0.9f
        instructor.tilt(observation(0.4f, vy = -8f))
        assertEquals("a gap of 0.1 is no cue", "", text("ghostCue", instructor))
        rescue = -1f
        student = 0f
        instructor.tilt(observation(0.4f, vy = -8f))
        assertEquals("Down!", text("ghostCue", instructor))
    }

    @Test
    fun noMarkersWithoutAnInstructorOrOutsideAFlight() {
        assertFalse(flag("isInstructed"))
        flying()
        assertFalse(flag("isInstructed"))
        instructor.tilt(observation(0.4f, vy = -8f))
        flow.pause()
        flow.mainMenu()
        assertFalse("the main menu shows none", flag("isInstructed", instructor))
        assertFalse(flag("ghostVisible", instructor))
    }

    @Test
    fun gameOverSaysTheInstructorWasNotNeededOrHowOftenItTookOver() {
        flying()
        val flying = flow.screen as Screen.Flying
        fun facts() =
            (values().getValue("facts") as UiValue.Rows).items.associate {
                (it.getValue("name") as UiValue.Text).value to (it.getValue("value") as UiValue.Text).value
            }
        val untouched = FlightReport(flying.plane.name, FlightEnd.LANDED, 100f, FlightResult.Free(0, 0, 1))
        flow.flightEnded(untouched)
        // an empty table takes any score
        flow.nameEntered("Ann")
        assertTrue(flow.screen is Screen.GameOver)
        assertEquals("Not needed", facts()["Instructor"])

        flow.retry()
        val assisted = untouched.copy(takeovers = listOf(Takeover(5f, 6f), Takeover(9f, 10f)))
        flow.flightEnded(assisted)
        assertEquals("Took over 2 times, not saved", facts()["Instructor"])
    }

    @Test
    fun aFlightWithoutTheInstructorHasNoInstructorRow() {
        flow.freeFlight()
        flow.choosePlane()
        flow.flightEnded(FlightReport("Stunter", FlightEnd.LANDED, 100f, FlightResult.Free(0, 0, 1)))
        val names =
            (values().getValue("facts") as UiValue.Rows).items.map { (it.getValue("name") as UiValue.Text).value }
        assertFalse("Instructor" in names)
    }
}
