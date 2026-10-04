/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.score

import net.nevinsky.abyssus.games.controlline.track.Maneuver
import net.nevinsky.abyssus.games.controlline.track.ManeuverEvent
import net.nevinsky.abyssus.games.controlline.track.TrackSample
import net.nevinsky.abyssus.games.controlline.track.wrapDegrees
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreKeeperTest {
    @Test
    fun threeLapsScoreThirtyUnmultiplied() {
        val scoring = FlightScoring()
        scoring.keeper.maneuver(ManeuverEvent(Maneuver.INSIDE_LOOP, 0f))
        val before = scoring.keeper.score
        // three turns at one lap per 5 s, in the air, sampled at 120 Hz
        var azimuth = 0f
        for (i in 1..(15 * 120 + 12)) {
            azimuth = wrapDegrees(azimuth + 360f / 5f / 120f)
            scoring.sample(TrackSample(i / 120f, azimuth, 10f, 0f, inverted = false, airborne = true))
        }
        assertEquals(3, scoring.keeper.laps)
        assertEquals(30, scoring.keeper.score - before)
    }

    @Test
    fun chainedLoopsScoreFiftyHundredHundredFifty() {
        val keeper = ScoreKeeper()
        val points = listOf(0f, 4f, 8f).map { keeper.maneuver(ManeuverEvent(Maneuver.INSIDE_LOOP, it)) }
        assertEquals(listOf(50, 100, 150), points)
        assertEquals(3, keeper.bestCombo)
        assertEquals(300, keeper.score)
    }

    @Test
    fun theMultiplierStopsAtFive() {
        val keeper = ScoreKeeper()
        repeat(7) { keeper.maneuver(ManeuverEvent(Maneuver.INSIDE_LOOP, it * 3f)) }
        assertEquals(5, keeper.multiplier)
        assertEquals(5, keeper.bestCombo)
    }

    @Test
    fun comboLostAfterTwelveSeconds() {
        val keeper = ScoreKeeper()
        keeper.maneuver(ManeuverEvent(Maneuver.WINGOVER, 0f))
        keeper.maneuver(ManeuverEvent(Maneuver.WINGOVER, 5f))
        assertEquals(2, keeper.multiplier)
        keeper.tick(17f)
        assertEquals(1, keeper.multiplier)
        assertEquals(100, keeper.maneuver(ManeuverEvent(Maneuver.WINGOVER, 17.5f)))
    }

    @Test
    fun slackLinesLoseTheCombo() {
        val keeper = ScoreKeeper()
        keeper.maneuver(ManeuverEvent(Maneuver.INSIDE_LOOP, 0f))
        keeper.maneuver(ManeuverEvent(Maneuver.INSIDE_LOOP, 4f))
        keeper.linesSlack()
        assertEquals(1, keeper.multiplier)
        assertEquals(50, keeper.maneuver(ManeuverEvent(Maneuver.INSIDE_LOOP, 6f)))
    }

    @Test
    fun landingAfterThirtyLapPointsGivesTwoHundredThirty() {
        val scoring = FlightScoring()
        repeat(3) { scoring.keeper.lap() }
        scoring.finish(landed = true)
        assertEquals(230, scoring.keeper.score)
    }

    @Test
    fun aCrashAddsNothing() {
        val scoring = FlightScoring()
        repeat(3) { scoring.keeper.lap() }
        scoring.finish(landed = false)
        assertEquals(30, scoring.keeper.score)
    }
}
