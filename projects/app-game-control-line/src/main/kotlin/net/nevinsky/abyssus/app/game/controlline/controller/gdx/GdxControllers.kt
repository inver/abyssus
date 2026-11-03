/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller.gdx

import com.badlogic.gdx.controllers.Controller
import com.badlogic.gdx.controllers.ControllerMapping
import com.badlogic.gdx.controllers.Controllers
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerSource
import net.nevinsky.abyssus.app.game.controlline.controller.DeviceState
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_A
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_B
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_DPAD_DOWN
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_DPAD_LEFT
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_DPAD_RIGHT
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_DPAD_UP
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_BUTTON_START
import net.nevinsky.abyssus.app.game.controlline.controller.HAT_DOWN
import net.nevinsky.abyssus.app.game.controlline.controller.HAT_LEFT
import net.nevinsky.abyssus.app.game.controlline.controller.HAT_RIGHT
import net.nevinsky.abyssus.app.game.controlline.controller.HAT_UP

/** The standard layout the profiles use has this many axes and buttons. */
private const val GAMEPAD_AXES = 6
private const val GAMEPAD_BUTTONS = 15
private const val TRIGGER_LEFT = 4
private const val TRIGGER_RIGHT = 5

/**
 * The devices libGDX's gdx-controllers sees (design decision 1): each one is read through its [ControllerMapping] and
 * presented in the standard gamepad layout, with its Y axes turned so that up is positive. gdx-controllers only lists
 * devices that have a gamepad mapping, so every device here is a gamepad. The only class of the game that imports
 * gdx-controllers; it is polled on the main thread only, once the application exists.
 */
class GdxControllers : ControllerSource {
    override fun poll(): List<DeviceState> = Controllers.getControllers().filter { it.isConnected }.map(::stateOf)

    private fun stateOf(controller: Controller): DeviceState {
        val mapping = controller.mapping
        val axes = FloatArray(GAMEPAD_AXES)
        axes[0] = axis(controller, mapping.axisLeftX)
        // gdx-controllers reports a stick pushed up as -1: turn the Y axes so that up is positive, like the profile reads them
        axes[1] = -axis(controller, mapping.axisLeftY)
        axes[2] = axis(controller, mapping.axisRightX)
        axes[3] = -axis(controller, mapping.axisRightY)
        axes[TRIGGER_LEFT] = trigger(controller, mapping.buttonL2)
        axes[TRIGGER_RIGHT] = trigger(controller, mapping.buttonR2)
        val buttons = BooleanArray(GAMEPAD_BUTTONS)
        fun map(code: Int, standard: Int) {
            if (code != ControllerMapping.UNDEFINED) buttons[standard] = controller.getButton(code)
        }
        map(mapping.buttonA, GAMEPAD_BUTTON_A)
        map(mapping.buttonB, GAMEPAD_BUTTON_B)
        map(mapping.buttonStart, GAMEPAD_BUTTON_START)
        map(mapping.buttonDpadUp, GAMEPAD_BUTTON_DPAD_UP)
        map(mapping.buttonDpadRight, GAMEPAD_BUTTON_DPAD_RIGHT)
        map(mapping.buttonDpadDown, GAMEPAD_BUTTON_DPAD_DOWN)
        map(mapping.buttonDpadLeft, GAMEPAD_BUTTON_DPAD_LEFT)
        val hat = (if (buttons[GAMEPAD_BUTTON_DPAD_UP]) HAT_UP else 0) or (if (buttons[GAMEPAD_BUTTON_DPAD_RIGHT]) HAT_RIGHT else 0) or
            (if (buttons[GAMEPAD_BUTTON_DPAD_DOWN]) HAT_DOWN else 0) or (if (buttons[GAMEPAD_BUTTON_DPAD_LEFT]) HAT_LEFT else 0)
        return DeviceState(controller.uniqueId ?: controller.name, controller.name, true, axes, buttons, hat)
    }

    private fun axis(controller: Controller, code: Int): Float =
        if (code == ControllerMapping.UNDEFINED) 0f else controller.getAxis(code)

    /** A trigger is a button in the mapping, so it reads as fully pressed or not. */
    private fun trigger(controller: Controller, code: Int): Float =
        if (code != ControllerMapping.UNDEFINED && controller.getButton(code)) 1f else 0f
}
