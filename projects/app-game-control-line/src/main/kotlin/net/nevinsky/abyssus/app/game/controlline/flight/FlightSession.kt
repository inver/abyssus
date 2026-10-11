/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.lib.physics.MAX_STEPS_PER_ADVANCE
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import org.slf4j.Logger

/** A frame never runs more than this much simulation (s), so a stall does not jump the plane. */
private const val MAX_FRAME = 0.1f

/**
 * One flight in the game: a physics world over its own load of the field scene, [plane] on its lines and the fixed-step
 * loop. Each step reads the handle from the input, updates the [flight] and steps the world. After the flight ends
 * the world goes on, so a crashed plane comes to rest. Lives on the game's main thread; [close] releases the world.
 */
class FlightSession(
    engine: SceneEngine, pilot: Entity, val plane: Entity, assets: PhysicsAssets, log: Logger, natives: JoltNatives,
    judge: FlightJudge? = null,
) : AutoCloseable {
    private val world = PhysicsWorld(engine, assets, log, natives)
    private val ground = groundOf(engine, assets)
    val rig = LineRig(world, pilot, plane, ground)
    val flight = Flight(world, rig, ground).also { it.takeoff(0f, judge) }
    private var carried = 0f

    // The plane is drawn between its last two simulated poses: a frame holds a whole number of 1/120 s steps plus a
    // remainder, so showing the latest pose alone makes the plane (and the camera that follows it) move in uneven jumps.
    private val body = plane.getComponent(PositionComponent::class.java)
    private val before = Vector3()
    private val beforeTurn = Quaternion()
    private val after = Vector3()
    private val afterTurn = Quaternion()
    private var shown = false

    /** Where the plane is drawn minus where it is simulated (m): the lines' plane ends move with it. */
    private val offset = Vector3()

    /** Runs the fixed steps [seconds] of frame time hold, with the handle from [input]. */
    fun advance(seconds: Float, input: HandleInput) {
        if (shown) body?.let {
            it.localPosition.set(after)
            it.localRotation.set(afterTurn)
        }
        carried += seconds.coerceIn(0f, MAX_FRAME)
        var steps = 0
        while (carried >= PHYSICS_STEP && steps < MAX_STEPS_PER_ADVANCE) {
            body?.let { before.set(it.localPosition); beforeTurn.set(it.localRotation) }
            input.update(PHYSICS_STEP)
            rig.tilt = input.tilt
            flight.update(PHYSICS_STEP)
            world.step()
            carried -= PHYSICS_STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_ADVANCE) carried = 0f
        show()
    }

    /** Puts the plane [carried] of the way from its previous pose to its latest, keeping the latest to restore. */
    private fun show() {
        val component = body ?: return
        after.set(component.localPosition)
        afterTurn.set(component.localRotation)
        if (!shown) {
            before.set(after)
            beforeTurn.set(afterTurn)
        }
        // (before, after) are the poses before and after the last step, and the remainder is how far on from `before`
        // the drawn moment is: the picture runs one step behind the simulation, in return for even motion
        val alpha = (carried / PHYSICS_STEP).coerceIn(0f, 1f)
        component.localPosition.set(after).lerp(before, 1f - alpha)
        component.localRotation.set(afterTurn).slerp(beforeTurn, 1f - alpha)
        offset.set(component.localPosition).sub(after)
        shown = true
    }

    /** The two lines, from the handle to the leadouts (world space), up line first. */
    fun lines(): List<Pair<Vector3, Vector3>> = rig.lines.map { line ->
        val a = Vector3()
        val b = Vector3()
        line.anchors(a, b)
        a to b.add(offset)
    }

    override fun close() = world.close()
}
