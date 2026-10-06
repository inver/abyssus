/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.track

import kotlin.math.abs
import kotlin.math.max

/** The maneuvers scored, with their points before the combo multiplier. */
enum class Maneuver(val points: Int, val label: String) {
    INSIDE_LOOP(50, "Inside loop"),
    OUTSIDE_LOOP(50, "Outside loop"),
    INVERTED_LAP(80, "Inverted lap"),
    WINGOVER(100, "Wingover"),
    FIGURE_EIGHT(150, "Figure 8"),
}

/** A [maneuver] completed at [time] seconds into the flight. */
data class ManeuverEvent(val maneuver: Maneuver, val time: Float)

// The thresholds of the `control-line-scoring` spec, in degrees and seconds.
/** A loop returns to level flight below this elevation... */
const val LOOP_LEVEL_ELEVATION = 30f

/** ...within this time of leaving it. */
const val LOOP_TIME = 8f

/** Level flight: the climb angle is within this of along the circle (either way). */
const val LEVEL_CLIMB = 20f

/** An outside loop that starts within this of an inside loop's end makes a figure 8. */
const val FIGURE_EIGHT_GAP = 2f

/** An inverted lap is flown below this elevation. */
const val INVERTED_LAP_ELEVATION = 45f

/** A wingover climbs above this elevation... */
const val WINGOVER_TOP = 80f

/** ...and returns to level flight below this one... */
const val WINGOVER_LEVEL_ELEVATION = 20f

/** ...within this time, on the other side: the azimuth has moved by more than [WINGOVER_TURN]. */
const val WINGOVER_TIME = 6f
const val WINGOVER_TURN = 135f

private fun level(climb: Float) = abs(climb) < LEVEL_CLIMB || abs(climb) > 180f - LEVEL_CLIMB

/** An unwrapped climb angle rounded to the nearest level direction, a multiple of 180. */
private fun snapped(climb: Float) = Math.round(climb / 180f) * 180f

/**
 * Detects maneuvers from a flight's [TrackSample]s, one sample per physics step, with no shape judge. Each detector is
 * a small state machine:
 * - loops follow the unwrapped climb angle from the last level flight below [LOOP_LEVEL_ELEVATION] to the next;
 *   a full turn up within [LOOP_TIME] is an inside loop, down an outside loop;
 * - an inside loop is held for [FIGURE_EIGHT_GAP] seconds: an outside loop starting in that time makes the pair one
 *   figure 8, and neither loop scores on its own;
 * - an inverted lap follows the azimuth while the plane is upside down below [INVERTED_LAP_ELEVATION];
 * - a wingover follows the elevation from level flight over the top and down the other side.
 */
class Maneuvers {
    private var previous: TrackSample? = null

    // loops
    private var climb = 0f
    private var loopFrom = 0f
    private var loopStart = 0f
    private var loopValid = true
    private var held: ManeuverEvent? = null

    // inverted lap
    private var inverted = 0f

    // wingover
    private var wingoverStart = 0f
    private var wingoverAzimuth = 0f
    private var highest = -90f

    /** The maneuvers completed with [sample], in the order they completed. */
    fun sample(sample: TrackSample): List<ManeuverEvent> {
        val last = previous
        previous = sample
        if (last == null) {
            climb = sample.climb
            restartLoop(sample)
            restartWingover(sample)
            return emptyList()
        }
        val found = ArrayList<ManeuverEvent>(1)
        loops(last, sample, found)
        invertedLap(last, sample, found)
        wingover(sample, found)
        return found
    }

    /** The flight has ended: a held inside loop scores now. */
    fun finish(): List<ManeuverEvent> = listOfNotNull(held).also { held = null }

    private fun restartLoop(s: TrackSample) {
        loopFrom = climb
        loopStart = s.time
        loopValid = true
    }

    private fun loops(last: TrackSample, s: TrackSample, found: MutableList<ManeuverEvent>) {
        climb += wrapDegrees(s.climb - last.climb)
        // near the zenith the sphere's frame turns over and the climb angle means nothing: no loop goes up there
        if (s.elevation > WINGOVER_TOP) loopValid = false
        if (s.elevation < LOOP_LEVEL_ELEVATION && level(s.climb)) {
            // both ends are level: count the turn between the level directions they are nearest to
            val turned = snapped(climb) - snapped(loopFrom)
            val inTime = loopValid && s.time - loopStart <= LOOP_TIME
            when {
                inTime && turned >= 360f -> {
                    held?.let { found += it }
                    held = ManeuverEvent(Maneuver.INSIDE_LOOP, s.time)
                }
                inTime && turned <= -360f -> {
                    val inside = held
                    held = null
                    if (inside != null && loopStart - inside.time <= FIGURE_EIGHT_GAP) {
                        found += ManeuverEvent(Maneuver.FIGURE_EIGHT, s.time)
                    } else {
                        inside?.let { found += it }
                        found += ManeuverEvent(Maneuver.OUTSIDE_LOOP, s.time)
                    }
                }
            }
            restartLoop(s)
        }
        // level flight went on past the gap: no figure 8 can start in time any more
        held?.let { if (loopStart - it.time > FIGURE_EIGHT_GAP) found += it.also { held = null } }
    }

    private fun invertedLap(last: TrackSample, s: TrackSample, found: MutableList<ManeuverEvent>) {
        if (!s.inverted || !s.airborne || s.elevation >= INVERTED_LAP_ELEVATION) {
            inverted = 0f
            return
        }
        inverted += wrapDegrees(s.azimuth - last.azimuth)
        if (inverted >= 360f) {
            inverted -= 360f
            found += ManeuverEvent(Maneuver.INVERTED_LAP, s.time)
        }
    }

    private fun restartWingover(s: TrackSample) {
        wingoverStart = s.time
        wingoverAzimuth = s.azimuth
        highest = s.elevation
    }

    private fun wingover(s: TrackSample, found: MutableList<ManeuverEvent>) {
        highest = max(highest, s.elevation)
        if (s.elevation >= WINGOVER_LEVEL_ELEVATION || !level(s.climb)) return
        if (highest > WINGOVER_TOP && s.time - wingoverStart <= WINGOVER_TIME && abs(wrapDegrees(s.azimuth - wingoverAzimuth)) > WINGOVER_TURN) {
            found += ManeuverEvent(Maneuver.WINGOVER, s.time)
        }
        restartWingover(s)
    }
}

/** Counts laps: each full turn around the pilot in the flying direction while airborne. */
class Laps {
    private var previous: Float? = null
    private var progress = 0f

    var count = 0
        private set

    /** The laps completed with [sample] (0 or 1). */
    fun sample(sample: TrackSample): Int {
        val last = previous
        previous = sample.azimuth
        if (last == null || !sample.airborne) return 0
        progress += wrapDegrees(sample.azimuth - last)
        if (progress < 360f) return 0
        progress -= 360f
        count++
        return 1
    }
}
