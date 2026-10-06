/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

/** Fixed simulation step: 1/120 s. */
const val PHYSICS_STEP = 1f / 120f

/** Most fixed steps one `advance` runs. */
const val MAX_STEPS_PER_ADVANCE = 8

/** Downward acceleration along Y, m/s². */
const val GRAVITY = 9.81f

/** A body of the simulation, as a game acts on it between steps. */
interface PhysicsBody {
    val entity: Entity
    val motionType: MotionType

    /** Mass in kilograms; `0` for a static or kinematic body. */
    val mass: Float

    /** Adds [force] (N, world space) at the centre of mass for every step of the next `advance`. */
    fun applyForce(force: Vector3)

    /** Adds [torque] (N·m, world space) for every step of the next `advance`. */
    fun applyTorque(torque: Vector3)

    /** Linear velocity in m/s, into [out]. */
    fun velocity(out: Vector3 = Vector3()): Vector3

    /** Angular velocity in rad/s, into [out]. */
    fun angularVelocity(out: Vector3 = Vector3()): Vector3

    fun setVelocity(velocity: Vector3)

    /** For a kinematic body: moves it to [position] / [rotation] over the next [seconds]. */
    fun moveKinematic(position: Vector3, rotation: Quaternion, seconds: Float)

    /**
     * For a dynamic or kinematic body: puts it at [position] / [rotation] at once, at rest, and writes that pose to
     * its entity's `PositionComponent` (a game restarting a run).
     */
    fun setPose(position: Vector3, rotation: Quaternion)
}

/** Two bodies that touched during the last `advance`, and the speed they met at (m/s). [first]: a new contact. */
data class Contact(val a: Entity, val b: Entity, val relativeSpeed: Float, val first: Boolean)

/** A constraint of the simulation, from a [ConstraintComponent] or added by a game. */
interface PhysicsConstraint {
    val kind: ConstraintKind

    /** The entity that holds the constraint, or the first body of one a game added. */
    val entity: Entity

    /** The other body's entity; null when the constraint holds to a fixed point in the world. */
    val other: Entity?

    /** For a distance constraint: the force along it in newtons after the last step, `0` while slack. */
    val tension: Float

    /** The anchors in world space after the last step, into [a] and [b]. */
    fun anchors(a: Vector3, b: Vector3)
}
