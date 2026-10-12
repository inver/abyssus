/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The judge on whole synthetic flights: the order, repeat, pause, time and crash rules of `control-line-f2b-pattern`. */
class PatternJudgeTest {
    private fun SyntheticSchedule.describe() = judge.sheet.entries.joinToString("\n") {
        if (it == null) "undecided" else "${it.figure.label}: ${if (it.reason == null) it.mark.toString() else "0 ${it.reason.label}"}"
    }

    private fun SyntheticSchedule.entry(figure: Figure): SheetEntry = checkNotNull(judge.sheet[figure]) { "not decided: $figure\n${describe()}" }

    @Test
    fun aPerfectFlightScoresThirteenHundred() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding()
        flight.landing()
        flight.end(FlightEnd.LANDED)
        for (f in SCHEDULE) assertEquals("${f.label}\n${flight.describe()}", 100, flight.entry(f).tenths)
        assertEquals("1300.00", flight.judge.sheet.score)
        assertTrue("flew ${flight.flight.time} s", flight.flight.time < FLIGHT_TIME)
    }

    @Test
    fun theSumOfEightPointFiveTimesTwoAndSevenPointTwoTimesEight() {
        val sheet = ScoreSheet()
        sheet[Figure.TAKEOFF] = SheetEntry(Figure.TAKEOFF, 85, null, null, 20f)
        sheet[Figure.REVERSE_WINGOVER] = SheetEntry(Figure.REVERSE_WINGOVER, 72, null, null, 40f)
        assertEquals("74.60", sheet.score)
        assertEquals(746, sheet.tenths)
        assertEquals("1300.00", formatPoints(13000))
    }

    @Test
    fun invertedLapsLeftOutAreOmittedAndTheOutsideLoopsAreMarked() {
        val flight = SyntheticSchedule()
        flight.takeoff()
        flight.figure { flight.tracks.reverseWingover(it) }
        flight.figure { flight.tracks.loops(it, 3) }
        flight.figure(pauseInverted = false) { flight.tracks.loops(it, 3, inverted = true) }
        flight.pause(2f)
        flight.end(FlightEnd.CRASHED)
        assertEquals(ZeroReason.OMITTED, flight.entry(Figure.INVERTED_LAPS).reason)
        assertEquals(100, flight.entry(Figure.OUTSIDE_LOOPS).tenths)
        assertEquals(100, flight.entry(Figure.INSIDE_LOOPS).tenths)
    }

    @Test
    fun aLoopFlownAgainLaterChangesNothing() {
        val flight = SyntheticSchedule()
        flight.takeoff()
        flight.figure { flight.tracks.reverseWingover(it) }
        flight.figure { flight.tracks.loops(it, 3) }
        flight.figure(pauseInverted = false) { flight.tracks.invertedLaps(it, 2) }
        flight.figure { flight.tracks.loops(it, 3, inverted = true) }
        flight.figure { flight.tracks.squares(it, 2) }
        flight.pause(2f)
        val before = flight.judge.sheet.tenths
        val decided = flight.judge.decided
        flight.figure(pauseInverted = false) { flight.tracks.loops(it, 3) }
        flight.pause(2f)
        assertEquals(before, flight.judge.sheet.tenths)
        assertEquals(decided, flight.judge.decided)
        assertEquals(Figure.OUTSIDE_SQUARES, flight.judge.expected)
    }

    @Test
    fun aThirdSquareScoresWrongNumberOfRepeats() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding(skip = setOf(Figure.INSIDE_SQUARES, Figure.OUTSIDE_SQUARES, Figure.INSIDE_TRIANGLES, Figure.HORIZONTAL_EIGHTS, Figure.SQUARE_EIGHTS, Figure.VERTICAL_EIGHTS, Figure.HOURGLASS, Figure.OVERHEAD_EIGHTS, Figure.CLOVER))
        flight.figure { flight.tracks.squares(it, 3) }
        flight.pause(2f)
        assertEquals(ZeroReason.WRONG_REPEATS, flight.entry(Figure.INSIDE_SQUARES).reason)
        assertEquals(Figure.OUTSIDE_SQUARES, flight.judge.expected)
    }

    @Test
    fun aSquareGivenUpScoresNotCompleted() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding(skip = setOf(Figure.INSIDE_SQUARES, Figure.OUTSIDE_SQUARES, Figure.INSIDE_TRIANGLES, Figure.HORIZONTAL_EIGHTS, Figure.SQUARE_EIGHTS, Figure.VERTICAL_EIGHTS, Figure.HOURGLASS, Figure.OVERHEAD_EIGHTS, Figure.CLOVER))
        // up the first side, then level along the 45 degree parallel for a lap and a half, and back down
        flight.figure {
            val corner = (1f / 18f) / (Math.PI / 180.0).toFloat()
            val edges = listOf(
                parallel(0f, it - 20f, it, Tag.BASE), meridian(it, 0f, 45f), parallel(45f, it, it - 540f),
                meridian(it - 540f, 45f, 0f), parallel(0f, it - 540f, it - 500f, Tag.BASE),
            )
            val b = TrackBuilder()
            b.add(rounded(edges, corner).points)
            b.build()
        }
        flight.pause(2f)
        assertEquals(flight.describe(), ZeroReason.NOT_COMPLETED, flight.entry(Figure.INSIDE_SQUARES).reason)
    }

    @Test
    fun outsideLoopsOneLapAfterTheInvertedLapsAreTooSoon() {
        val flight = SyntheticSchedule()
        flight.takeoff()
        flight.figure { flight.tracks.reverseWingover(it) }
        flight.figure { flight.tracks.loops(it, 3) }
        flight.figure(pauseInverted = false) { flight.tracks.invertedLaps(it, 2) }
        flight.figure(laps = 1f) { flight.tracks.loops(it, 3, inverted = true) }
        flight.pause(2f, inverted = true)
        assertEquals(flight.describe(), 100, flight.entry(Figure.INVERTED_LAPS).tenths)
        assertEquals(ZeroReason.TOO_SOON, flight.entry(Figure.OUTSIDE_LOOPS).reason)
    }

    @Test
    fun aCloverEndingAfterSevenMinutesScoresAfterSevenMinutes() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding(skip = setOf(Figure.CLOVER), time = 418f)
        flight.figure { flight.tracks.clover(it, connected = true) }
        flight.pause(2f)
        assertEquals(flight.describe(), ZeroReason.AFTER_TIME, flight.entry(Figure.CLOVER).reason)
        assertEquals(100, flight.entry(Figure.OVERHEAD_EIGHTS).tenths)
        assertEquals(100, flight.entry(Figure.TAKEOFF).tenths)
    }

    @Test
    fun aCrashInTheTrianglesScoresCrashedFromThereOn() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding(skip = setOf(Figure.INSIDE_TRIANGLES, Figure.HORIZONTAL_EIGHTS, Figure.SQUARE_EIGHTS, Figure.VERTICAL_EIGHTS, Figure.HOURGLASS, Figure.OVERHEAD_EIGHTS, Figure.CLOVER))
        // the first triangle only, then the crash
        flight.figure { flight.tracks.triangles(it, 1) }
        flight.end(FlightEnd.CRASHED)
        assertEquals(100, flight.entry(Figure.OUTSIDE_SQUARES).tenths)
        for (f in SCHEDULE.drop(Figure.INSIDE_TRIANGLES.ordinal)) assertEquals("${f.label}\n${flight.describe()}", ZeroReason.CRASHED, flight.entry(f).reason)
    }

    @Test
    fun aLandingAfterSevenMinutesScoresNothing() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding(time = 424f)
        flight.landing()
        flight.end(FlightEnd.LANDED)
        assertEquals(ZeroReason.AFTER_TIME, flight.entry(Figure.LANDING).reason)
    }

    @Test
    fun aLoopWhileTheFuelRunsOutChangesNoScoreAndTheLandingIsMarked() {
        val flight = SyntheticSchedule()
        flight.everythingBeforeTheLanding()
        flight.pause(2f)
        val before = flight.judge.sheet.tenths
        flight.figure { flight.tracks.loops(it, 3) }
        flight.landing()
        flight.end(FlightEnd.LANDED)
        assertEquals(before + 5 * 100, flight.judge.sheet.tenths)
        assertEquals(100, flight.entry(Figure.LANDING).tenths)
    }

    @Test
    fun theHudStateAfterTheTakeoff() {
        val flight = SyntheticSchedule()
        assertEquals(Figure.TAKEOFF, flight.judge.expected)
        flight.takeoff()
        flight.pause(0.3f)
        assertEquals(Figure.REVERSE_WINGOVER, flight.judge.expected)
        assertNotNull(flight.judge.lastScored)
        assertEquals(100, flight.judge.lastScored!!.tenths)
        assertTrue(flight.judge.pauseLaps!! in 0f..3f)
        assertNull(flight.judge.heightWarning)
    }

    private fun invertedLapsRaised(metres: Float): Graded {
        val flight = SyntheticSchedule()
        flight.takeoff()
        flight.figure { flight.tracks.reverseWingover(it) }
        flight.figure { flight.tracks.loops(it, 3) }
        flight.figure(Perturbation(raise = metres), pauseInverted = false) { flight.tracks.invertedLaps(it, 2) }
        flight.pause(2f, inverted = true)
        flight.end(FlightEnd.CRASHED)
        return checkNotNull(flight.entry(Figure.INVERTED_LAPS).graded) { flight.describe() }
    }

    @Test
    fun levelFlightAtOnePointSevenMetresIsWithinTheTolerance() {
        val graded = invertedLapsRaised(0.2f)
        assertNull(graded.deductions.firstOrNull { it.criterion == Criterion.BASE_HEIGHT })
    }

    @Test
    fun theInvertedLapsAtTwoMetresAreTwentyCentimetresAboveTheTolerance() {
        val deduction = invertedLapsRaised(0.5f).deductions.first { it.criterion == Criterion.BASE_HEIGHT }
        assertEquals(0.5f, deduction.measured, 0.05f)
        assertEquals(2f * (deduction.measured - 0.3f), deduction.amount, 1e-3f)
        assertEquals(0.4f, deduction.amount, 0.1f)
    }
}
