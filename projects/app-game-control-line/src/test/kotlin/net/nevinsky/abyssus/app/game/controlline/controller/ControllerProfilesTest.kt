/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ControllerProfilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val home get() = temp.root.toPath().resolve("home/.abyssus-control-line")

    private fun file(): Path = home.resolve("controls.json")

    @Test
    fun theDefaultFileIsInTheHomeFolder() =
        assertEquals(Path.of("/home/pilot/.abyssus-control-line/controls.json"), defaultControlsFile("/home/pilot"))

    @Test
    fun aGamepadStartsOnTheLeftStickAndStart() {
        val p = ControllerProfiles(file()).profile("pad", gamepad = true)
        assertEquals(GAMEPAD_AXIS_LEFT_Y, p.axis)
        assertEquals(0f, p.rest, 0f)
        assertEquals(0.10f, p.deadZone, 0f)
        assertEquals(0f, p.curve, 0f)
        assertFalse(p.invert)
        assertEquals(GAMEPAD_BUTTON_START, p.pauseButton)
    }

    @Test
    fun aJoystickStartsWithNoAxisAndNoPauseButton() {
        val p = ControllerProfiles(file()).profile("stick", gamepad = false)
        assertNull(p.axis)
        assertNull(p.pauseButton)
    }

    @Test
    fun changesSurviveAReload() {
        val profiles = ControllerProfiles(file())
        val changed = profiles.profile("stick", false).copy(axis = 1, rest = -0.5f, invert = true, deadZone = 0.2f, curve = 0.4f, pauseButton = 7)
        profiles.put("stick", changed)
        assertEquals(changed, ControllerProfiles(file()).profile("stick", false))
        assertTrue(Files.readString(file()).contains("\"version\" : 1"))
    }

    @Test
    fun twoDevicesKeepSeparateProfilesWhateverTheirOrder() {
        val profiles = ControllerProfiles(file())
        profiles.put("stick", ControllerProfile(axis = 2, invert = true))
        profiles.put("pad", gamepadDefault().copy(deadZone = 0.3f))
        val again = ControllerProfiles(file())
        assertEquals(2, again.profile("stick", false).axis)
        assertEquals(0.3f, again.profile("pad", true).deadZone, 0f)
        assertEquals("a device never seen takes its kind's default", gamepadDefault(), again.profile("other", true))
    }

    @Test
    fun resetBringsTheDefaultBack() {
        val profiles = ControllerProfiles(file())
        profiles.put("pad", gamepadDefault().copy(invert = true))
        profiles.reset("pad")
        assertEquals(gamepadDefault(), profiles.profile("pad", true))
        assertEquals(gamepadDefault(), ControllerProfiles(file()).profile("pad", true))
    }

    @Test
    fun anUnreadableFileIsRenamedAndTheDefaultsApply() {
        Files.createDirectories(home)
        Files.writeString(file(), "not json")
        val profiles = ControllerProfiles(file())
        assertTrue(profiles.damaged)
        assertEquals(gamepadDefault(), profiles.profile("pad", true))
        assertTrue(Files.exists(home.resolve("controls.json.bad")))
        assertFalse(Files.exists(file()))
    }

    @Test
    fun anUnknownVersionIsRefusedLikeAnUnreadableFile() {
        Files.createDirectories(home)
        Files.writeString(file(), """{"version": 2, "devices": {}}""")
        assertTrue(ControllerProfiles(file()).damaged)
        assertTrue(Files.exists(home.resolve("controls.json.bad")))
    }
}
