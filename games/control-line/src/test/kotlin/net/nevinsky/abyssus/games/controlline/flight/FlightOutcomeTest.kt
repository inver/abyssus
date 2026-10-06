/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightOutcomeTest {
    /**
     * Holds the plane in the air for a moment (so it has flown), then puts it [drop] metres above where it sits on the
     * ground, upright, coming straight down at [sinkSpeed].
     */
    private fun FieldFlight.touchDown(sinkSpeed: Float, drop: Float = 0.02f) {
        val body = world.bodyOf(plane)!!
        val ground = Vector3(position)
        val rotation = Quaternion(rotation)
        body.setPose(Vector3(ground).add(0f, 1f, 0f), rotation)
        step()
        step()
        body.setPose(Vector3(ground).add(0f, drop, 0f), rotation)
        body.setVelocity(Vector3(0f, -sinkSpeed, 0f))
    }

    @Test
    fun fullDownFromLevelFlightCrashes() = FieldFlight("Stunter").use { f ->
        f.fly(10f)
        assertNull("level flight ended: ${f.flight.result}", f.flight.result)
        f.fly(3f, tilt = -1f)
        assertEquals(FlightEnd.CRASHED, f.flight.result)
    }

    @Test
    fun aGentleUprightTouchAfterTheEngineStopsLands() = FieldFlight("Trainer") { fuelTime = 0f }.use { f ->
        f.touchDown(sinkSpeed = 2f)
        f.fly(0.5f)
        assertEquals(FlightEnd.LANDED, f.flight.result)
    }

    @Test
    fun aGentleTouchWithTheEngineRunningGoesOn() = FieldFlight("Trainer").use { f ->
        f.touchDown(sinkSpeed = 2f)
        f.fly(0.5f)
        assertNull(f.flight.result)
    }

    @Test
    fun aHardTouchCrashes() = FieldFlight("Trainer") { fuelTime = 0f }.use { f ->
        f.touchDown(sinkSpeed = 6f)
        f.fly(0.5f)
        assertEquals(FlightEnd.CRASHED, f.flight.result)
    }

    @Test
    fun anUpsideDownTouchCrashes() = FieldFlight("Trainer") { fuelTime = 0f }.use { f ->
        val body = f.world.bodyOf(f.plane)!!
        body.setPose(Vector3(f.position).add(0f, 0.5f, 0f), Quaternion(f.rotation).mul(Quaternion(Vector3.Z, 180f)))
        f.fly(1f)
        assertEquals(FlightEnd.CRASHED, f.flight.result)
    }

    @Test
    fun twoSecondsOfSlackLinesInTheAirCrash() = FieldFlight("Trainer") { thrust = 0f }.use { f ->
        // inside the lines' reach, thrown up: the lines stay slack for its whole time in the air
        val body = f.world.bodyOf(f.plane)!!
        body.setPose(Vector3(f.rig.handle).add(8f, 1.5f, 0f), Quaternion(f.rotation))
        body.setVelocity(Vector3(0f, 12f, 0f))
        f.fly(1.9f)
        assertNull(f.flight.result)
        f.fly(0.3f)
        assertEquals(FlightEnd.LINES_SLACK, f.flight.result)
        assertEquals("Lines went slack", f.flight.result!!.label)
    }

    @Test
    fun theImpactSpeedPicksTheCrash() {
        for ((sinkSpeed, severity) in listOf(4.5f to CrashSeverity.LITTLE, 8f to CrashSeverity.MEDIUM, 14f to CrashSeverity.FULL)) {
            FieldFlight("Trainer").use { f ->
                f.touchDown(sinkSpeed)
                f.fly(0.5f)
                assertEquals("at $sinkSpeed m/s", FlightEnd.CRASHED, f.flight.result)
                assertEquals("at $sinkSpeed m/s", sinkSpeed, f.flight.impactSpeed, 0.5f)
                assertEquals("at $sinkSpeed m/s", severity, f.flight.crash)
            }
        }
    }

    @Test
    fun aLandingIsNoCrash() = FieldFlight("Trainer") { fuelTime = 0f }.use { f ->
        f.touchDown(sinkSpeed = 2f)
        f.fly(0.5f)
        assertEquals(FlightEnd.LANDED, f.flight.result)
        assertNull(f.flight.crash)
        assertEquals(0f, f.flight.impactSpeed, 0f)
    }
}
