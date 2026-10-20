/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flight

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.lib.physics.PHYSICS_STEP

/**
 * What a [FlightControlSource] sees before a fixed step: the state the steps so far left, never the interpolated pose
 * the plane is drawn at. Every vector is a new copy the source may keep. Before the first step it is the plane at rest
 * at takeoff, at [time] 0 with no [tension].
 *
 * - [offset]: the plane's position minus the pilot's ground point (m), as [net.nevinsky.abyssus.app.game.controlline.track.TrackSample.offset];
 * - [velocity] and [angularVelocity]: world space (m/s, rad/s);
 * - [nose] and [up]: the plane's +Z and +Y (unit, world);
 * - [height]: above the ground (m);
 * - [tension]: the lines' combined tension (N);
 * - [fuelLeft]: seconds of thrust left, [engineRunning] whether it still gives thrust;
 * - [end]: how the flight ended, or null while it goes on.
 */
class FlightObservation(
    val time: Float,
    val offset: Vector3,
    val velocity: Vector3,
    val angularVelocity: Vector3,
    val nose: Vector3,
    val up: Vector3,
    val height: Float,
    val tension: Float,
    val fuelLeft: Float,
    val engineRunning: Boolean,
    val lineLength: Float,
    val end: FlightEnd?,
)

/**
 * Who holds the handle (design decision 1 of `add-f2b-ai-demonstration`): [FlightSession] asks it once per fixed step,
 * before the step's forces, for the tilt from the last [FlightObservation]. A tilt outside -1..1 is clamped and one that
 * is not finite is neutral. It is called on the game's main thread only, and not while the session is not advanced
 * (pause), so its state stays where the flight is.
 */
fun interface FlightControlSource {
    fun tilt(observation: FlightObservation): Float
}

/** The player's handle: [input]'s keys ramp by one fixed step per call, so a human flight steps as it always has. */
class HumanControl(val input: HandleInput) : FlightControlSource {
    override fun tilt(observation: FlightObservation): Float {
        input.update(PHYSICS_STEP)
        return input.tilt
    }
}
