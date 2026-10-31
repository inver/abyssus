/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import kotlin.math.abs

/** What the game is showing, as far as the controller layer cares. */
class ControllerContext(
    /** A menu screen (the main menu, plane select, the pause menu, the settings...): the device navigates it. */
    val menu: Boolean,
    /** The settings screen is open: a detect may go on. */
    val settings: Boolean,
    /** A human flight is on: the device sets the handle and its pause button acts. */
    val humanFlight: Boolean,
)

/** What a frame of the controller layer asks of the game: the [keys] to send the menu and whether to [pause] the flight. */
class ControllerFrame(val keys: List<Int>, val pause: Boolean)

/**
 * The controller layer of the game (design decision 3), polled once per frame on the main thread before the flow follows:
 * reads the devices from the [source], applies each one's profile from [profiles], feeds the handle's tilt to [input]
 * during a human flight, notices a pause button, navigates the menus through a [PadNavigator], runs the detect of the settings
 * screen and tells the game about a device that went away. Also the [ControllerSettings] the settings screen shows.
 */
class ControllerService(
    private val source: ControllerSource,
    private val profiles: ControllerProfiles,
    private val input: HandleInput,
) : ControllerSettings {
    private val navigator = PadNavigator()
    private var devices: List<DeviceState> = emptyList()
    private var selectedGuid: String? = null
    private var controlling: String? = null
    private var session: DetectSession? = null
    private var sessionKind: DetectKind? = null
    private var sessionGuid: String? = null
    private val pauseHeld = HashSet<String>()

    override val damaged: Boolean get() = profiles.damaged

    override val detecting: DetectKind? get() = sessionKind

    override val detectRemaining: Float get() = session?.remaining ?: 0f

    /** The profile of [device]: its saved one, or its kind's default. */
    fun profileOf(device: DeviceState): ControllerProfile = profiles.profile(device.guid, device.gamepad)

    /** The tilt [device] would set under its profile. */
    fun tiltOf(device: DeviceState): Float = profileOf(device).tiltOf(device.axes)

    /** Reads the devices and acts on them for one frame of [seconds] in [context]. */
    fun update(seconds: Float, context: ControllerContext): ControllerFrame {
        // a platform that cannot be read is a game with no device, never a crash
        val now = runCatchingKeepingCancellation { source.poll() }.getOrDefault(emptyList())
        val gone = devices.map { it.guid }.toSet() - now.map { it.guid }.toSet()
        devices = now
        if (selectedGuid == null || devices.none { it.guid == selectedGuid }) selectedGuid = devices.firstOrNull()?.guid
        if (gone.isNotEmpty()) {
            navigator.reset()
            pauseHeld.removeAll { it.substringBefore('#') in gone }
            if (controlling in gone) {
                input.padLost()
                controlling = null
            }
        }

        var pause = false
        for (device in devices) {
            val button = profileOf(device).pauseButton ?: continue
            val key = "${device.guid}#$button"
            if (device.pressed(button)) {
                if (pauseHeld.add(key) && context.humanFlight) pause = true
            } else {
                pauseHeld.remove(key)
            }
        }

        if (context.humanFlight) {
            val best = devices.map { it to tiltOf(it) }.maxByOrNull { abs(it.second) }
            if (best != null) {
                input.pad(best.second)
                if (best.second != 0f) controlling = best.first.guid
            }
        }

        advanceDetect(seconds, context.settings)
        val keys = navigator.update(devices, seconds, context.menu && session == null)
        return ControllerFrame(keys, pause)
    }

    private fun advanceDetect(seconds: Float, settingsOpen: Boolean) {
        val current = session ?: return
        if (!settingsOpen) {
            cancelDetect()
            return
        }
        val device = devices.firstOrNull { it.guid == sessionGuid }
        val outcome = current.update(device, seconds)
        if (outcome == DetectOutcome.Waiting) return
        val guid = sessionGuid
        if (device != null && guid != null) profiles.put(guid, profileOf(device).applied(outcome))
        session = null
        sessionKind = null
        sessionGuid = null
    }

    override val device: SettingsDevice?
        get() {
            val index = devices.indexOfFirst { it.guid == selectedGuid }.takeIf { it >= 0 } ?: return null
            val d = devices[index]
            val profile = profileOf(d)
            return SettingsDevice(
                d.name, d.gamepad, profile, tiltOf(d), axisName(profile.axis, d.gamepad), buttonName(profile.pauseButton, d.gamepad),
                index, devices.size,
            )
        }

    override fun previousDevice() = turn(-1)

    override fun nextDevice() = turn(1)

    private fun turn(by: Int) {
        if (devices.isEmpty()) return
        cancelDetect()
        val index = devices.indexOfFirst { it.guid == selectedGuid }.coerceAtLeast(0)
        selectedGuid = devices[Math.floorMod(index + by, devices.size)].guid
    }

    private fun change(edit: (ControllerProfile) -> ControllerProfile) {
        val d = devices.firstOrNull { it.guid == selectedGuid } ?: return
        profiles.put(d.guid, edit(profileOf(d)))
    }

    override fun setInvert(invert: Boolean) = change { it.copy(invert = invert) }

    override fun setDeadZone(deadZone: Float) = change { it.copy(deadZone = deadZone.coerceIn(0f, MAX_DEAD_ZONE)) }

    override fun setCurve(curve: Float) = change { it.copy(curve = curve.coerceIn(0f, 1f)) }

    override fun detect(kind: DetectKind) {
        val d = devices.firstOrNull { it.guid == selectedGuid } ?: return
        session = DetectSession(kind, d)
        sessionKind = kind
        sessionGuid = d.guid
    }

    override fun cancelDetect() {
        session = null
        sessionKind = null
        sessionGuid = null
    }

    override fun reset() {
        val d = devices.firstOrNull { it.guid == selectedGuid } ?: return
        cancelDetect()
        profiles.reset(d.guid)
    }
}
