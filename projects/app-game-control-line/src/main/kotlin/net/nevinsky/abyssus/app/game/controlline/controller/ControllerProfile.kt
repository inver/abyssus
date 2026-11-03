/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import kotlin.math.abs

/** The standard gamepad layout (the order GLFW uses): axes (up and right are positive here) and buttons by index. */
const val GAMEPAD_AXIS_LEFT_X = 0
const val GAMEPAD_AXIS_LEFT_Y = 1
const val GAMEPAD_AXIS_RIGHT_X = 2
const val GAMEPAD_AXIS_RIGHT_Y = 3
const val GAMEPAD_AXIS_LEFT_TRIGGER = 4
const val GAMEPAD_AXIS_RIGHT_TRIGGER = 5
const val GAMEPAD_BUTTON_A = 0
const val GAMEPAD_BUTTON_B = 1
const val GAMEPAD_BUTTON_START = 7
const val GAMEPAD_BUTTON_DPAD_UP = 11
const val GAMEPAD_BUTTON_DPAD_RIGHT = 12
const val GAMEPAD_BUTTON_DPAD_DOWN = 13
const val GAMEPAD_BUTTON_DPAD_LEFT = 14

/** The default dead zone: this share of the travel from the rest value does nothing. */
const val DEFAULT_DEAD_ZONE = 0.10f

/** The largest dead zone the settings screen offers. */
const val MAX_DEAD_ZONE = 0.5f

/**
 * What the game knows about one device (spec `control-line-controller-input`): the [axis] that sets the handle (an index
 * into the device's axes, null when none is chosen), its [rest] value, [invert], the [deadZone] (0 to [MAX_DEAD_ZONE] of the
 * travel), the [curve] (0 linear to 1 cubic) and the [pauseButton] (a button index, null for none).
 */
data class ControllerProfile(
    val axis: Int? = null,
    val rest: Float = 0f,
    val invert: Boolean = false,
    val deadZone: Float = DEFAULT_DEAD_ZONE,
    val curve: Float = 0f,
    val pauseButton: Int? = null,
)

/** The profile a gamepad starts with: the left stick's vertical axis, pushed up for handle up, and Start for pause. */
fun gamepadDefault() = ControllerProfile(axis = GAMEPAD_AXIS_LEFT_Y, pauseButton = GAMEPAD_BUTTON_START)

/** The profile a joystick starts with: nothing chosen until the player detects an axis and a button. */
fun joystickDefault() = ControllerProfile()

/**
 * The handle's tilt (-1..1) from [value] of the profile's axis: taken relative to [ControllerProfile.rest] so that full
 * travel either way is 1, then the dead zone, the curve and invert. NaN is neutral.
 */
fun ControllerProfile.tiltOf(value: Float): Float {
    if (value.isNaN()) return 0f
    val v = value.coerceIn(-1f, 1f)
    val rest = rest.coerceIn(-1f, 1f)
    val share = when {
        v >= rest -> if (rest >= 1f) 0f else (v - rest) / (1f - rest)
        else -> if (rest <= -1f) 0f else (v - rest) / (rest + 1f)
    }
    val dead = deadZone.coerceIn(0f, MAX_DEAD_ZONE)
    val magnitude = abs(share)
    if (magnitude <= dead) return 0f
    val x = (magnitude - dead) / (1f - dead)
    val c = curve.coerceIn(0f, 1f)
    val shaped = (1f - c) * x + c * x * x * x
    val signed = if (share < 0f) -shaped else shaped
    return if (invert) -signed else signed
}

/** The tilt [axes] give under this profile; 0 without a chosen axis or when the device has fewer axes. */
fun ControllerProfile.tiltOf(axes: FloatArray): Float = axis?.takeIf { it in axes.indices }?.let { tiltOf(axes[it]) } ?: 0f
