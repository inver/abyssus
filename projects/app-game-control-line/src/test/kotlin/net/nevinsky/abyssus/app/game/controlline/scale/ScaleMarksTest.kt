/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleMarksTest {
    @Test
    fun aSteadyLevelLandedFlightScoresEverything() {
        val marks = scaleMarks(listOf(10f, 10.2f, 9.8f, 10.1f, 9.9f), meanElevation = 10f, landed = true)
        assertEquals(ScaleMarks(400, 400, 200), marks)
        assertEquals(1000, marks.total)
    }

    @Test
    fun speedFallsFromFivePercentToThirtyPercentSpread() {
        // laps of 10 and 11 s: mean 10.5, spread 0.5/10.5 = 4.8 %: full marks
        assertEquals(400, scaleMarks(listOf(10f, 11f), 0f, false).speed)
        // laps of 10 and 13 s: spread 1.5/11.5 = 13 %: part of the way down
        assertEquals(271, scaleMarks(listOf(10f, 13f), 0f, false).speed)
        // laps of 10 and 18.5 s: spread 4.25/14.25 = 29.8 %: nearly nothing
        assertEquals(3, scaleMarks(listOf(10f, 18.5f), 0f, false).speed)
        // laps of 10 and 19 s: spread 4.5/14.5 = 31 %: nothing
        assertEquals(0, scaleMarks(listOf(10f, 19f), 0f, false).speed)
    }

    @Test
    fun levelFallsFromFifteenToFortyFiveDegrees() {
        val laps = listOf(10f, 10f)
        assertEquals(400, scaleMarks(laps, 15f, false).level)
        assertEquals(200, scaleMarks(laps, 30f, false).level)
        assertEquals(0, scaleMarks(laps, 45f, false).level)
        assertEquals(0, scaleMarks(laps, 80f, false).level)
        assertEquals(200, scaleMarks(laps, -30f, false).level)
    }

    @Test
    fun fewerThanTwoLapsScoreOnlyTheLanding() {
        assertEquals(ScaleMarks(0, 0, 200), scaleMarks(listOf(10f), 5f, true))
        assertEquals(ScaleMarks(0, 0, 0), scaleMarks(emptyList(), 5f, false))
    }

    @Test
    fun aCrashKeepsTheOtherMarksAndScoresNoLanding() {
        assertEquals(ScaleMarks(400, 400, 0), scaleMarks(listOf(10f, 10f, 10f, 10f), 5f, false))
    }
}
