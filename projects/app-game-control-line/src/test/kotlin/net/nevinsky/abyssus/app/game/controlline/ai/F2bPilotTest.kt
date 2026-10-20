/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.pattern.LOOP_RADIUS
import net.nevinsky.abyssus.app.game.controlline.pattern.circleArc
import net.nevinsky.abyssus.app.game.controlline.pattern.meridian
import net.nevinsky.abyssus.app.game.controlline.pattern.place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tracker on observations made up for it: which way it moves the handle, and that it stays finite. */
class F2bPilotTest {
    private val radius = 18f

    /** The bundled `Stunter`'s settings. */
    private val stunter = PlaneComponent().apply { lineLength = radius; wingArea = 0.325f; liftSlope = 8f; elevatorEffect = 3f }
    private val pilot = F2bPilot(Handling(stunter, 1.1f))

    /**
     * A plane at [u] flying along [heading] (unit, tangent) at [speed]: its up across the lines, `u x heading`, as the
     * physics keeps it.
     */
    private fun observation(u: Vector3, heading: Vector3, speed: Float = 22f): FlightObservation = FlightObservation(
        10f, Vector3(u).scl(radius).add(0f, BASE_HEIGHT, 0f), Vector3(heading).scl(speed), Vector3(),
        Vector3(heading), Vector3(u).crs(heading), 1.5f, 25f, 300f, true, radius, null,
    )

    private fun east(azimuth: Float): Vector3 {
        val a = Math.toRadians(azimuth.toDouble()).toFloat()
        return Vector3(-kotlin.math.sin(a), 0f, -kotlin.math.cos(a))
    }

    @Test
    fun anUprightPlaneBelowItsCoursePullsUp() {
        val tilt = pilot.tilt(observation(place(0f, 30f), east(30f)), Parallel(radius, 8f))
        assertTrue("tilt $tilt", tilt > 0.05f)
    }

    @Test
    fun anInvertedPlaneBelowItsCoursePushesUp() {
        // flying clockwise its up points to the ground, so climbing is a push
        val tilt = pilot.tilt(observation(place(0f, 30f), Vector3(east(30f)).scl(-1f)), Parallel(radius, 8f, clockwise = true))
        assertTrue("tilt $tilt", tilt < -0.05f)
    }

    @Test
    fun onItsCourseLevelThePlaneHoldsNearlyNeutral() {
        val tilt = pilot.tilt(observation(place(0f, 30f), east(30f)), Parallel(radius, 0f))
        assertEquals(0f, tilt, 0.1f)
        assertEquals(0f, pilot.crossTrack, 1e-3f)
    }

    @Test
    fun crossingTheZenithOnAMeridianStaysFiniteAndMovesOn() {
        val course = PathCourse(radius, meridian(40f, 60f, 90f).points + meridian(220f, 90f, 60f).points)
        var last = -1
        for (latitude in listOf(80f, 88f, 89.9f, 91.5f, 95f, 100f)) {
            // up the meridian at 40 and on over the top, down the one at 220
            val u = place(latitude, 40f)
            val heading = place(latitude + 90f, 40f)
            val tilt = pilot.tilt(observation(u, heading, 15f), course)
            assertTrue("tilt at $latitude: $tilt", tilt.isFinite() && kotlin.math.abs(tilt) <= 1f)
            assertEquals("cross track at $latitude", 0f, pilot.crossTrack, 0.05f)
            assertTrue("progress at $latitude", course.progress >= last)
            last = course.progress
        }
        assertTrue("past the zenith", course.travelled > course.length / 2f)
    }

    @Test
    fun threeLoopsAreFollowedOneAfterAnotherNeverJumpingToALaterOne() {
        val centre = place(LOOP_RADIUS, 0f)
        val bottom = place(0f, 0f)
        val loop = circleArc(centre, LOOP_RADIUS, bottom, 360f).points
        val course = PathCourse(radius, loop + loop + loop)
        val perLoop = course.length / 3f
        var last = 0f
        for (lap in 0 until 3) for (i in 1 until loop.size step 20) {
            val u = loop[i]
            val heading = tangentAt(u, Vector3(loop[minOf(i + 1, loop.size - 1)]).sub(loop[i - 1]))
            pilot.tilt(observation(u, heading), course)
            val travelled = course.travelled
            assertTrue("progress went back at loop ${lap + 1}, point $i", travelled >= last)
            assertTrue("loop ${lap + 1}, point $i matched at $travelled m", travelled >= lap * perLoop - 1f && travelled <= (lap + 1) * perLoop + 1f)
            last = travelled
        }
        assertTrue("reached the third loop", course.travelled > 2f * perLoop)
    }

    @Test
    fun aLoopWantsALeftTurnWhoseTiltLiesWithinTheHandle() {
        val centre = place(LOOP_RADIUS, 0f)
        val loop = circleArc(centre, LOOP_RADIUS, place(0f, 0f), 360f).points
        val course = PathCourse(radius, loop)
        val u = loop[40]
        val tilt = pilot.tilt(observation(u, tangentAt(u, Vector3(loop[41]).sub(loop[39]))), course)
        assertTrue("curvature ${pilot.curvature}", pilot.curvature > 0.05f)
        assertTrue("tilt $tilt", tilt > 0f && tilt < 1f)
    }

    @Test
    fun belowControlSpeedOrWithoutAPlaceTheHandleIsNeutral() {
        assertEquals(0f, pilot.tilt(observation(place(-4f, 0f), east(0f), CONTROL_SPEED - 1f), Parallel(radius, 0f)), 0f)
        val nowhere = FlightObservation(0f, Vector3(0f, BASE_HEIGHT, 0f), Vector3(20f, 0f, 0f), Vector3(), Vector3.Z, Vector3.Y, 1.5f, 0f, 1f, true, radius, null)
        assertEquals(0f, pilot.tilt(nowhere, Parallel(radius, 0f)), 0f)
        val broken = observation(place(0f, 0f), east(0f)).let {
            FlightObservation(it.time, it.offset, Vector3(Float.NaN, 0f, 0f), it.angularVelocity, it.nose, it.up, it.height, it.tension, it.fuelLeft, true, radius, null)
        }
        assertEquals(0f, pilot.tilt(broken, Parallel(radius, 0f)), 0f)
    }

    @Test
    fun theWantedCurvatureNeverPassesTheStall() {
        // far off a course that turns hard: the wanted turn is clamped to what the wing gives
        val tilt = pilot.tilt(observation(place(0f, 0f), east(0f)), Parallel(radius, 60f))
        assertTrue(kotlin.math.abs(pilot.curvature) <= Handling(stunter, 1.1f).maxCurvature + 1e-6f)
        assertTrue(tilt <= 1f)
    }
}
