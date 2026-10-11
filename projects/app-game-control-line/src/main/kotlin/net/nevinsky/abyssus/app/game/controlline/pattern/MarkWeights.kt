/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import kotlin.math.max
import kotlin.math.min

/** One criterion of a mark: nothing is deducted up to [tolerance], then [perUnit] marks for each unit more, at most [cap]. */
data class Weight(val tolerance: Float, val perUnit: Float, val cap: Float) {
    /** The deduction (marks) for a [measured] error. Never decreases as the error grows. */
    fun deduct(measured: Float): Float = min(cap, perUnit * max(0f, measured - tolerance))
}

/**
 * The deductions of a mark (design decision 5). Annex 4B, the Judges' Guide, is not part of the 2027 volume, so these are
 * this game's own weights, one table to tune by play. Units: metres, degrees, laps, metres per second.
 */
data class MarkWeights(
    /** Worst height away from 1.5 m on the base (m). */
    val baseHeight: Weight = Weight(0.30f, 2f, 3f),
    /** RMS distance to the ideal track (m). */
    val trackDeviation: Weight = Weight(0.5f, 1f, 4f),
    /** The top of a figure away from the 45° parallel (m). */
    val parallel45: Weight = Weight(0.5f, 1f, 2f),
    /** Verticals and 30° legs away from their direction (degrees). */
    val lineAngle: Weight = Weight(3f, 0.1f, 2f),
    /** The mean radius of the corners (m); the Stunter cannot turn tighter than about 2.7 m at full tilt (see the README). */
    val cornerRadius: Weight = Weight(3f, 1f, 3f),
    /** A segment that should be ⅛ lap, ½ lap or 1 lap away from it (laps). */
    val segmentLength: Weight = Weight(0.02f, 10f, 2f),
    /** The worst distance between repeats that should be on top of each other (m). */
    val superimposition: Weight = Weight(0.5f, 1f, 2f),
    /** The crossing point drifting between passes (m). */
    val symmetry: Weight = Weight(0.5f, 1f, 2f),
    /** The take-off roll outside 4.5 m to ¼ lap (m). */
    val takeoffRoll: Weight = Weight(0f, 0.2f, 2f),
    /** The vertical speed at touchdown (m/s). */
    val touchdown: Weight = Weight(1f, 1f, 2f),
)
