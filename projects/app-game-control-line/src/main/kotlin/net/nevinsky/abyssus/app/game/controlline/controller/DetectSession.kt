/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import kotlin.math.abs

/** Detecting stops after this many seconds with nothing taken. */
const val DETECT_SECONDS = 10f

/** An axis counts as moved when it is this far from where it was when detecting started. */
const val DETECT_TRAVEL = 0.5f

enum class DetectKind { AXIS, BUTTON }

/** How a [DetectSession] stands after an update. */
sealed interface DetectOutcome {
    /** Still waiting for the player. */
    data object Waiting : DetectOutcome

    /** The player moved axis [index], which rested at [rest]. */
    data class Axis(val index: Int, val rest: Float) : DetectOutcome

    /** The player pressed button [index]. */
    data class Button(val index: Int) : DetectOutcome

    /** Stopped with no change: cancelled, the device went away or [DETECT_SECONDS] passed. */
    data object Stopped : DetectOutcome
}

/**
 * Detecting the handle axis or the pause button of one device (design decisions 2 and 7): an axis that moves at least
 * [DETECT_TRAVEL] from where it was at the start is taken, the one that moved most if several did, with its starting value as
 * the rest value; a button is the first one pressed that was not already held at the start (so the confirm that began
 * detecting is not taken). While detecting an axis the device's back button (B, or button 1) cancels; while detecting a button
 * it is a candidate like any other. Main thread only.
 */
class DetectSession(val kind: DetectKind, private val start: DeviceState) {
    private var elapsed = 0f
    private var finished = false
    private var lastButtons = start.buttons.copyOf()

    /** Seconds left before it stops. */
    val remaining: Float get() = (DETECT_SECONDS - elapsed).coerceAtLeast(0f)

    /** Reads [device] (null when it went away) after [seconds]. */
    fun update(device: DeviceState?, seconds: Float): DetectOutcome {
        if (finished) return DetectOutcome.Stopped
        if (device == null) return stop()
        elapsed += seconds
        val outcome = when (kind) {
            DetectKind.AXIS -> axis(device)
            DetectKind.BUTTON -> button(device)
        }
        lastButtons = device.buttons.copyOf()
        if (outcome != DetectOutcome.Waiting) {
            finished = true
            return outcome
        }
        return if (elapsed >= DETECT_SECONDS) stop() else DetectOutcome.Waiting
    }

    /** Stops with no change. */
    fun cancel(): DetectOutcome = stop()

    private fun stop(): DetectOutcome {
        finished = true
        return DetectOutcome.Stopped
    }

    private fun axis(device: DeviceState): DetectOutcome {
        val back = if (device.gamepad) GAMEPAD_BUTTON_B else 1
        if (device.pressed(back) && !(back in lastButtons.indices && lastButtons[back])) return stop()
        var best = -1
        var moved = 0f
        for (i in device.axes.indices) {
            val travel = abs(device.axes[i] - start.axis(i))
            if (travel >= DETECT_TRAVEL && travel > moved) {
                best = i
                moved = travel
            }
        }
        return if (best >= 0) DetectOutcome.Axis(best, start.axis(best)) else DetectOutcome.Waiting
    }

    private fun button(device: DeviceState): DetectOutcome {
        for (i in device.buttons.indices) {
            if (device.buttons[i] && !(i in lastButtons.indices && lastButtons[i])) return DetectOutcome.Button(i)
        }
        return DetectOutcome.Waiting
    }
}

/** This profile with what [outcome] detected; unchanged for an outcome that detected nothing. */
fun ControllerProfile.applied(outcome: DetectOutcome): ControllerProfile = when (outcome) {
    is DetectOutcome.Axis -> copy(axis = outcome.index, rest = outcome.rest)
    is DetectOutcome.Button -> copy(pauseButton = outcome.index)
    else -> this
}
