/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleJudgeTest {
    /** Feeds [laps] laps of [seconds] each at [elevation] degrees, a sample every 0.5 s, starting at [start]. */
    private fun ScaleJudge.fly(laps: Int, seconds: Float, elevation: Float, start: Float = 0f): Float {
        var t = start
        var azimuth = 0f
        sample(sample(t, azimuth, elevation))
        val steps = (seconds / 0.5f).toInt()
        repeat(laps) {
            repeat(steps) {
                t += 0.5f
                azimuth += 360f / steps
                sample(sample(t, azimuth, elevation))
            }
        }
        return t
    }

    private fun sample(time: Float, azimuth: Float, elevation: Float, airborne: Boolean = true) =
        TrackSample(time, ((azimuth + 180f) % 360f) - 180f, elevation, 0f, false, airborne)

    @Test
    fun steadyLevelLapsAndALandingScoreEverything() {
        val judge = ScaleJudge()
        judge.fly(laps = 5, seconds = 10f, elevation = 10f)
        judge.ended(FlightEnd.LANDED)
        assertEquals(5, judge.laps)
        assertEquals(ScaleMarks(400, 400, 200), judge.marks)
    }

    @Test
    fun racingAndStallingLapsScoreNoSpeed() {
        val judge = ScaleJudge()
        val t = judge.fly(laps = 1, seconds = 5f, elevation = 0f)
        judge.fly(laps = 1, seconds = 10f, elevation = 0f, start = t)
        judge.ended(FlightEnd.LANDED)
        assertEquals(2, judge.laps)
        assertEquals(0, judge.marks.speed)
    }

    @Test
    fun aCrashAfterFourLapsKeepsTheMarksAndScoresNoLanding() {
        val judge = ScaleJudge()
        judge.fly(laps = 4, seconds = 10f, elevation = 5f)
        judge.ended(FlightEnd.CRASHED)
        assertEquals(ScaleMarks(400, 400, 0), judge.marks)
    }

    @Test
    fun oneLapAndALandingScoreTheLandingOnly() {
        val judge = ScaleJudge()
        judge.fly(laps = 1, seconds = 10f, elevation = 5f)
        judge.ended(FlightEnd.LANDED)
        assertEquals(ScaleMarks(0, 0, 200), judge.marks)
    }

    @Test
    fun aLapStillBeingFlownDoesNotCount() {
        val judge = ScaleJudge()
        val t = judge.fly(laps = 2, seconds = 10f, elevation = 5f)
        // half a lap more, high up: it must not drag the level mark down
        var azimuth = 0f
        repeat(10) { i -> azimuth += 18f; judge.sample(sample(t + 0.5f * (i + 1), azimuth, 80f)) }
        judge.ended(FlightEnd.CRASHED)
        assertEquals(2, judge.laps)
        assertEquals(ScaleMarks(400, 400, 0), judge.marks)
    }

    @Test
    fun theGroundRollIsNotALap() {
        val judge = ScaleJudge()
        var azimuth = 0f
        repeat(40) { i -> azimuth += 20f; judge.sample(sample(0.5f * i, azimuth, 0f, airborne = false)) }
        assertEquals(0, judge.laps)
    }
}
