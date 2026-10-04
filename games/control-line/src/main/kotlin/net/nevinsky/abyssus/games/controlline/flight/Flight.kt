/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.games.controlline.score.FlightScoring
import net.nevinsky.abyssus.games.controlline.track.SphereTrack
import net.nevinsky.abyssus.games.controlline.track.TrackSample
import net.nevinsky.abyssus.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import kotlin.math.max

/**
 * One plane flying on its lines: the [rig], the aerodynamic forces, how the flight ends and its score. The same code
 * flies the game and Play in Abyssus.
 *
 * [update] is called before the world steps, with the time those steps will take: it first looks at what the steps
 * since the last call did (the ending, the track and the score), then applies the forces for the next ones. The game
 * calls it before each `world.step()`; Play calls it before each `advance`.
 */
class Flight(
    private val world: PhysicsWorld,
    val rig: LineRig,
    private val ground: Ground,
    private val aero: Aero = Aero(),
    private val track: SphereTrack = SphereTrack(),
) {
    private val plane = rig.plane
    private val body = requireNotNull(world.bodyOf(plane))
    private val outcome = FlightOutcome(plane, ground.field)
    private var started = false
    private val velocity = Vector3()
    private val spin = Vector3()
    private val lastVelocity = Vector3()

    /** The aerodynamic forces of the last update. */
    val forces = AeroForces()

    /** Seconds since takeoff. */
    var time = 0f
        private set

    var scoring = FlightScoring()
        private set

    /** The lines' combined tension after the last step (N). */
    var tension = 0f
        private set

    var lastSample: TrackSample? = null
        private set

    /** How the flight ended; null while it goes on. */
    var result: FlightEnd? = null
        private set

    val finished: Boolean get() = result != null

    /** Seconds of fuel left. */
    val fuelLeft: Float get() = max(0f, rig.planeSettings.fuelTime - time)

    /** Starts a new flight: the plane at takeoff on the circle at [azimuth] degrees, the score at 0. */
    fun takeoff(azimuth: Float = 0f) {
        rig.takeoff(azimuth)
        outcome.reset()
        scoring = FlightScoring()
        time = 0f
        tension = 0f
        result = null
        lastSample = null
        started = false
        lastVelocity.setZero()
    }

    fun update(seconds: Float) {
        if (result != null) return
        val position = plane.getComponent(PositionComponent::class.java)
        val rotation = Quaternion(position.localRotation).nor()
        body.velocity(velocity)
        if (started) {
            tension = rig.tension
            val p = position.localPosition
            val airborne = p.y - ground.heightAt(p.x, p.z) > AIRBORNE_HEIGHT
            val upright = rotation.transform(Vector3(0f, 1f, 0f)).y >= 0f
            val wasSlack = outcome.slackInAir
            val ended = outcome.check(seconds, world.contacts(), -lastVelocity.y, upright, airborne, aero.thrust(rig.planeSettings, time) > 0f, tension)
            val sample = track.sample(time, rig.handle, p, velocity, rotation, airborne)
            lastSample = sample
            scoring.sample(sample)
            if (outcome.slackInAir && !wasSlack) scoring.keeper.linesSlack()
            if (ended != null) {
                result = ended
                scoring.finish(landed = ended == FlightEnd.LANDED)
                return
            }
        }
        started = true
        rig.beforeStep(seconds)
        body.angularVelocity(spin)
        aero.forces(rig.planeSettings, rotation, velocity, spin, rig.elevator, aero.thrust(rig.planeSettings, time), forces)
        body.applyForce(forces.force)
        body.applyTorque(forces.torque)
        lastVelocity.set(velocity)
        time += seconds
    }
}

/** A [Flight] as an Ashley system, for Play's host, which updates its systems before each advance. */
class FlightSystem(val flight: Flight) : EntitySystem() {
    override fun update(deltaTime: Float) = flight.update(deltaTime)
}
