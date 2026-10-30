/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.SCHEDULE
import net.nevinsky.abyssus.app.game.controlline.pattern.ZeroReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A plane the bot is not made for flies honestly: the `Stunter` on 15 m lines instead of 18 crashes in its triangles.
 * The flight ends there, as any crash ends one; the marks before it stand and the rest score 0 for the crash. Nothing
 * restarts it or reports a whole routine.
 */
class F2bAiFailureTest {
    @Test
    fun aStunterOnShorterLinesCrashesAndKeepsWhatItFlew() = AiFlight(change = { lineLength = 15f }).use { f ->
        f.fly()
        val report = f.describe()
        assertEquals(report, FlightEnd.CRASHED, f.result)
        val crashedFrom = SCHEDULE.indexOfFirst { f.entry(it)?.reason == ZeroReason.CRASHED }
        assertTrue(report, crashedFrom > 1)
        for (figure in SCHEDULE.take(crashedFrom)) assertTrue("${figure.label}\n$report", (f.entry(figure)?.tenths ?: 0) >= 10)
        for (figure in SCHEDULE.drop(crashedFrom)) assertEquals("${figure.label}\n$report", ZeroReason.CRASHED, f.entry(figure)?.reason)
        assertTrue(report, f.entry(Figure.LANDING)?.tenths == 0)
        // the flight stays over: more frames neither restart nor move it on
        val time = f.time
        repeat(120) { f.session.advance(1f / 60f) }
        assertEquals(FlightEnd.CRASHED, f.result)
        assertEquals(time, f.time, 0f)
    }
}
