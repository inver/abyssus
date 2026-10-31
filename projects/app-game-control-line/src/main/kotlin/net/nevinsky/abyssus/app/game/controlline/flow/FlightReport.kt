/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flow

import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.FlightRecording
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.ScoreSheet
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleJudge
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleMarks

/** How a kind of flight is scored: the arcade [Free] result, the F2B [Pattern] one, or the replica's [Scale] realism marks. */
sealed interface FlightResult {
    /** Free flight: points for laps, maneuvers and the landing, with the combo. */
    data class Free(val score: Int, val laps: Int, val bestCombo: Int) : FlightResult

    /** An F2B flight at [level]: the score sheet, and the [recording] the debrief replays. */
    data class Pattern(val level: AssistLevel, val sheet: ScoreSheet, val recording: FlightRecording? = null) : FlightResult

    /** A Scale flight: the three realism [marks] and the [laps] they were measured over. */
    data class Scale(val marks: ScaleMarks, val laps: Int) : FlightResult {
        val score: Int get() = marks.total
    }
}

/** How a flight went, as GAME OVER shows it: the [plane], how it ended, the [flightTime] in seconds and its [result]. */
data class FlightReport(val plane: String, val end: FlightEnd, val flightTime: Float, val result: FlightResult)

/** The mode of a flight: Free flight, an F2B pattern at an assist level, or Scale (a replica judged on realism). */
sealed interface Mode {
    data object Free : Mode

    data class Pattern(val level: AssistLevel) : Mode

    data object Scale : Mode
}

/**
 * The report of [session]'s flight under [mode] (with the F2B [judge] of a pattern flight, or the [scaleJudge] of a Scale
 * one), or null while the flight goes on.
 */
fun reportOf(session: FlightSession, mode: Mode, judge: PatternJudge?, plane: String, scaleJudge: ScaleJudge? = null): FlightReport? {
    val end = session.flight.result ?: return null
    val result = when (mode) {
        Mode.Free -> session.flight.scoring.keeper.let { FlightResult.Free(it.score, it.laps, it.bestCombo) }
        is Mode.Pattern -> requireNotNull(judge) { "an F2B flight needs its judge" }
            .let { FlightResult.Pattern(mode.level, it.sheet, it.recording(plane, mode.level)) }
        Mode.Scale -> requireNotNull(scaleJudge) { "a Scale flight needs its judge" }.let { FlightResult.Scale(it.marks, it.laps) }
    }
    return FlightReport(plane, end, session.flight.time, result)
}
