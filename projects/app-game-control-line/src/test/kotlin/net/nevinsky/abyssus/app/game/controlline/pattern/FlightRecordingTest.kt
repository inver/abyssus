/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A short flight, a crash after the loops, with the inverted laps left out: the recordings of the debrief tests. */
fun recordedFlight(level: AssistLevel = AssistLevel.PRO): FlightRecording {
    val flight = SyntheticSchedule()
    flight.takeoff()
    flight.figure { flight.tracks.reverseWingover(it) }
    flight.figure { flight.tracks.loops(it, 3) }
    flight.figure(pauseInverted = false) { flight.tracks.loops(it, 3, inverted = true) }
    flight.pause(2f)
    flight.end(FlightEnd.CRASHED)
    return flight.judge.recording("Stunter", level)
}

class FlightRecordingTest {
    @Test
    fun aTenSecondFlightGivesThreeHundredSamples() {
        val flight = SyntheticSchedule()
        flight.takeoff()
        flight.fillTo(11f)
        val judge = PatternJudge(18f)
        flight.flight.samples.filter { it.time < 10f }.forEach(judge::sample)
        val recording = judge.recording("Stunter", AssistLevel.PRO)
        assertTrue("${recording.count} samples", recording.count in 299..301)
        assertEquals(0f, recording.time(0), 0f)
        assertEquals(1f / 30f, recording.time(1), 0.002f)
    }

    @Test
    fun everyJudgedManoeuvreHasItsSpanInsideTheSamples() {
        val recording = recordedFlight()
        val first = recording.time(0)
        val last = recording.time(recording.count - 1)
        assertTrue(recording.judgements.isNotEmpty())
        for (j in recording.judgements) {
            assertTrue("${j.figure}: ${j.start}..${j.end} in $first..$last", j.start >= first && j.end <= last + 0.05f && j.start <= j.end)
        }
        val loops = checkNotNull(recording.judgement(Figure.INSIDE_LOOPS.ordinal))
        assertEquals(100, loops.tenths)
        assertTrue(loops.end - loops.start > 5f)
        assertTrue(loops.ideal.size >= 3 * 30)
        assertEquals(0, loops.ideal.size % 3)
        assertEquals("Stunter", recording.plane)
        assertEquals(18f, recording.lineLength, 0f)
    }

    @Test
    fun anOmittedManoeuvreHasAReasonAndNoIdealTrack() {
        val omitted = checkNotNull(recordedFlight().judgement(Figure.INVERTED_LAPS.ordinal))
        assertEquals(0, omitted.tenths)
        assertEquals(ZeroReason.OMITTED, omitted.reason)
        assertEquals(0, omitted.ideal.size)
        assertNotNull(omitted.deductions)
        assertTrue(omitted.deductions.isEmpty())
    }

    @Test
    fun theIdealTrackIsThinnedToAQuarterOfAMetre() {
        val loops = checkNotNull(recordedFlight().judgement(Figure.INSIDE_LOOPS.ordinal))
        var gaps = 0
        // every gap but the last, which ends the track wherever it falls
        for (i in 1 until loops.ideal.size / 3 - 1) {
            val d = Math.sqrt((0..2).sumOf { k -> Math.pow((loops.ideal[i * 3 + k] - loops.ideal[(i - 1) * 3 + k]).toDouble(), 2.0) })
            assertTrue("gap $d", d >= 0.2)
            gaps++
        }
        assertTrue(gaps > 100)
    }
}
