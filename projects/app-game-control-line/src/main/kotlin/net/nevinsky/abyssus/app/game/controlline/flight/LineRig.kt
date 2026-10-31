/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.PilotComponent
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.util.azimuthOf
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import net.nevinsky.abyssus.lib.physics.PhysicsConstraint
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import kotlin.math.asin
import kotlin.math.sqrt

/** The lines' combined tension (N) at or above which the elevator follows the handle. */
const val CONTROL_TENSION = 1f

/** The Jolt ropes are this fraction longer than the lines' rest length: a backstop behind the wire's spring. */
const val ROPE_MARGIN = 0.02f

/** The pull (N) the pilot holds each line at when the plane is put on the ground. */
const val TAKEOFF_PULL = 3f

/** The damping of a line's spring, as a fraction of critical. */
const val DAMPING_RATIO = 0.1f

/**
 * The pilot, the handle and the two lines (design decision 4). The pilot's entity is a kinematic body; the lines are
 * ropes of the plane's line length from two points on its vertical axis, `handleSpacing / 2` above and below the handle
 * height, to the plane's up and down leadouts. They pull when taut and go slack.
 *
 * The handle's [tilt] (-1 full down .. 1 full up) moves the elevator directly, but only through taut lines.
 */
class LineRig(private val world: PhysicsWorld, val pilot: Entity, val plane: Entity, private val ground: Ground) {
    val pilotSettings: PilotComponent = pilot.getComponent(PilotComponent::class.java)
    val planeSettings: PlaneComponent = plane.getComponent(PlaneComponent::class.java)

    private val pilotBody =
        requireNotNull(world.bodyOf(pilot)) { "the pilot has no body: give it a kinematic rigid body and a collider" }
    private val planeBody =
        requireNotNull(world.bodyOf(plane)) { "the plane has no body: give it a rigid body and a collider" }
    private val pilotPosition = Vector3(pilot.getComponent(PositionComponent::class.java).localPosition)

    /** The plane's pitch (rad) and height above the ground as parked, which takeoff keeps. */
    private val parkedPitch: Float
    private val parkedClearance: Float

    /** The lines' steel wire. */
    val wire = Wire(planeSettings.lineDiameter)

    /** What both lines add to the plane's mass (kg): a third of each wire, as it swings about the handle. */
    val wireMass: Float get() = 2f * wire.tipMass(planeSettings.lineLength)

    private val lineTension = FloatArray(2)

    /** One line's tension (N) in the last step; [index] 0 is the up line. */
    fun tensionOf(index: Int): Float = lineTension[index]

    private val from = Vector3()
    private val to = Vector3()
    private val direction = Vector3()
    private val arm = Vector3()
    private val point = Vector3()
    private val force = Vector3()
    private val torque = Vector3()
    private val velocity = Vector3()
    private val spin = Vector3()

    /** The lines, up line first; empty before [takeoff]. */
    var lines: List<PhysicsConstraint> = emptyList()
        private set

