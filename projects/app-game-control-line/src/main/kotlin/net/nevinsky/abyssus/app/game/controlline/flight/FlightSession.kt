/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.physics.MAX_STEPS_PER_ADVANCE
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import org.slf4j.Logger
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** The time constant (s) of the tension the drawn curves use. */
private const val WIRE_TENSION_SMOOTHING = 0.1f

/** The least share of the lines' tension a drawn line is given while they pull. */
private const val WIRE_SHARE = 0.3f

/** A frame never runs more than this much simulation (s), so a stall does not jump the plane. */
private const val MAX_FRAME = 0.1f

/**
 * One flight in the game: a physics world over its own load of the field scene, [plane] on its lines and the fixed-step
 * loop. Each step asks [control] for the handle's tilt from the state the last step left, updates the [flight] and
 * steps the world. After the flight ends the world goes on, so a crashed plane comes to rest, and [control] is no longer
 * asked. Lives on the game's main thread; [close] releases the world.
 */
class FlightSession(
    engine: SceneEngine, pilot: Entity, val plane: Entity, assets: PhysicsAssets, log: Logger, natives: JoltNatives,
    judge: FlightJudge? = null,
    private val control: FlightControlSource = FlightControlSource { 0f },
) : AutoCloseable {
    private val world = PhysicsWorld(engine, assets, log, natives)
    private val ground = groundOf(engine, assets)
    val rig = LineRig(world, pilot, plane, ground)
    val flight = Flight(world, rig, ground).also { it.takeoff(0f, judge) }
    private val planeBody = requireNotNull(world.bodyOf(plane))
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

    /** Runs the fixed steps [seconds] of frame time hold, with the handle from [control]. */
    fun advance(seconds: Float) {
        if (shown) body?.let {
            it.localPosition.set(after)
            it.localRotation.set(afterTurn)
        }
        carried += seconds.coerceIn(0f, MAX_FRAME)
        var steps = 0
        while (carried >= PHYSICS_STEP && steps < MAX_STEPS_PER_ADVANCE) {
            body?.let { before.set(it.localPosition); beforeTurn.set(it.localRotation) }
            if (!flight.finished) rig.tilt = control.tilt(observe()).takeIf { it.isFinite() } ?: 0f
            flight.update(PHYSICS_STEP)
            world.step()
            carried -= PHYSICS_STEP
            steps++
        }
        if (steps == MAX_STEPS_PER_ADVANCE) carried = 0f
        show()
    }

    /** The simulated state, for [control]: only called within [advance], after the drawn pose is put back. */
    private fun observe(): FlightObservation {
        val position = plane.getComponent(PositionComponent::class.java)
        val p = Vector3(position.localPosition)
        val rotation = Quaternion(position.localRotation).nor()
        val handle = rig.handle
        return FlightObservation(
            flight.time,
            Vector3(p).sub(handle.x, handle.y - rig.pilotSettings.handleHeight, handle.z),
            planeBody.velocity(Vector3()),
            planeBody.angularVelocity(Vector3()),
            rotation.transform(Vector3(0f, 0f, 1f)),
            rotation.transform(Vector3(0f, 1f, 0f)),
            p.y - ground.heightAt(p.x, p.z),
            flight.tension,
            flight.fuelLeft,
            flight.fuelLeft > 0f,
            rig.planeSettings.lineLength,
            flight.result,
        )
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

    private val wireShape = WireShape(rig.wire)
    private val shapes = List(2) { List(WIRE_SEGMENTS + 1) { Vector3() } }
    private val smoothTension = FloatArray(2)
    private var smoothed = false
    private val heading = Vector3()
    private val velocity = Vector3()

    /**
     * The two lines as curves from the handle to the leadouts (world space), up line first: shaped by each line's
     * tension (smoothed, so the curve does not flicker), weight and drag. The points are reused by the next call.
     */
    fun wires(seconds: Float = PHYSICS_STEP): List<List<Vector3>> {
        planeBody.velocity(velocity)
        val speed = velocity.len()
        heading.set(velocity).also { if (speed > 1e-3f) it.scl(1f / speed) }
        val blend = 1f - exp(-seconds / WIRE_TENSION_SMOOTHING)
        for (i in smoothTension.indices) {
            smoothTension[i] = if (smoothed) smoothTension[i] + (rig.tensionOf(i) - smoothTension[i]) * blend else rig.tensionOf(i)
        }
        smoothed = true
        val pulled = rig.tension >= CONTROL_TENSION
        return lines().mapIndexed { i, (from, to) ->
            // while the lines pull, both are held taut by the handle: the plane's pitch makes one carry less, and the
            // model's equal rest lengths would leave it with a few centimetres to spare and hang it like thread
            val tension = if (pulled) max(max(smoothTension[i], WIRE_SHARE * smoothTension.sum()), TAKEOFF_PULL) else smoothTension[i]
            val rest = if (pulled) min(rig.planeSettings.lineLength, from.dst(to)) else rig.planeSettings.lineLength
            wireShape.points(from, to, rest, tension, speed, heading, shapes[i])
            shapes[i]
        }
    }

    override fun close() = world.close()
}
