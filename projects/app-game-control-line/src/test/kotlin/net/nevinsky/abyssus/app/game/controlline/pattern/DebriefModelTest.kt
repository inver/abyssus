/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.badlogic.gdx.math.MathUtils
import net.nevinsky.abyssus.app.game.controlline.track.wrapDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

/** A recording of [seconds] with the plane moving 1 m/s along x at 30 Hz and one judgement per entry of [judgements]. */
private fun handRecording(seconds: Float, judgements: List<RecordedJudgement>): FlightRecording {
    val count = (seconds * 30).toInt() + 1
    val samples = FloatArray(count * SAMPLE_STRIDE)
    for (i in 0 until count) {
        val t = i / 30f
        floatArrayOf(t, t, 1.5f, 10f, 0f, 0f, 0f, 1f).copyInto(samples, i * SAMPLE_STRIDE)
    }
    return FlightRecording("Stunter", 18f, AssistLevel.PRO, samples, judgements)
}

private fun judgement(figure: Figure, start: Float, end: Float, tenths: Int, reason: ZeroReason? = null, deductions: List<RecordedDeduction> = emptyList(), ideal: FloatArray = FloatArray(0)) =
    RecordedJudgement(figure.ordinal, start, end, tenths, reason, deductions, ideal)

class DebriefModelTest {
    private fun azimuth(x: Float, z: Float) = atan2(-z, x) * MathUtils.radiansToDegrees

    @Test
    fun theDebriefStartsOnTheFirstManoeuvreWithFifteenRows() {
        val model = debriefOf(recordedFlight())
        assertEquals(0, model.selected)
        assertEquals(15, model.rows.size)
        assertEquals(Figure.TAKEOFF, model.row.figure)
        assertNull(model.notice)
        // the crashed flight decided every row but ... all 15 are on the sheet
        assertTrue(model.rows.all { it.decided })
    }

    @Test
    fun insideLoopsHaveTheIdealAndFlownSegmentsAndTheGuides() {
        val model = debriefOf(recordedFlight())
        model.select(2)
        assertEquals(Figure.INSIDE_LOOPS, model.row.figure)
        val kinds = model.segments().map { it.kind }
        assertEquals(listOf(TrackKind.IDEAL, TrackKind.FLOWN, TrackKind.GUIDE, TrackKind.GUIDE), kinds)
        assertTrue(model.segments().all { it.points.size > 10 })
        val frame = model.frame()
        assertTrue(frame.points.size > 100)
        assertTrue(frame.centre.y > 2f)
    }

    @Test
    fun omittedHasNoSegmentsAndTheReason() {
        val model = debriefOf(recordedFlight())
        model.select(3)
        assertEquals(Figure.INVERTED_LAPS, model.row.figure)
        assertTrue(model.segments().isEmpty())
        assertEquals(listOf("Scored 0: omitted"), model.deductionLines())
        assertEquals(1, model.frame().points.size)
    }

    @Test
    fun selectionStopsAtTheEndsAndRestartsTheReplay() {
        val model = debriefOf(recordedFlight())
        model.select(-1)
        assertEquals(0, model.selected)
        model.select(6)
        assertEquals(6, model.selected)
        model.select(100)
        assertEquals(14, model.selected)
        model.select(-100)
        assertEquals(0, model.selected)
        assertEquals(0f, model.clock, 0f)
    }

    @Test
    fun slowMotionTakesTwiceAsLongAndSpaceHoldsThePose() {
        val model = debriefOf(handRecording(20f, listOf(judgement(Figure.INSIDE_LOOPS, 2f, 14f, 100))))
        model.select(2)
        model.setSpeed(0)
        assertEquals(12f, model.duration, 0f)
        repeat(23) { model.advance(1f) }
        assertEquals(11.5f, model.clock, 1e-4f)
        model.advance(1f)
        assertEquals(12f, model.clock, 1e-4f)
        model.advance(5f)
        assertEquals(12f, model.clock, 1e-4f)
        val atEnd = checkNotNull(model.planePose()).position.x
        assertEquals(14f, atEnd, 0.05f)

        model.restart()
        model.advance(2f)
        assertEquals(1f, model.clock, 1e-4f)
        model.togglePause()
        model.advance(4f)
        assertEquals(1f, model.clock, 1e-4f)
        assertFalse(model.playing)
        model.togglePause()
        model.setSpeed(2)
        model.advance(1f)
        assertEquals(3f, model.clock, 1e-4f)
    }

