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
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP
import net.nevinsky.abyssus.lib.physics.PhysicsConstraint
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import kotlin.math.asin
import kotlin.math.atan2

/** The lines' combined tension (N) at or above which the elevator follows the handle. */
const val CONTROL_TENSION = 1f

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

    private val pilotBody = requireNotNull(world.bodyOf(pilot)) { "the pilot has no body: give it a kinematic rigid body and a collider" }
    private val planeBody = requireNotNull(world.bodyOf(plane)) { "the plane has no body: give it a rigid body and a collider" }
    private val pilotPosition = Vector3(pilot.getComponent(PositionComponent::class.java).localPosition)

    /** The plane's pitch (rad) and height above the ground as parked, which takeoff keeps. */
    private val parkedPitch: Float
    private val parkedClearance: Float

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
    val tension: Float get() = lines.sumOf { it.tension.toDouble() }.toFloat()

    /** The elevator's deflection (rad): the handle's through taut lines, neutral through slack ones. */
    val elevator: Float get() = if (tension >= CONTROL_TENSION) tilt * ELEVATOR_THROW else 0f

    /**
     * Puts the plane on the ground at its line length along the circle at [azimuth] degrees, at rest and pointing in
     * the flying direction (anticlockwise seen from above), with the pitch and height above the ground it was parked
     * with; turns the pilot to it and attaches new lines, taut.
     */
    fun takeoff(azimuth: Float = 0f) {
        for (line in lines) world.remove(line)
        lines = emptyList()
        tilt = 0f
        val a = azimuth * MathUtils.degreesToRadians
        val rotation = Quaternion(Vector3.Y, azimuth + 180f).mul(Quaternion(Vector3.X, -parkedPitch * MathUtils.radiansToDegrees))
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
        var low = 0f
        var high = planeSettings.lineLength * 2f
        repeat(40) {
            val mid = (low + high) / 2
            if (longest(mid) < planeSettings.lineLength) low = mid else high = mid
        }
        planeBody.setPose(position(low), rotation)
        pilotBody.setPose(pilotPosition, facing(azimuth))
        lines = handlePoints().zip(leadouts) { from, to -> world.addRope(pilot, from, plane, to, planeSettings.lineLength) }
    }

    /** Before a step: turns the pilot and the handle to face the plane. */
    fun beforeStep(seconds: Float = PHYSICS_STEP) {
        pilotBody.moveKinematic(pilotPosition, facing(azimuthOfPlane()), seconds)
    }

    /** The plane's azimuth around the pilot, in degrees (anticlockwise seen from above). */
    fun azimuthOfPlane(): Float {
        val p = plane.getComponent(PositionComponent::class.java).localPosition
        return atan2(-(p.z - pilotPosition.z), p.x - pilotPosition.x) * MathUtils.radiansToDegrees
    }

    /** The pilot's rotation facing azimuth [degrees]: the figure looks along +Z. */
    private fun facing(degrees: Float) = Quaternion(Vector3.Y, 90f + degrees)
}
