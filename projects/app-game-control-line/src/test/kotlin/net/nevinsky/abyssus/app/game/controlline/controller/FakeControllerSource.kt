/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

/** A [ControllerSource] the test drives: plug, unplug, move an axis, press a button, set the hat. */
class FakeControllerSource : ControllerSource {
    private class Device(val guid: String, val name: String, val gamepad: Boolean, val axes: FloatArray, val buttons: BooleanArray, var hat: Int = 0)

    private val devices = LinkedHashMap<String, Device>()

    /** Plugs in a device; a gamepad has the standard 6 axes and 15 buttons. */
    fun plug(guid: String, name: String = guid, gamepad: Boolean = false, axes: Int = if (gamepad) 6 else 4, buttons: Int = if (gamepad) 15 else 12): FakeControllerSource {
        devices[guid] = Device(guid, name, gamepad, FloatArray(axes), BooleanArray(buttons))
        return this
    }

    fun unplug(guid: String) {
        devices.remove(guid)
    }

    fun axis(guid: String, index: Int, value: Float) {
        devices.getValue(guid).axes[index] = value
    }

    fun press(guid: String, button: Int, down: Boolean = true) {
        devices.getValue(guid).buttons[button] = down
    }

    fun hat(guid: String, mask: Int) {
        devices.getValue(guid).hat = mask
    }

    override fun poll(): List<DeviceState> =
        devices.values.map { DeviceState(it.guid, it.name, it.gamepad, it.axes.copyOf(), it.buttons.copyOf(), it.hat) }
}
