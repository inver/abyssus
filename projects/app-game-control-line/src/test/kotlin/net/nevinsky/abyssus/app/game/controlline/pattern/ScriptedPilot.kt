/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import net.nevinsky.abyssus.app.game.controlline.flight.FieldFlight
import net.nevinsky.abyssus.app.game.controlline.track.TrackSample
import net.nevinsky.abyssus.app.game.controlline.track.wrapDegrees

/**
 * Flies a [FieldFlight] by feedback on the handle: each step the tilt is proportional to how far the plane's climb angle is
 * from the one [target] asks for, with the sign turned over while the plane is inverted (its elevator then pitches it
 * towards the ground).
 */
class ScriptedPilot(private val f: FieldFlight, private val gain: Float = 0.08f, private val damping: Float = 0.004f) {
    /** The climb angle (degrees) wanted for [sample], or null to leave the handle neutral. */
    var target: (TrackSample) -> Float? = { 0f }

    /** The tilt the last [step] set. */
    var tilt = 0f
        private set

    private var lastClimb = 0f

    /** One fixed step with the handle set from the last sample. */
    fun step() {
        val sample = f.flight.lastSample
        val wanted = sample?.let(target)
        tilt = if (sample == null || wanted == null) 0f else {
            val error = wrapDegrees(wanted - sample.climb)
            val rate = wrapDegrees(sample.climb - lastClimb) * 120f
            lastClimb = sample.climb
            (gain * error - damping * rate).coerceIn(-1f, 1f) * (if (sample.inverted) -1f else 1f)
        }
        f.rig.tilt = tilt
        f.step()
    }

    /** Steps until [done] (checked after each step), the flight ends, or [limit] seconds pass; returns whether [done] held. */
    fun until(limit: Float, done: () -> Boolean): Boolean {
        var steps = Math.round(limit * 120f)
        while (steps-- > 0 && !f.flight.finished) {
            step()
            if (done()) return true
        }
        return false
    }
}
