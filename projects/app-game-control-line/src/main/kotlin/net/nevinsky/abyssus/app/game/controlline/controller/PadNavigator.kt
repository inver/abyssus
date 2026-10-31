/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import com.badlogic.gdx.Input

/** A held direction repeats after this long, then every [REPEAT_EVERY] seconds. */
const val REPEAT_AFTER = 0.4f
const val REPEAT_EVERY = 0.12f

/** A gamepad's left stick acts as a direction past this much of its travel, and stays on until it falls below [STICK_OFF]. */
const val STICK_ON = 0.5f
const val STICK_OFF = 0.3f

/**
 * Turns the devices' state into the keys of the keyboard (design decision 5): the D-pad or left stick of a gamepad, or the
 * first hat of a joystick, as Up, Down, Left and Right with auto-repeat; the confirm button (A, or button 0) as Enter and
 * the back button (B, or button 1) as Escape. Any connected device may navigate. While [update] is told navigation is
 * off (flying, detecting) it emits nothing but still tracks what is held, so a button still down when navigation comes back
 * is not a new press. Main thread only.
 */
class PadNavigator {
    private val directions = intArrayOf(HAT_UP, HAT_RIGHT, HAT_DOWN, HAT_LEFT)
    private val keys = intArrayOf(Input.Keys.UP, Input.Keys.RIGHT, Input.Keys.DOWN, Input.Keys.LEFT)
    private val heldFor = FloatArray(4)
    private val repeatAt = FloatArray(4)
    private var held = 0
    private var confirmHeld = false
    private var backHeld = false
    private var stick = 0

    /** The keys to send for [devices] after [seconds], in order; empty while [enabled] is false. */
    fun update(devices: List<DeviceState>, seconds: Float, enabled: Boolean): List<Int> {
        var mask = 0
        var confirm = false
        var back = false
        for (device in devices) {
            mask = mask or device.hat or stickMask(device)
            confirm = confirm or device.pressed(if (device.gamepad) GAMEPAD_BUTTON_A else 0)
            back = back or device.pressed(if (device.gamepad) GAMEPAD_BUTTON_B else 1)
        }
        val out = ArrayList<Int>()
        for (i in directions.indices) {
            val down = mask and directions[i] != 0
            val was = held and directions[i] != 0
            if (!down) {
                heldFor[i] = 0f
                continue
            }
            if (!was) {
                heldFor[i] = 0f
                repeatAt[i] = REPEAT_AFTER
                if (enabled) out += keys[i]
            } else {
                heldFor[i] += seconds
                if (heldFor[i] >= repeatAt[i]) {
                    repeatAt[i] += REPEAT_EVERY
                    if (enabled) out += keys[i]
                }
            }
        }
        if (enabled && confirm && !confirmHeld) out += Input.Keys.ENTER
        if (enabled && back && !backHeld) out += Input.Keys.ESCAPE
        held = mask
        confirmHeld = confirm
        backHeld = back
        return out
    }

    /** Forgets what was held, as when the device that held it went away. */
    fun reset() {
        held = 0
        confirmHeld = false
        backHeld = false
        stick = 0
        heldFor.fill(0f)
    }

    /** The left stick of a gamepad as a direction mask, with a little hysteresis so a stick near the threshold does not flutter. */
    private fun stickMask(device: DeviceState): Int {
        if (!device.gamepad) return 0
        fun on(value: Float, bit: Int, negative: Int): Int {
            val limit = if (stick and bit != 0 || stick and negative != 0) STICK_OFF else STICK_ON
            return when {
                value > limit -> bit
                value < -limit -> negative
                else -> 0
            }
        }
        // the Y axis of a gamepad reads up as positive, the X axis right as positive
        val mask = on(device.axis(GAMEPAD_AXIS_LEFT_X), HAT_RIGHT, HAT_LEFT) or on(device.axis(GAMEPAD_AXIS_LEFT_Y), HAT_UP, HAT_DOWN)
        stick = mask
        return mask
    }
}

/**
 * Sends [keycode] as a device's navigation (design decision 5): to [handle] first (the game's keys, then the presenter's), and
 * when nothing took it, Up and Down become Shift-Tab and Tab on a screen with tab navigation, and Enter becomes Space on a
 * focused checkbox. Returns whether anything took it. Pure, so the fallbacks are tested without the presenter.
 */
fun navigateKey(keycode: Int, handle: (Int) -> Boolean, tab: (Boolean) -> Boolean, space: () -> Boolean): Boolean {
    if (handle(keycode)) return true
    return when (keycode) {
        Input.Keys.UP -> tab(true)
        Input.Keys.DOWN -> tab(false)
        Input.Keys.ENTER -> space()
        else -> false
    }
}
