/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ai

import net.nevinsky.abyssus.app.game.controlline.flight.FlightControlSource
import net.nevinsky.abyssus.app.game.controlline.flight.FlightObservation
import net.nevinsky.abyssus.app.game.controlline.flight.Takeover

/**
 * The instructor beside the student (design decision 1 of `add-control-line-trainer`): each step it asks the [student]
  * (the player's handle) for its tilt, measures the danger, steps the share and asks the [rescue] for its tilt, and
  * holds
 * the handle at `(1 - share) * student + share * rescue`. The rescue is only asked while there is danger or a share, so
 * the ghost handle has its tilt exactly then. Main thread only, like any [FlightControlSource].
 */
class InstructorControl(
    private val student: FlightControlSource,
    private val gauge: DangerGauge,
    private val rescue: RescueSource,
    private val shares: InstructorShare = InstructorShare(),
) : FlightControlSource {
    /** The danger of the last step, 0 to 1. */
    var danger = 0f
        private set

    /** The player's and the instructor's tilts of the last step; the instructor's is 0 while it is not asked. */
    var playerTilt = 0f
        private set
    var rescueTilt = 0f
        private set

    /** The instructor's share of the handle, 0 to 1. */
    val share: Float get() = shares.share

    /** Whether the instructor holds at least half the handle: the INSTRUCTOR flag and a takeover. */
    val holding: Boolean get() = shares.inTakeover

    /** Whether the ghost handle shows: there is danger or the instructor holds a share. */
    val ghost: Boolean get() = danger > 0f || shares.share > 0f

    /** The takeovers so far; one still going on at [time] ends there. */
    fun takeovers(time: Float): List<Takeover> = shares.takeoversAt(time)

    override fun tilt(observation: FlightObservation): Float {
        playerTilt = student.tilt(observation).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        danger = gauge.danger(observation)
        shares.step(danger, observation.time)
        rescueTilt = if (ghost) {
            rescue.tilt(observation).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        } else {
            0f
        }
        if (!ghost) {
            rescue.release()
        }
        return (1f - share) * playerTilt + share * rescueTilt
    }
}