    /** -1 (full down) .. 1 (full up). */
    var tilt = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
        }

    init {
        val parked = plane.getComponent(PositionComponent::class.java)
        val nose = parked.localRotation.transform(Vector3(0f, 0f, 1f))
        parkedPitch = asin(nose.y.coerceIn(-1f, 1f))
        parkedClearance = parked.localPosition.y - ground.heightAt(parked.localPosition.x, parked.localPosition.z)
    }

    /** The handle's centre, in world space. */
    val handle: Vector3 get() = Vector3(pilotPosition).add(0f, pilotSettings.handleHeight, 0f)

    /** The two points the lines leave the handle from (up line first), on the pilot's vertical axis, local to it. */
    private fun handlePoints(): List<Vector3> = listOf(
        Vector3(0f, pilotSettings.handleHeight + pilotSettings.handleSpacing / 2, 0f),
        Vector3(0f, pilotSettings.handleHeight - pilotSettings.handleSpacing / 2, 0f),
    )

    /** The lines' combined tension after the last step (N). */
    val tension: Float get() = lineTension.sum()

    /** The elevator's deflection (rad): the handle's through taut lines, neutral through slack ones. */
    val elevator: Float get() = if (tension >= CONTROL_TENSION) tilt * ELEVATOR_THROW else 0f

    /**
     * Puts the plane on the ground at its line length along the circle at [azimuth] degrees, at rest and pointing in
     * the flying direction (anticlockwise seen from above), with the pitch and height above the ground it was parked
     * with; turns the pilot to it and attaches new lines, taut.
     */
    fun takeoff(azimuth: Float = 0f) {
        for (line in lines) {
            world.remove(line)
        }
        lines = emptyList()
        tilt = 0f
        val a = azimuth * MathUtils.degreesToRadians
        val rotation =
            Quaternion(Vector3.Y, azimuth + 180f).mul(Quaternion(Vector3.X, -parkedPitch * MathUtils.radiansToDegrees))
        val leadouts = listOf(planeSettings.leadoutUp, planeSettings.leadoutDown)
        val points = handlePoints().map { Vector3(it).add(pilotPosition) }

        // the radius at which the longer line is exactly its length
        fun position(radius: Float): Vector3 {
            val x = pilotPosition.x + radius * MathUtils.cos(a)
            val z = pilotPosition.z - radius * MathUtils.sin(a)
            return Vector3(x, ground.heightAt(x, z) + parkedClearance, z)
        }

        fun longest(radius: Float): Float {
            val p = position(radius)
            return points.indices.maxOf { i -> rotation.transform(Vector3(leadouts[i])).add(p).dst(points[i]) }
        }

        // the pilot leaves the wire a little stretched, so the lines pull from the first step
        val pretension = wire.stretch(planeSettings.lineLength, TAKEOFF_PULL)
        var low = 0f
        var high = planeSettings.lineLength * 2f
        repeat(40) {
            val mid = (low + high) / 2
            if (longest(mid) < planeSettings.lineLength + pretension) low = mid else high = mid
        }
        planeBody.setPose(position(low), rotation)
        pilotBody.setPose(pilotPosition, facing(azimuth))
        lines = handlePoints().zip(leadouts) { from, to -> world.addRope(pilot, from, plane, to, planeSettings.lineLength * (1f + ROPE_MARGIN)) }
        lineTension.fill(0f)
    }

    /**
     * The wire's pull on the plane for the next step: each line is a spring of stiffness `E * A / L` (with a little
     * damping, so it does not ring) once it is longer than its rest length, applied at its leadout. The Jolt ropes are a
     * backstop [ROPE_MARGIN] longer, which the spring never lets the plane reach.
     */
    private fun pull(seconds: Float) {
        val rest = planeSettings.lineLength
        val k = wire.stiffness(rest)
        val body = planeBody
        val centre = plane.getComponent(PositionComponent::class.java).localPosition
        body.velocity(velocity)
        body.angularVelocity(spin)
        val damping = 2f * DAMPING_RATIO * sqrt(k * body.mass)
        lines.forEachIndexed { i, line ->
            line.anchors(from, to)
            val length = from.dst(to)
            if (length <= rest || length == 0f) {
                lineTension[i] = 0f
                return@forEachIndexed
            }
            direction.set(from).sub(to).scl(1f / length)
            arm.set(to).sub(centre)
            point.set(spin).crs(arm).add(velocity)
            val pulled = k * (length - rest) - damping * point.dot(direction)
            lineTension[i] = pulled.coerceAtLeast(0f)
            force.set(direction).scl(lineTension[i])
            body.applyForce(force)
            body.applyTorque(torque.set(arm).crs(force))
        }
    }

    /** Before a step: turns the pilot and the handle to face the plane. */
    fun beforeStep(seconds: Float = PHYSICS_STEP) {
        pull(seconds)
        pilotBody.moveKinematic(pilotPosition, facing(azimuthOfPlane()), seconds)
    }

    /** The plane's azimuth around the pilot, in degrees (anticlockwise seen from above). */
    fun azimuthOfPlane(): Float {
        val p = plane.getComponent(PositionComponent::class.java).localPosition
        return azimuthOf(p.x - pilotPosition.x, p.z - pilotPosition.z)
    }

    /** The pilot's rotation facing azimuth [degrees]: the figure looks along +Z. */
    private fun facing(degrees: Float) = Quaternion(Vector3.Y, 90f + degrees)
}
