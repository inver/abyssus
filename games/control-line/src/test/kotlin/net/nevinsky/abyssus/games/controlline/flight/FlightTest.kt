/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import net.nevinsky.abyssus.games.controlline.track.wrapDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** The flight scenarios of `control-line-flight` on the bundled field's planes, as tuned. */
class FlightTest {
    /** The average speed over [seconds] after [settle] seconds of level flight with the handle neutral. */
    private fun steadySpeed(f: FieldFlight, settle: Float = 20f, seconds: Float = 10f): Float {
        f.fly(settle)
        var sum = 0.0
        var n = 0
        f.fly(seconds) { sum += f.speed; n++ }
        assertNull(f.flight.result)
        return (sum / n).toFloat()
    }

    @Test
    fun trainerTakesOffWithinFiveSeconds() = FieldFlight("Trainer").use { f ->
        var airborneAt: Float? = null
        var t = 0f
        f.fly(5f) {
            t += 1f / 120f
            if (airborneAt == null && f.height > 1f) airborneAt = t
        }
        assertTrue("Trainer was not above 1 m within 5 s", airborneAt != null)
        val distance = f.position.cpy().sub(f.rig.handle).len()
        assertTrue("off its lines: $distance m from the handle", distance > f.settings.lineLength - 0.5f)
        assertTrue("tension ${f.flight.tension}", f.flight.tension >= CONTROL_TENSION)
    }

    @Test
    fun stunterLoopsOnFullUpWithinOneLap() = FieldFlight("Stunter").use { f ->
        f.fly(12f)
        assertNull(f.flight.result)
        val start = f.rig.azimuthOfPlane()
        var turned = 0f
        var last = start
        var vertical = false
        var overTheTop = false
        f.rig.tilt = 1f
        while (turned < 360f && !overTheTop && !f.flight.finished) {
            f.step()
            val azimuth = f.rig.azimuthOfPlane()
            turned += wrapDegrees(azimuth - last)
            last = azimuth
            val climb = f.flight.lastSample!!.climb
            if (climb >= 90f) vertical = true
            // over the top: flying back along the circle, upside down, above the ground
            if (vertical && abs(climb) > 170f && f.flight.lastSample!!.inverted && f.height > 2f) overTheTop = true
        }
        assertTrue("the climb angle did not pass vertical", vertical)
        assertTrue("the plane did not come over the top within one lap", overTheTop)
    }

    @Test
    fun levelFlightTensionIsCloseToMassTimesSpeedSquaredOverLineLength() = FieldFlight("Trainer").use { f ->
        f.fly(20f)
        val mass = f.world.bodyOf(f.plane)!!.mass
        val ratios = ArrayList<Float>()
        f.fly(10f) { ratios += f.flight.tension / (mass * f.speed * f.speed / f.settings.lineLength) }
        assertNull(f.flight.result)
        val ratio = ratios.average().toFloat()
        assertEquals("tension / (m v² / L)", 1f, ratio, 0.3f)
    }

    @Test
    fun doubledThrustGivesAHigherSteadySpeed() {
        val normal = FieldFlight("Trainer").use { steadySpeed(it) }
        val doubled = FieldFlight("Trainer") { thrust *= 2f }.use { steadySpeed(it) }
        assertTrue("$doubled m/s with doubled thrust, $normal m/s without", doubled > normal + 1f)
    }

    @Test
    fun racersThrustIsZeroAfterSixtySeconds() = FieldFlight("Racer").use { f ->
        assertEquals(60f, f.settings.fuelTime, 0f)
        f.fly(59f)
        assertTrue(f.flight.forces.thrust > 0f)
        f.fly(1.2f)
        var maxThrust = 0f
        f.fly(5f) { maxThrust = max(maxThrust, f.flight.forces.thrust) }
        assertEquals(0f, maxThrust, 0f)
        assertEquals(0f, f.flight.fuelLeft, 0f)
    }

    @Test
    fun trainerFliesTenLevelLapsWithAHeldTrim() = FieldFlight("Trainer").use { f ->
        f.rig.tilt = 0f
        var highest = 0f
        var seconds = 0f
        while (f.flight.scoring.keeper.laps < 10 && !f.flight.finished && seconds < 120f) {
            f.fly(1f) { highest = max(highest, f.flight.lastSample?.elevation ?: 0f) }
            seconds += 1f
        }
        assertNull("the flight ended: ${f.flight.result}", f.flight.result)
        assertEquals(10, f.flight.scoring.keeper.laps)
        assertTrue("climbed to $highest degrees", highest < 20f)
    }
}
