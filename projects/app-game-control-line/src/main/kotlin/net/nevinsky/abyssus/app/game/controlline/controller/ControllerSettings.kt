/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

/** One device as the settings screen shows it: [name], its [kind] and its saved or default [profile], with the live [tilt] it would set. */
data class SettingsDevice(
    val name: String,
    val gamepad: Boolean,
    val profile: ControllerProfile,
    val tilt: Float,
    val axisName: String,
    val pauseName: String,
    val index: Int,
    val count: Int,
)

/** What the settings screen reads from and sends to the controller layer (design decision 6). Main thread only. */
interface ControllerSettings {
    /** The device the screen shows, or null when none is connected. */
    val device: SettingsDevice?

    /** The old settings file could not be read. */
    val damaged: Boolean

    /** What is being detected on the shown device, or null. */
    val detecting: DetectKind?

    /** Seconds left in the detect that is going on. */
    val detectRemaining: Float

    fun previousDevice()
    fun nextDevice()
    fun setInvert(invert: Boolean)
    fun setDeadZone(deadZone: Float)
    fun setCurve(curve: Float)
    fun detect(kind: DetectKind)
    fun cancelDetect()
    fun reset()
}

/** The settings of a game with no controller layer (tests, Play): nothing connected. */
data object NoControllerSettings : ControllerSettings {
    override val device: SettingsDevice? = null
    override val damaged = false
    override val detecting: DetectKind? = null
    override val detectRemaining = 0f
    override fun previousDevice() = Unit
    override fun nextDevice() = Unit
    override fun setInvert(invert: Boolean) = Unit
    override fun setDeadZone(deadZone: Float) = Unit
    override fun setCurve(curve: Float) = Unit
    override fun detect(kind: DetectKind) = Unit
    override fun cancelDetect() = Unit
    override fun reset() = Unit
}

private val GAMEPAD_AXES = listOf("Left stick X", "Left stick Y", "Right stick X", "Right stick Y", "Left trigger", "Right trigger")

private val GAMEPAD_BUTTONS = listOf(
    "A", "B", "X", "Y", "Left bumper", "Right bumper", "Back", "Start", "Guide", "Left stick", "Right stick",
    "D-pad up", "D-pad right", "D-pad down", "D-pad left",
)

/** The name of axis [index] of a gamepad or joystick, or "not set". */
fun axisName(index: Int?, gamepad: Boolean): String = when {
    index == null -> "not set"
    gamepad -> GAMEPAD_AXES.getOrNull(index) ?: "Axis ${index + 1}"
    else -> "Axis ${index + 1}"
}

/** The name of button [index] of a gamepad or joystick, or "not set". */
fun buttonName(index: Int?, gamepad: Boolean): String = when {
    index == null -> "not set"
    gamepad -> GAMEPAD_BUTTONS.getOrNull(index) ?: "Button ${index + 1}"
    else -> "Button ${index + 1}"
}
