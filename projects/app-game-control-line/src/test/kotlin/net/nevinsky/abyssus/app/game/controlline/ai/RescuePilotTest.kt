/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.pattern.place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which course the rescue picks, from observations made up for it. */
class RescuePilotTest {
    private val radius = 18f
    private val stunter = PlaneComponent().apply { lineLength = radius; wingArea = 0.325f; liftSlope = 8f; elevatorEffect = 3f }
    private val rescue = rescuePilot(radius, Handling(stunter, 1.1f))

    private fun east(azimuth: Float): Vector3 {
        val a = Math.toRadians(azimuth.toDouble()).toFloat()
        return Vector3(-kotlin.math.sin(a), 0f, -kotlin.math.cos(a))
    }

    /** A plane at the base at [azimuth], flying anticlockwise upright, or clockwise inverted. */
    private fun observation(time: Float = 10f, clockwise: Boolean = false, engine: Boolean = true, azimuth: Float = 30f): FlightObservation {
        val u = place(0f, azimuth)
        val heading = east(azimuth).also { if (clockwise) it.scl(-1f) }
        val up = Vector3(u).crs(heading)
        return FlightObservation(
            time, Vector3(u).scl(radius).add(0f, BASE_HEIGHT, 0f), Vector3(heading).scl(22f), Vector3(), heading, up, 1.5f, 25f,
            if (engine) 100f else 0f, engine, radius, null,
        )
    }

    @Test
    fun anUprightPlaneWithTheEngineRunningFliesTheBaseAnticlockwise() {
        rescue.tilt(observation())
        val course = rescue.course as Parallel
        assertEquals(0f, course.latitude, 0f)
        assertTrue(!course.clockwise)
        assertEquals(RescuePhase.LEVEL, rescue.phase)
    }

    @Test
    fun aClockwiseInvertedPlaneKeepsFlyingClockwiseAndStaysInverted() {
        rescue.tilt(observation(clockwise = true))
        assertTrue((rescue.course as Parallel).clockwise)
    }

    @Test
    fun withTheEngineStoppedAnUprightPlaneGlidesDown() {
        rescue.tilt(observation(engine = false))
        assertTrue(rescue.course is PathCourse)
        assertEquals(RescuePhase.GLIDE, rescue.phase)
    }

    @Test
    fun withTheEngineStoppedAnInvertedPlaneTurnsOverFirst() {
        rescue.tilt(observation(engine = false, clockwise = true))
        assertEquals(RescuePhase.TURN_OVER, rescue.phase)
    }

    @Test
    fun theCourseIsKeptUntilReleasedAndThenBuiltAgain() {
        rescue.tilt(observation())
        val first = rescue.course
        rescue.tilt(observation(time = 10.1f))
        assertSame(first, rescue.course)
        rescue.release()
        assertNull(rescue.course)
        rescue.tilt(observation(time = 12f))
        assertNotSame(first, rescue.course)
    }

    @Test
    fun aGlideThatDoesNotMoveOnForThreeSecondsIsBuiltAgainFromThePlane() {
        rescue.tilt(observation(time = 10f, engine = false))
        val first = rescue.course
        // the plane stays where it was: no progress along the glide
        rescue.tilt(observation(time = 11f, engine = false))
        assertSame(first, rescue.course)
        rescue.tilt(observation(time = 13.5f, engine = false, azimuth = 30f))
        assertNotSame(first, rescue.course)
    }
}
