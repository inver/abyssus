/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import kotlin.math.abs
import kotlin.math.roundToInt

/** The most steady speed scores. */
const val SPEED_POINTS = 400

/** The most level laps score. */
const val LEVEL_POINTS = 400

/** A landing scores this much. */
const val LANDING_POINTS = 200

/** Lap times within this share of their mean score all of [SPEED_POINTS]. */
const val STEADY_SPREAD = 0.05f

/** Lap times this far from their mean score none of them. */
const val UNSTEADY_SPREAD = 0.30f

/** A mean elevation of at most this many degrees scores all of [LEVEL_POINTS]. */
const val LEVEL_ELEVATION = 15f

/** A mean elevation this high scores none of them. */
const val UNLEVEL_ELEVATION = 45f

/** The fewest laps a flight needs for its speed and level marks. */
const val MIN_LAPS = 2

/** The three marks of a Scale flight, in points. */
data class ScaleMarks(val speed: Int, val level: Int, val landing: Int) {
    val total: Int get() = speed + level + landing
}

/**
 * The marks of a flight that flew laps of [lapTimes] seconds at a mean absolute elevation of [meanElevation] degrees and
 * [landed] or not. Fewer than [MIN_LAPS] laps score no speed or level mark.
 */
fun scaleMarks(lapTimes: List<Float>, meanElevation: Float, landed: Boolean): ScaleMarks {
    val landing = if (landed) LANDING_POINTS else 0
    if (lapTimes.size < MIN_LAPS) return ScaleMarks(0, 0, landing)
    val mean = lapTimes.average().toFloat()
    val spread = if (mean <= 0f) UNSTEADY_SPREAD else lapTimes.maxOf { abs(it - mean) } / mean
    return ScaleMarks(
        falling(spread, STEADY_SPREAD, UNSTEADY_SPREAD, SPEED_POINTS),
        falling(abs(meanElevation), LEVEL_ELEVATION, UNLEVEL_ELEVATION, LEVEL_POINTS),
        landing,
    )
}

/** [points] up to [full], then falling linearly to 0 at [none]. */
private fun falling(value: Float, full: Float, none: Float, points: Int): Int =
    (points * ((none - value) / (none - full)).coerceIn(0f, 1f)).roundToInt()
