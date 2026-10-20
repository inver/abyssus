/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.pattern.FLIGHT_TIME
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bot's take-off and landing with real physics and the bundled, unchanged `Stunter`: from rest, on the base until
 * the engine stops under the normal fuel rule at 360 s, then the glide to an upright touchdown inside the flying circle:
 * where the lines meet the plane, within their length and 0.5 m of the pilot.
 */
class F2bAiLandingTest {
    @Test
    fun fromRestToTheEngineStoppingAndALandingInsideTheCircle() = AiFlight(schedule = listOf(Figure.TAKEOFF, Figure.LANDING)).use { f ->
        f.fly()
        val report = f.describe()
        assertEquals(report, FlightEnd.LANDED, f.result)
        assertTrue(report, f.time <= FLIGHT_TIME)
        val stopped = checkNotNull(f.engineStopped) { report }
        assertEquals("the engine stops at the scene's fuel time\n$report", 360f, stopped, 0.05f)
        assertTrue("touched down after the engine stopped\n$report", f.time > stopped)
        // the lines meet the plane within their length and 0.5 m of the pilot; its centre of gravity is 0.75 m further out
        assertTrue("line ends within the line length and 0.5 m\n$report", f.lineEnds <= 18.5f)
        assertTrue("upright\n$report", f.lastSample?.inverted == false)
        assertTrue("never slack in the air\n$report", f.minTension >= 1f)
        // the judge's own reading of it
        val landing = f.entry(Figure.LANDING)
        assertNull(report, landing?.reason)
        assertTrue(report, (landing?.tenths ?: 0) >= 10)
        assertTrue(report, (f.entry(Figure.TAKEOFF)?.tenths ?: 0) >= 10)
        assertEquals(report, listOf(BotPhase.TAKEOFF, BotPhase.FUEL_WAIT, BotPhase.GLIDE), f.phases.map { it.first })
    }
}
