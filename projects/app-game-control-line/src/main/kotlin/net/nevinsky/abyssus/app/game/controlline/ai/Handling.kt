/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.flight.AIR_DENSITY
import net.nevinsky.abyssus.app.game.controlline.flight.ASPECT_RATIO
import net.nevinsky.abyssus.app.game.controlline.flight.ELEVATOR_THROW
import net.nevinsky.abyssus.app.game.controlline.flight.PITCH_STABILITY
import net.nevinsky.abyssus.app.game.controlline.flight.STALL_ANGLE
import net.nevinsky.abyssus.app.game.controlline.flight.TRIM_ANGLE
import kotlin.math.sqrt

/**
 * How a plane turns on its lines, from its own [PlaneComponent] and [mass] and the flight model's constants: the steady
 * curvature (1/m, towards the plane's up) at a handle tilt, from the balance of the elevator, the stabiliser and the
 * pitch damping against the lift. It is the pilot's feed-forward; feedback corrects what it leaves out.
 */
class Handling(plane: PlaneComponent, mass: Float) {
    /** Lift acceleration per V² per radian of angle of attack (1/m). */
    private val lift = 0.5f * AIR_DENSITY * plane.wingArea * plane.liftSlope / mass

    /** The pitch damping's angle of attack per unit of curvature (m·rad): `pitchDamping * chord / 2 / -PITCH_STABILITY`. */
    private val damping = plane.pitchDamping * sqrt(plane.wingArea / ASPECT_RATIO) / 2f / -PITCH_STABILITY
    private val perElevator = plane.elevatorEffect / -PITCH_STABILITY
    private val denominator = 1f + lift * damping

    /** The tightest curvature the pilot asks for (1/m): what lift alone gives just short of the stall. */
    val maxCurvature: Float = lift * STALL_ANGLE * 0.85f

    /** The tilt that holds a steady turn of [curvature] (1/m, towards the plane's up) with [gravityUp] m/s² of gravity along the plane's up, at [speed] m/s. */
    fun tilt(curvature: Float, gravityUp: Float, speed: Float): Float {
        val v2 = (speed * speed).coerceAtLeast(1f)
        // κ (1 + lift·damping) = lift (TRIM + perElevator·elevator) + gravityUp / V²
        val elevator = (curvature * denominator - lift * TRIM_ANGLE - gravityUp / v2) / (lift * perElevator)
        return elevator / ELEVATOR_THROW
    }
}
