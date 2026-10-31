/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/** The hat directions as a bit mask, as a direction pad gives it. */
const val HAT_UP = 1
const val HAT_RIGHT = 2
const val HAT_DOWN = 4
const val HAT_LEFT = 8

/**
 * One connected device as of one poll: its stable [guid] and [name], whether it has the standard [gamepad] layout, its
 * [axes] (-1..1, up and right positive on a gamepad), [buttons] and the direction pad or first hat as a [hat] mask. A
 * snapshot: the arrays are the device's alone and not changed afterwards.
 */
class DeviceState(
    val guid: String,
    val name: String,
    val gamepad: Boolean,
    val axes: FloatArray,
    val buttons: BooleanArray,
    val hat: Int = 0,
) {
    fun pressed(button: Int): Boolean = button in buttons.indices && buttons[button]

    fun axis(index: Int): Float = if (index in axes.indices) axes[index] else 0f
}

/** The devices present now. Polled once per frame on the main thread; the gdx-controllers one is the only one that touches the platform. */
fun interface ControllerSource {
    fun poll(): List<DeviceState>
}

/** The devices of every source in turn; a source that cannot be read adds none. */
class CombinedControllerSource(private val sources: List<ControllerSource>) : ControllerSource {
    override fun poll(): List<DeviceState> = sources.flatMap { runCatchingKeepingCancellation { it.poll() }.getOrDefault(emptyList()) }
}
