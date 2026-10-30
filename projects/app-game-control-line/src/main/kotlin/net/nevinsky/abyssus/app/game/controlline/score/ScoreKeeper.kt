/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.score

import net.nevinsky.abyssus.app.game.controlline.track.Laps
import net.nevinsky.abyssus.app.game.controlline.track.ManeuverEvent
import net.nevinsky.abyssus.app.game.controlline.track.Maneuvers
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample

/** Points for each lap, never multiplied. */
const val LAP_POINTS = 10

/** Extra points for a flight that ends as a landing. */
const val LANDING_POINTS = 200

/** A maneuver completed within this many seconds of the previous one raises the multiplier... */
const val COMBO_WINDOW = 10f

/** ...up to this. */
const val MAX_MULTIPLIER = 5

/** The points of one flight: laps, maneuvers times the combo multiplier, and the landing bonus. */
class ScoreKeeper {
    var score = 0
        private set
    var laps = 0
        private set

    /** The multiplier the next maneuver gets if it completes in time; 1 after a pause. */
    var multiplier = 1
        private set
    var bestCombo = 1
        private set

    /** The last maneuver scored, for the HUD. */
    var lastManeuver: ManeuverEvent? = null
        private set

    private var lastTime: Float? = null

    fun lap() {
        laps++
        score += LAP_POINTS
    }

    /** Scores [event] at the multiplier its time earns; returns its points. */
    fun maneuver(event: ManeuverEvent): Int {
        val previous = lastTime
        multiplier = if (previous != null && event.time - previous <= COMBO_WINDOW) minOf(multiplier + 1, MAX_MULTIPLIER) else 1
        bestCombo = maxOf(bestCombo, multiplier)
        lastTime = event.time
        lastManeuver = event
        val points = event.maneuver.points * multiplier
        score += points
        return points
    }

    /** At [time]: the combo is lost once [COMBO_WINDOW] passes without a maneuver. */
    fun tick(time: Float) {
        lastTime?.let { if (time - it > COMBO_WINDOW) loseCombo() }
    }

    /** Slack lines lose the combo. */
    fun linesSlack() = loseCombo()

    fun landed() {
        score += LANDING_POINTS
    }

    private fun loseCombo() {
        multiplier = 1
        lastTime = null
    }
}

/** Scores a flight from its track: laps and maneuvers into a [ScoreKeeper]. */
class FlightScoring(val keeper: ScoreKeeper = ScoreKeeper(), private val maneuvers: Maneuvers = Maneuvers(), private val laps: Laps = Laps()) {
    fun sample(sample: TrackSample) {
        repeat(laps.sample(sample)) { keeper.lap() }
        for (event in maneuvers.sample(sample)) keeper.maneuver(event)
        keeper.tick(sample.time)
    }

    /** The flight ended; [landed] adds the landing bonus. A held maneuver scores first. */
    fun finish(landed: Boolean) {
        for (event in maneuvers.finish()) keeper.maneuver(event)
        if (landed) keeper.landed()
    }
}
