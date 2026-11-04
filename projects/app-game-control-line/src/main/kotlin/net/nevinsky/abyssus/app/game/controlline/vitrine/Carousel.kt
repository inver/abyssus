/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.vitrine

import kotlin.math.floor
import kotlin.math.min

/** How far apart the planes of the hangar stand along the table's side (m). */
const val CAROUSEL_SPACING = 3.2f

/** How quickly the carousel turns to the selected plane (per second). */
private const val SLIDE = 5f

/**
 * Where the planes of plane select stand along a line through the table's centre: the selected plane at 0, the others
 * at whole steps to either side, wrapping round. [position] is the (fractional) index at the centre; it glides to the
 * selected index by the shortest way. Plain math, no GL.
 */
class Carousel(private val count: Int, selected: Int = 0) {
    var position = selected.toFloat().coerceAtLeast(0f)
        private set

    /** Glides over [seconds] towards [selected]. */
    fun advance(selected: Int, seconds: Float) {
        if (count == 0) {
            return
        }
        val step = wrap(selected - position)
        position = wrapIndex(position + step * min(1f, SLIDE * seconds))
    }

    /** How many steps plane [index] stands from the centre: in `[-count / 2, count / 2)`; 0 is the centre. */
    fun offset(index: Int): Float = if (count == 0) 0f else wrap(index - position)

    private fun wrap(delta: Float): Float {
        val half = count / 2f
        return delta - count * floor((delta + half) / count)
    }

    private fun wrapIndex(index: Float): Float = index - count * floor(index / count)
}
