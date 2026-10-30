/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// The constants of the flight model that are not scene fields. Tuned with FlightTest; see games/control-line/README.md.
/** Air density, kg/m³. */
const val AIR_DENSITY = 1.225f

/** Every plane's wing aspect ratio: span²/area. Gives the mean chord and the span from the wing area. */
const val ASPECT_RATIO = 6f

/** Past this angle of attack (rad) lift stops growing and drag doubles. */
const val STALL_ANGLE = 15f * MathUtils.degreesToRadians

/** The elevator's deflection at full handle tilt (rad). */
const val ELEVATOR_THROW = 20f * MathUtils.degreesToRadians

/** Pitching moment per radian of angle of attack away from [TRIM_ANGLE]: the stabiliser's restoring moment. */
const val PITCH_STABILITY = -0.6f

/** The angle of attack (rad) a plane settles at with the elevator neutral. */
const val TRIM_ANGLE = 2f * MathUtils.degreesToRadians

/** The fin's side force per radian of sideslip, on the wing area, acting [TAIL_ARM] chords behind the centre of gravity. */
const val FIN_SIDE_FORCE = 0.6f
const val TAIL_ARM = 3f

/** Yaw moment per radian of rudder offset (on wing area and span). */
const val RUDDER_EFFECT = 0.05f

/** Roll and yaw damping, as pitch damping is: moment per unit of the rate's non-dimensional form. */
const val ROLL_DAMPING = 0.5f
const val YAW_DAMPING = 0.3f

/** The drag coefficient of a line, a cylinder across the flow. */
const val LINE_DRAG_COEFFICIENT = 1f

/** Below this airspeed (m/s) there are no aerodynamic forces. */
private const val STILL = 0.01f

/** The force and torque (world space) on a plane for the next step, and what made them. */
class AeroForces {
    val force = Vector3()
    val torque = Vector3()
    var airspeed = 0f
    var angleOfAttack = 0f
    var lift = 0f
    var drag = 0f
    var thrust = 0f
    var lineDrag = 0f
}

/**
 * The plane's aerodynamics as pure functions of its state and its [PlaneComponent] (design decision 3). The plane's
 * frame: nose +Z, up +Y, inboard wing +X.
 */
class Aero {
    /** Mean chord (m): a wing of [ASPECT_RATIO] and [area]. */
    fun chord(area: Float): Float = sqrt(area / ASPECT_RATIO)

    fun span(area: Float): Float = sqrt(area * ASPECT_RATIO)

    /** Lift coefficient at [alpha] (rad): linear, held at its stall value past [STALL_ANGLE]. */
    fun liftCoefficient(plane: PlaneComponent, alpha: Float): Float =
        plane.liftSlope * (if (abs(alpha) > STALL_ANGLE) sign(alpha) * STALL_ANGLE else alpha)

    /** Drag coefficient at [alpha] (rad): zero-lift plus induced drag, doubled past [STALL_ANGLE]. */
    fun dragCoefficient(plane: PlaneComponent, alpha: Float): Float {
        val cl = liftCoefficient(plane, alpha)
        val cd = plane.zeroLiftDrag + cl * cl / (PI.toFloat() * ASPECT_RATIO)
        return if (abs(alpha) > STALL_ANGLE) 2f * cd else cd
    }

    /** The engine's thrust (N) [flightTime] seconds after takeoff: full until the fuel is gone, then 0. */
    fun thrust(plane: PlaneComponent, flightTime: Float): Float = if (flightTime < plane.fuelTime) plane.thrust else 0f

    /** The drag of one line (N) at [airspeed]: `rho * Cd * d * L * V² / 8`, a line moving from 0 at the handle to V. */
    fun lineDrag(plane: PlaneComponent, airspeed: Float): Float =
        AIR_DENSITY * LINE_DRAG_COEFFICIENT * plane.lineDiameter * plane.lineLength * airspeed * airspeed / 8f

    /**
     * The force and torque on the plane at [rotation], moving at [velocity] and turning at [angularVelocity] (world
     * space), with the elevator at [elevator] rad (positive pitches up) and the engine giving [thrust] N; into [out].
     */
    fun forces(
        plane: PlaneComponent, rotation: Quaternion, velocity: Vector3, angularVelocity: Vector3,
        elevator: Float, thrust: Float, out: AeroForces,
    ): AeroForces {
        out.force.setZero()
        out.torque.setZero()
        val forward = rotation.transform(Vector3(0f, 0f, 1f))
        val up = rotation.transform(Vector3(0f, 1f, 0f))
        val left = rotation.transform(Vector3(1f, 0f, 0f))

        // thrust along the nose, turned outward (away from the inboard wing) by the engine offset
        val offset = plane.engineOffset * MathUtils.degreesToRadians
        out.thrust = thrust
        out.force.mulAdd(forward, thrust * cos(offset)).mulAdd(left, -thrust * sin(offset))

        val v = velocity.len()
        out.airspeed = v
        if (v < STILL) {
            out.angleOfAttack = 0f
            out.lift = 0f
            out.drag = 0f
            out.lineDrag = 0f
            return out
        }
        val q = 0.5f * AIR_DENSITY * v * v
        val area = plane.wingArea
        val c = chord(area)
        val b = span(area)
        val along = velocity.dot(forward)
        val alpha = atan2(-velocity.dot(up), along)
        val beta = atan2(velocity.dot(left), along)
        out.angleOfAttack = alpha

        val direction = Vector3(velocity).scl(1f / v)
        val liftDirection = Vector3(velocity).crs(left).nor()
        out.lift = q * area * liftCoefficient(plane, alpha)
        out.drag = q * area * dragCoefficient(plane, alpha)
        out.lineDrag = 2f * lineDrag(plane, v)
        out.force.mulAdd(liftDirection, out.lift).mulAdd(direction, -(out.drag + out.lineDrag))

        // pitch, about the right wing (-X): elevator, stability towards the trim angle, damping
        val right = Vector3(left).scl(-1f)
        val pitchRate = angularVelocity.dot(right)
        val pitch = q * area * c * (plane.elevatorEffect * elevator + PITCH_STABILITY * (alpha - TRIM_ANGLE) -
            plane.pitchDamping * c * pitchRate / (2f * v))
        out.torque.mulAdd(right, pitch)

        // the fin: a side force against the sideslip, behind the centre of gravity, so it also turns the nose into the wind
        val side = -q * area * FIN_SIDE_FORCE * beta
        out.force.mulAdd(left, side)
        out.torque.mulAdd(up, -side * TAIL_ARM * c)

        // the rudder offset turns the nose outward; yaw and roll are damped
        val yawRate = angularVelocity.dot(up)
        val rollRate = angularVelocity.dot(forward)
        out.torque.mulAdd(up, -q * area * b * (RUDDER_EFFECT * plane.rudderOffset * MathUtils.degreesToRadians + YAW_DAMPING * b * yawRate / (2f * v)))
        out.torque.mulAdd(forward, -q * area * b * ROLL_DAMPING * b * rollRate / (2f * v))
        return out
    }
}
