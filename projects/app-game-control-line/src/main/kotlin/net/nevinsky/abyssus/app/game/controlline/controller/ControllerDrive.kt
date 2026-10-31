/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import net.nevinsky.abyssus.app.game.controlline.flow.Control
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen

/**
 * One frame of the controllers against the [flow] (design decision 3 of `add-control-line-gamepad-support`): the handle's tilt
 * goes to the flight's input, a pause button pauses a human flight, and the keys a pad sends go to [navigate], which moves
 * the menus. Called at the top of the game's frame, before the flow is followed. Main thread only.
 */
fun ControllerService.drive(flow: GameFlow, seconds: Float, navigate: (Int) -> Unit) {
    val screen = flow.screen
    val flying = screen as? Screen.Flying
    val frame = update(
        seconds,
        ControllerContext(menu = flying == null, settings = screen is Screen.Settings, humanFlight = flying?.control == Control.HUMAN),
    )
    if (frame.pause) flow.pause()
    frame.keys.forEach(navigate)
}
