/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flight

import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.physics.Contact

/** How a flight ended, with the text GAME OVER shows. */
enum class FlightEnd(val label: String) {
    CRASHED("Crashed"),
    LINES_SLACK("Lines went slack"),
    LANDED("Landed"),
}

/** Faster than this into the ground (m/s) is a crash. */
const val CRASH_SPEED = 3f

/** Slack lines in the air for this long (s) are a crash. */
const val SLACK_TIME = 2f

/** Higher than this above the ground (m) the plane is in the air. */
const val AIRBORNE_HEIGHT = 0.5f

/**
 * Decides when a flight ends (design decision 5), from what happened during the steps since the last check. A touch of
 * the [field] counts once the plane has been in the air, or at any time upside down:
 * - upside down, or coming down faster than [CRASH_SPEED]: crashed;
 * - otherwise, after the engine has stopped: landed; while it runs the flight goes on.
 *
 * The speed of a touch is how fast the plane came down onto the ground just before it (its sink speed), so a
 * plane gliding in on its wheels can land.
 * Lines below [CONTROL_TENSION] for [SLACK_TIME] while in the air end it as [FlightEnd.LINES_SLACK].
 */
class FlightOutcome(private val plane: Entity, private val field: Entity?) {
    private var flown = false
    private var slack = 0f

    /** Whether the lines are slack in the air right now. */
    var slackInAir = false
        private set

    fun reset() {
        flown = false
        slack = 0f
        slackInAir = false
    }

    /**
     * After [seconds] of steps: [contacts] of those steps, the plane's [sinkSpeed] before them (m/s, positive
     * downward), whether it is [upright], [airborne] and its [engineRunning], and the lines' [tension]. Returns how the
     * flight ended, or null while it goes on.
     */
    fun check(
        seconds: Float, contacts: List<Contact>, sinkSpeed: Float, upright: Boolean, airborne: Boolean,
        engineRunning: Boolean, tension: Float,
    ): FlightEnd? {
        if (airborne) flown = true
        val touched = field != null && contacts.any { (it.a == plane && it.b == field) || (it.b == plane && it.a == field) }
        if (touched && (flown || !upright)) {
            if (!upright || sinkSpeed > CRASH_SPEED) return FlightEnd.CRASHED
            if (!engineRunning) return FlightEnd.LANDED
        }
        slackInAir = airborne && tension < CONTROL_TENSION
        slack = if (slackInAir) slack + seconds else 0f
        return if (slack >= SLACK_TIME) FlightEnd.LINES_SLACK else null
    }
}
