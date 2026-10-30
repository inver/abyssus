/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.pattern.BASE_HEIGHT
import kotlin.math.atan
import kotlin.math.sin

/** Below this airspeed (m/s) the handle stays neutral: the elevator does little and the plane is still rolling. */
const val CONTROL_SPEED = 8f

/** Gravity (m/s²), as the physics has it. */
private const val GRAVITY = 9.81f

/**
 * The tracking half of the bot (design decision 2): from one [FlightObservation] and a [Course], the tilt that turns the
 * plane onto it. Pure and GL-free.
 *
 * The plane only pitches: its up stays across the lines, so the elevator turns it within the sphere's tangent plane, to
 * the plane's up when pulled. A turn's sign is therefore measured about the place `u` from the hemisphere's centre
 * (positive is left seen from outside) and turned into a pull or a push by which side the plane's up is on; an inverted
 * plane needs no special case, and nothing is singular over the pilot.
 *
 * The wanted curvature is the course's own [lead] metres ahead (feed-forward) plus a pursuit of the point [lookAhead]
 * seconds of flight ahead on the course's tangent, limited to what the wing gives before the stall. [Handling] turns it
 * into a tilt; the difference from the turn the plane makes now (its angular velocity about `u`) damps the rest.
 */
class F2bPilot(
    private val handling: Handling,
    private val lookAhead: Float = 0.2f,
    private val lead: Float = 0.06f,
    private val minLookAhead: Float = 2.5f,
    private val rateGain: Float = 1.2f,
) {
    /** What the last step measured and wanted, for diagnostics. */
    var curvature = 0f
        private set
    var crossTrack = 0f
        private set
    var headingError = 0f
        private set

    /** The tilt that steers the plane of [o] along [course]; neutral below [CONTROL_SPEED]. */
    fun tilt(o: FlightObservation, course: Course): Float {
        val u = Vector3(o.offset).sub(0f, BASE_HEIGHT, 0f)
        if (u.len2() < 1e-6f) return 0f
        u.nor()
        val speed = o.velocity.len()
        // the course follows the plane on the ground roll too, so its progress shows the plane moving
        val aim = course.aim(u, speed * lead)
        val along = tangentAt(u, o.velocity)
        if (speed < CONTROL_SPEED || !along.x.isFinite()) return 0f
        val left = Vector3(aim.point).crs(aim.tangent)
        crossTrack = Vector3(u).sub(aim.point).dot(left) * course.radius
        headingError = turn(along, tangentAt(u, aim.tangent), u)
        val distance = (speed * lookAhead).coerceAtLeast(minLookAhead)
        val eta = headingError - atan(crossTrack / distance)
        val wanted = (aim.curvature + 2f * sin(eta) / distance).coerceIn(-handling.maxCurvature, handling.maxCurvature)
        curvature = wanted
        // a pull turns towards the plane's up: left when it is on the left of the way it flies
        val side = if (o.up.dot(Vector3(u).crs(along)) >= 0f) 1f else -1f
        val towardsUp = wanted * side
        val turning = o.angularVelocity.dot(u) / speed * side
        val gravityUp = -GRAVITY * o.up.y
        val tilt = handling.tilt(towardsUp, gravityUp, speed) + rateGain * (towardsUp - turning)
        return if (tilt.isFinite()) tilt.coerceIn(-1f, 1f) else 0f
    }
}
