/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.input

/** Keys reach full tilt, and come back to neutral, in this many seconds. */
const val TILT_TIME = 0.15f

/** The mouse reaches full tilt this far from the window's centre, as a fraction of the window's height. */
const val MOUSE_FULL_TILT = 0.25f

/**
 * The handle's tilt from the keyboard, the mouse and a controller (design decision 9 of `add-control-line-game`, decision 4 of
 * `add-control-line-gamepad-support`): W or Up tilts up, S or Down down, ramping at 1 / [TILT_TIME] per second; the mouse's
 * height sets it by its distance from the window's centre; a controller's axis sets it as [pad] says. Whichever was used
 * last controls the handle. Key names are matched without case, so libGDX's (`Up`) and the play protocol's (`UP`)
 * both work.
 */
class HandleInput {
    private enum class Device { KEYS, MOUSE, PAD }

    private var up = false
    private var down = false
    private var mouse = 0f
    private var pad = 0f
    private var device = Device.KEYS

    /** -1 (full down) .. 1 (full up). */
    var tilt = 0f
        private set

    /** A key went down or up; returns whether it is one of the handle's. */
    fun key(name: String, pressed: Boolean): Boolean {
        when (name.uppercase()) {
            "W", "UP" -> up = pressed
            "S", "DOWN" -> down = pressed
            else -> return false
        }
        device = Device.KEYS
        return true
    }

    /** The mouse is at [y] pixels from the top of a window [height] pixels high. */
    fun mouseMoved(y: Float, height: Float) {
        if (height <= 0f) return
        mouse = ((height / 2 - y) / (height * MOUSE_FULL_TILT)).coerceIn(-1f, 1f)
        device = Device.MOUSE
    }

    /**
     * The tilt a controller's handle axis gives now, -1..1 after its dead zone. A tilt of 0 does not take control, so an
     * axis resting inside its dead zone never overrides the keys; once the controller controls, its value (0 included) is
     * the tilt until a key or the mouse is used.
     */
    fun pad(tilt: Float) {
        pad = if (tilt.isNaN()) 0f else tilt.coerceIn(-1f, 1f)
        if (pad != 0f) device = Device.PAD
    }

    /** The controller that was controlling the handle went away: neutral, and the keys control it again. */
    fun padLost() {
        pad = 0f
        if (device == Device.PAD) {
            device = Device.KEYS
            tilt = 0f
        }
    }

    /** Advances the keys' ramp by [seconds]. */
    fun update(seconds: Float) {
        if (device == Device.PAD) {
            tilt = pad
            return
        }
        if (device == Device.MOUSE) {
            tilt = mouse
            return
        }
        val target = (if (up) 1f else 0f) - (if (down) 1f else 0f)
        val step = seconds / TILT_TIME
        tilt = if (tilt < target) minOf(target, tilt + step) else maxOf(target, tilt - step)
    }

    companion object {
        /** Whether [name] is one of the handle's keys (W, S, Up, Down), in any case. */
        fun isHandleKey(name: String): Boolean = name.uppercase() in listOf("W", "UP", "S", "DOWN")
    }

    /** Back to neutral with nothing held (a new flight). */
    fun reset() {
        up = false
        down = false
        pad = 0f
        tilt = 0f
        device = Device.KEYS
    }
}
