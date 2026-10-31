/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.Takeover
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP

/** Seconds the danger must stay 0 before the instructor starts to hand the handle back, and how long the handback takes. */
const val HANDBACK_HOLD = 1f
const val HANDBACK_TIME = 0.5f

/** Float slack for the step counting of the hold and the fall. */
private const val EPSILON = 1e-4f

/** The share of the handle from which the instructor holds it: a takeover. */
const val TAKEOVER_SHARE = 0.5f

/**
 * How much of the handle the instructor holds, from 0 to 1 (design decision 4 of `add-control-line-trainer`): it rises
 * to the danger at once, stays through [HANDBACK_HOLD] of calm and then falls over [HANDBACK_TIME]. It records a
 * [Takeover] each time the share reaches [TAKEOVER_SHARE] and falls below it.
 */
class InstructorShare {
    var share = 0f
        private set

    private var calm = 0f
    private var open: Float? = null
    private val spans = ArrayList<Takeover>()

    /** Takeovers so far; one still going on is not in it ([inTakeover]). */
    val takeovers: List<Takeover> get() = spans

    /** Whether a takeover is going on (the share is at least [TAKEOVER_SHARE]). */
    val inTakeover: Boolean get() = open != null

    /** Takes the [danger] of the step at flight time [time] (s); the state is the one after the step. */
    fun step(danger: Float, time: Float) {
        if (danger > share) share = danger
        if (danger > 0f) {
            calm = 0f
        } else {
            calm += PHYSICS_STEP
            if (calm >= HANDBACK_HOLD - EPSILON) share = (share - PHYSICS_STEP / HANDBACK_TIME).let { if (it < EPSILON) 0f else it }
        }
        val start = open
        if (start == null && share >= TAKEOVER_SHARE) open = time
        else if (start != null && share < TAKEOVER_SHARE) {
            spans += Takeover(start, time)
            open = null
        }
    }

    /** The takeovers, with one still going on closed at [time]: for a flight that ends during it. */
    fun takeoversAt(time: Float): List<Takeover> = open?.let { spans + Takeover(it, time) } ?: spans
}
