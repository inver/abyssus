/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.jolt

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.physics.MotionType
import net.nevinsky.abyssus.physics.PHYSICS_STEP
import kotlin.math.max

/** A rope is slack while its anchors are more than this closer than its maximum (m). */
private const val SLACK = 0.001f

/**
 * Measures each rope's tension from the motion of the body it pulls, since jolt-jni exposes no constraint impulse.
 * Before a step, the pulled body's velocity and the acceleration the game and gravity give it are recorded; after it,
 * the force the ropes applied is `m * (v_after - (v_before + a * dt)) / dt`. A rope whose anchors are closer than its
 * maximum (less 1 mm) reads `0`. Ropes pulling the same body share that force by how far each points along it.
 *
 * The pulled body is the other entity's when it is dynamic, else the holding entity's. Contacts and damping during the
 * step also change the velocity, so the value is an estimate (see `physics/README.md`).
 */
internal class RopeTension(private val world: PhysicsWorld) {
    inner class Measured(val rope: PhysicsWorld.ConstraintRecord, val body: PhysicsWorld.BodyRecord, val bodyIsOther: Boolean)

    inner class Step(private val measured: List<Measured>, private val predicted: Map<PhysicsWorld.BodyRecord, Vector3>) {
        fun after() {
            val a = Vector3()
            val b = Vector3()
            for ((body, group) in measured.groupBy { it.body }) {
                val after = world.velocityOf(body, Vector3())
                val force = after.sub(predicted.getValue(body)).scl(body.mass / PHYSICS_STEP)
                val shares = group.map { m ->
                    world.anchorsOf(m.rope, a, b)
                    val distance = a.dst(b)
                    if (distance < m.rope.maxDistance - SLACK || distance == 0f) 0f
                    else {
                        // towards the anchor the pulled body is held by
                        val toward = if (m.bodyIsOther) Vector3(a).sub(b) else Vector3(b).sub(a)
                        max(0f, force.dot(toward.scl(1f / distance)))
                    }
                }
                val total = shares.sum()
                group.forEachIndexed { i, m -> m.rope.tension = if (total > 0f) shares[i] * shares[i] / total else 0f }
            }
        }
    }

    /** Records what [after][Step.after] needs; ropes without a dynamic body read `0`. */
    fun before(constraints: List<PhysicsWorld.ConstraintRecord>): Step {
        val measured = ArrayList<Measured>()
        val predicted = HashMap<PhysicsWorld.BodyRecord, Vector3>()
        for (c in constraints) {
            if (!c.rope) continue
            val other = world.recordOf(c.other)
            val own = world.recordOf(c.entity)
            val m = when {
                other?.motionType == MotionType.DYNAMIC -> Measured(c, other, true)
                own?.motionType == MotionType.DYNAMIC -> Measured(c, own, false)
                else -> null
            }
            if (m == null) {
                c.tension = 0f
                continue
            }
            measured += m
            predicted.getOrPut(m.body) {
                world.velocityOf(m.body, Vector3()).mulAdd(m.body.acceleration(Vector3()), PHYSICS_STEP)
            }
        }
        return Step(measured, predicted)
    }
}
