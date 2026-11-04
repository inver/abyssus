/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.AIRBORNE_HEIGHT
import net.nevinsky.abyssus.app.game.controlline.flight.CONTROL_TENSION
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max

/** Pull-out margin (m) at and above which ground danger is 0, and at and below which it is 1. */
const val GROUND_SAFE = 1.2f
const val GROUND_LOST = 0.4f

/** Height (m) of an inverted plane at and above which danger is 0, and at and below which it is 1. */
const val INVERTED_SAFE = 0.9f
const val INVERTED_LOST = 0.4f

/** Seconds of slack lines in the air before slack danger starts to rise, and when it reaches 1. */
const val SLACK_SAFE = 0.4f
const val SLACK_LOST = 1.2f

/** The time (s) of sinking the pull-out margin allows for the handle to act. */
private const val REACTION = 0.15f

/** An upright plane sinking slower than this (m/s) is landing, not diving. */
private const val LANDING_SINK = 1.5f

/**
 * How near a flight is to ending badly (design decision 2 of `add-control-line-trainer`), from 0 to 1: the largest of
 * the ground, inverted and slack terms of `control-line-instructor`. Call [danger] once per fixed step; it keeps the
 * time the lines have been slack. Pure and GL-free.
 */
class DangerGauge(handling: Handling) {
    /** The tightest turn radius (m) short of the stall. */
    private val radius = 1f / handling.maxCurvature

    private var slack = 0f
    private var flown = false

    fun reset() {
        slack = 0f
        flown = false
    }

    /** The danger of the plane of [o], one fixed step on from the last call. */
    fun danger(o: FlightObservation): Float {
        if (o.height > AIRBORNE_HEIGHT) {
            flown = true
        }
        val speed = o.velocity.len()
        val airborne = o.height > AIRBORNE_HEIGHT
        slack = if (airborne && o.tension < CONTROL_TENSION) {
            slack + PHYSICS_STEP
        } else {
            0f
        }
        if (!flown || speed < CONTROL_SPEED) {
            return 0f
        }
        val upright = o.up.y >= 0f
        return maxOf(ground(o, speed, upright), if (upright) {
            0f
        } else {
            ramp(o.height, INVERTED_LOST, INVERTED_SAFE)
        }, ramp(slack, SLACK_SAFE, SLACK_LOST, rising = true))
    }

    private fun ground(o: FlightObservation, speed: Float, upright: Boolean): Float {
        val sink = max(0f, -o.velocity.y)
        if (sink <= 0f || (upright && sink < LANDING_SINK)) {
            return 0f
        }
        val gamma = asin((sink / speed).coerceIn(0f, 1f))
        val margin = o.height - radius * (1f - cos(gamma)) - sink * REACTION
        return ramp(margin, GROUND_LOST, GROUND_SAFE)
    }

    /** 1 at [low] and below, 0 at [high] and above (or the other way round when [rising]), linear between. */
    private fun ramp(value: Float, low: Float, high: Float, rising: Boolean = false): Float {
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return if (rising) {
            t
        } else {
            1f - t
        }
    }
}
