/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.fieldSession
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
import net.nevinsky.abyssus.app.game.controlline.flow.reportOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleReportTest {
    @Test
    fun aScaleSessionReportsTheJudgesMarksAndNoFreeScore() {
        val judge = ScaleJudge()
        var seconds = 0f
        // level for a minute, then the handle goes full down: the plane crashes and the flight ends
        fieldSession("Trainer", judge, FlightControlSource { if (seconds < 60f) 0f else -1f }).use { session ->
            assertNull(reportOf(session, Mode.Scale, null, "Trainer", judge))
            repeat(60 * 180) {
                if (session.flight.result == null) {
                    session.advance(1f / 60f)
                    seconds += 1f / 60f
                }
            }
            val report = checkNotNull(reportOf(session, Mode.Scale, null, "Trainer", judge))
            val result = report.result as FlightResult.Scale
            assertEquals(judge.marks, result.marks)
            assertEquals(judge.laps, result.laps)
            assertEquals(result.marks.total, result.score)
            assertTrue(report.result !is FlightResult.Free)
        }
    }
}