    @Test
    fun thePlanePoseIsInterpolatedBetweenSamples() {
        val model = debriefOf(handRecording(20f, listOf(judgement(Figure.INSIDE_LOOPS, 2f, 14f, 100))))
        model.select(2)
        model.advance(0.5f * (1f / 30f) * 2f) // a third of a sample step... any fraction between two samples
        val pose = checkNotNull(model.planePose())
        assertEquals(2f + model.clock, pose.position.x, 1e-3f)
        assertEquals(1.5f, pose.position.y, 1e-3f)
        assertEquals(1f, pose.rotation.len(), 1e-3f)
    }

    @Test
    fun lowBottomsListOneLine() {
        val low = RecordedDeduction(Criterion.BASE_HEIGHT, 0.6f, 0.3f, 6)
        val model = debriefOf(handRecording(20f, listOf(judgement(Figure.INSIDE_LOOPS, 2f, 14f, 94, deductions = listOf(low)))))
        model.select(2)
        assertEquals(listOf("Base height 0.60 m off (±0.30): −0.6"), model.deductionLines())
    }

    @Test
    fun deductionsAreListedLargestFirstAndATenSaysNone() {
        val small = RecordedDeduction(Criterion.PARALLEL_45, 0.7f, 0.5f, 2)
        val big = RecordedDeduction(Criterion.LINE_ANGLE, 9f, 3f, 12)
        val model = debriefOf(handRecording(20f, listOf(
            judgement(Figure.INSIDE_LOOPS, 2f, 14f, 86, deductions = listOf(small, big)),
            judgement(Figure.INVERTED_LAPS, 14f, 18f, 100),
        )))
        model.select(2)
        assertEquals(listOf("Line angle 9.00 ° off (±3.00): −1.2", "45° parallel 0.70 m off (±0.50): −0.2"), model.deductionLines())
        model.select(1)
        assertEquals(listOf("No deductions"), model.deductionLines())
    }

    @Test
    fun tooSoonShowsTheReasonAndNoDeductions() {
        val model = debriefOf(handRecording(20f, listOf(judgement(Figure.OUTSIDE_LOOPS, 2f, 14f, 0, ZeroReason.TOO_SOON))))
        model.select(4)
        assertEquals(listOf("Scored 0: too soon"), model.deductionLines())
        // the flown path is still shown
        assertEquals(listOf(TrackKind.FLOWN, TrackKind.GUIDE, TrackKind.GUIDE), model.segments().map { it.kind })
    }

    @Test
    fun theGuidesRunTenDegreesBeyondTheFigure() {
        val geometry = HemisphereGeometry(18f)
        val ideal = FloatArray(31 * 3)
        for (i in 0..30) {
            val p = geometry.position(geometry.direction(30f, 20f + i)) // azimuths 20..50
            floatArrayOf(p.x, p.y, p.z).copyInto(ideal, i * 3)
        }
        val model = debriefOf(handRecording(20f, listOf(judgement(Figure.INSIDE_LOOPS, 2f, 14f, 100, ideal = ideal))))
        model.select(2)
        val guides = model.segments().filter { it.kind == TrackKind.GUIDE }
        assertEquals(2, guides.size)
        for (guide in guides) {
            val first = guide.points.first()
            val last = guide.points.last()
            assertEquals(10f, wrapDegrees(azimuth(first.x, first.z)), 0.5f)
            assertEquals(60f, wrapDegrees(azimuth(last.x, last.z)), 0.5f)
        }
        assertEquals(0f, guides[0].points.first().y - 1.5f, 0.01f)
        assertEquals(1.5f + 18f * Math.sin(Math.toRadians(45.0)).toFloat(), guides[1].points.first().y, 0.01f)
    }

    @Test
    fun aDeletedRecordingShowsTheSheetOnly() {
        val entry = PatternScoreEntry("Ann", "Stunter", 612.4, 400f, "2026-10-04T12:00:00Z", List(15) { 7.5 })
        val model = sheetOnlyDebrief(entry, AssistLevel.PRO)
        assertEquals(NO_REPLAY_MESSAGE, model.notice)
        assertEquals(15, model.rows.size)
        assertTrue(model.rows.all { it.tenths == 75 })
        assertTrue(model.segments().isEmpty())
        assertTrue(model.deductionLines().isEmpty())
        assertNull(model.planePose())
        assertNotNull(model.frame())
        model.advance(1f)
        model.select(5)
        assertEquals(5, model.selected)
    }

    @Test
    fun theViewSwitchesAndIsKeptWhenAnotherManoeuvreIsSelected() {
        val model = debriefOf(recordedFlight())
        assertEquals(DebriefView.PILOT, model.view)
        model.toggleView()
        model.select(1)
        assertEquals(DebriefView.OUTSIDE, model.view)
        model.toggleView()
        assertEquals(DebriefView.PILOT, model.view)
    }
}
