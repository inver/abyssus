/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller.glfw

import net.nevinsky.abyssus.app.game.controlline.controller.ControllerSource
import net.nevinsky.abyssus.app.game.controlline.controller.DeviceState
import org.lwjgl.glfw.GLFW

/**
 * The devices without a gamepad mapping (flight sticks, wheels, pedals), which gdx-controllers does not list: read through
 * GLFW's joystick API with their raw axes, buttons and first hat. A device with a gamepad mapping is left to [net.nevinsky.abyssus.app.game.controlline.controller.gdx.GdxControllers].
 * The window must exist, and it is polled on the main thread only.
 */
class GlfwJoysticks : ControllerSource {
    override fun poll(): List<DeviceState> = buildList {
        for (jid in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST) {
            if (!GLFW.glfwJoystickPresent(jid) || GLFW.glfwJoystickIsGamepad(jid)) continue
            val guid = GLFW.glfwGetJoystickGUID(jid) ?: continue
            val name = GLFW.glfwGetJoystickName(jid) ?: "Controller ${jid + 1}"
            val axes = GLFW.glfwGetJoystickAxes(jid) ?: continue
            val buttons = GLFW.glfwGetJoystickButtons(jid)
            val hats = GLFW.glfwGetJoystickHats(jid)
            add(
                DeviceState(
                    guid, name, false,
                    FloatArray(axes.remaining()) { axes.get(axes.position() + it) },
                    BooleanArray(buttons?.remaining() ?: 0) { buttons!!.get(buttons.position() + it).toInt() != 0 },
                    if (hats != null && hats.remaining() > 0) hats.get(hats.position()).toInt() and 0xF else 0,
                ),
            )
        }
    }
}
