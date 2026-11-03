/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import com.badlogic.gdx.Input
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ControllerServiceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val source = FakeControllerSource()
    private val input = HandleInput()
    private val file get() = temp.root.toPath().resolve("controls.json")
    private val profiles by lazy { ControllerProfiles(file) }
    private val service by lazy { ControllerService(source, profiles, input) }

    private val menu = ControllerContext(menu = true, settings = false, humanFlight = false)
    private val flight = ControllerContext(menu = false, settings = false, humanFlight = true)
    private val demonstration = ControllerContext(menu = false, settings = false, humanFlight = false)
    private val settings = ControllerContext(menu = true, settings = true, humanFlight = false)

    private fun frame(context: ControllerContext, seconds: Float = 1f / 60f) = service.update(seconds, context)

    private fun tiltNow(): Float {
        input.update(1f / 120f)
        return input.tilt
    }

    @Test
    fun theStickSetsTheTiltAndTakesControl() {
        source.plug("pad", gamepad = true)
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 1f)
        frame(flight)
        assertEquals(1f, tiltNow(), 1e-6f)
    }

    @Test
    fun aRestingStickLeavesTheKeysInControl() {
        source.plug("pad", gamepad = true)
        input.key("W", true)
        repeat(30) { frame(flight); input.update(1f / 120f) }
        assertEquals(1f, tiltNow(), 0f)
    }

    @Test
    fun aStickInsideTheDeadZoneDoesNotTakeControl() {
        source.plug("pad", gamepad = true)
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.08f)
        input.key("S", true)
        frame(flight)
        repeat(30) { input.update(1f / 120f) }
        assertEquals(-1f, input.tilt, 0f)
    }

    @Test
    fun aJoystickWithNoAxisChosenDoesNothingToTheHandle() {
        source.plug("stick")
        source.axis("stick", 1, 1f)
        frame(flight)
        assertEquals(0f, tiltNow(), 0f)
    }

    @Test
    fun aJoysticksChosenAxisFliesTheHandle() {
        source.plug("stick")
        frame(flight)
        service.detect(DetectKind.AXIS)
        // not while flying: the detect needs the settings screen
        service.update(0.1f, settings)
        service.cancelDetect()
        profiles.put("stick", ControllerProfile(axis = 1, deadZone = 0f))
        source.axis("stick", 1, -1f)
        frame(flight)
        assertEquals(-1f, tiltNow(), 1e-6f)
    }

    @Test
    fun unpluggingTheControllingDeviceMidFlightNeutralisesTheHandleAndKeepsTheFlightGoing() {
        source.plug("pad", gamepad = true)
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 1f)
        frame(flight)
        assertEquals(1f, tiltNow(), 0f)
        source.unplug("pad")
        val f = frame(flight)
        assertFalse(f.pause)
        assertEquals(0f, tiltNow(), 0f)
        input.key("W", true)
        repeat(30) { input.update(1f / 120f) }
        assertEquals("the keys work again", 1f, input.tilt, 0f)
    }

    @Test
    fun pluggingTheDeviceBackInUsesItsSavedProfile() {
        source.plug("stick", "Extreme 3D Pro")
        frame(menu)
        service.nextDevice()
        service.setInvert(true)
        source.unplug("stick")
        frame(menu)
        source.plug("stick", "Extreme 3D Pro")
        frame(menu)
        assertTrue(service.device!!.profile.invert)
        assertTrue(ControllerProfiles(file).profile("stick", false).invert)
    }

    @Test
    fun thePauseButtonPausesOnlyAHumanFlight() {
        source.plug("pad", gamepad = true)
        source.press("pad", GAMEPAD_BUTTON_START)
        assertFalse("in a menu it does not pause", frame(menu).pause)
        source.press("pad", GAMEPAD_BUTTON_START, false)
        frame(flight)
        source.press("pad", GAMEPAD_BUTTON_START)
        assertTrue(frame(flight).pause)
        assertFalse("held, it pauses once", frame(flight).pause)
        source.press("pad", GAMEPAD_BUTTON_START, false)
        frame(flight)
        source.press("pad", GAMEPAD_BUTTON_START)
        assertFalse("not in a demonstration", frame(demonstration).pause)
    }

    @Test
    fun aPauseButtonHeldWhenTheFlightStartsDoesNotPauseIt() {
        source.plug("pad", gamepad = true)
        source.press("pad", GAMEPAD_BUTTON_START)
        frame(menu)
        assertFalse(frame(flight).pause)
    }

    @Test
    fun aJoysticksPauseButtonIsTheOneDetected() {
        source.plug("stick")
        frame(menu)
        profiles.put("stick", ControllerProfile(pauseButton = 7))
        source.press("stick", 7)
        assertTrue(frame(flight).pause)
    }

    @Test
    fun theDpadMovesMenuFocusAndConfirmPresses() {
        source.plug("pad", gamepad = true)
        source.hat("pad", HAT_DOWN)
        assertEquals(listOf(Input.Keys.DOWN), frame(menu).keys)
        source.hat("pad", 0)
        frame(menu)
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals(listOf(Input.Keys.ENTER), frame(menu).keys)
    }

    @Test
    fun navigationIsSilentWhileFlying() {
        source.plug("pad", gamepad = true)
        source.hat("pad", HAT_DOWN)
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals(emptyList<Int>(), (1..60).flatMap { frame(flight).keys })
    }

    @Test
    fun navigationIsSilentWhileDetecting() {
        source.plug("pad", gamepad = true)
        frame(settings)
        service.detect(DetectKind.AXIS)
        source.hat("pad", HAT_DOWN)
        assertEquals(emptyList<Int>(), (1..30).flatMap { frame(settings).keys })
    }

    @Test
    fun aDemonstrationIgnoresTheStick() {
        source.plug("pad", gamepad = true)
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 1f)
        repeat(10) { frame(demonstration) }
        assertEquals(0f, tiltNow(), 0f)
    }

    @Test
    fun theSettingsScreenSeesTheDeviceItsLiveTiltAndItsNames() {
        assertNull(service.device)
        source.plug("pad", "Logitech F310", gamepad = true)
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.55f)
        frame(settings)
        val d = service.device!!
        assertEquals("Logitech F310", d.name)
        assertEquals(0.5f, d.tilt, 1e-5f)
        assertEquals("Left stick Y", d.axisName)
        assertEquals("Start", d.pauseName)
        assertEquals(1, d.count)
    }

    @Test
    fun changesAreSavedAndResetRestoresTheDefault() {
        source.plug("pad", gamepad = true)
        frame(settings)
        service.setInvert(true)
        service.setDeadZone(0.3f)
        service.setCurve(0.5f)
        assertEquals(gamepadDefault().copy(invert = true, deadZone = 0.3f, curve = 0.5f), ControllerProfiles(file).profile("pad", true))
        service.setDeadZone(5f)
        assertEquals(MAX_DEAD_ZONE, service.device!!.profile.deadZone, 0f)
        service.reset()
        assertEquals(gamepadDefault(), service.device!!.profile)
    }

    @Test
    fun detectingAnAxisOnTheSettingsScreenSavesItWithItsRest() {
        source.plug("stick")
        source.axis("stick", 1, 0.1f)
        frame(settings)
        service.detect(DetectKind.AXIS)
        assertEquals(DetectKind.AXIS, service.detecting)
        source.axis("stick", 1, 1f)
        frame(settings)
        assertNull(service.detecting)
        assertEquals(1, service.device!!.profile.axis)
        assertEquals(0.1f, service.device!!.profile.rest, 0f)
    }

    @Test
    fun detectingAButtonSavesThePauseButtonAndLeavingSettingsCancels() {
        source.plug("stick")
        frame(settings)
        service.detect(DetectKind.BUTTON)
        source.press("stick", 6)
        frame(settings)
        assertEquals(6, service.device!!.profile.pauseButton)

        service.detect(DetectKind.AXIS)
        frame(menu)
        assertNull("leaving the settings screen cancels", service.detecting)
    }

    @Test
    fun theDeviceListTurnsAndWrapsAndAnUnreadableFileIsReported() {
        source.plug("a", "First").plug("b", "Second")
        frame(settings)
        assertEquals("First", service.device!!.name)
        service.nextDevice()
        assertEquals("Second", service.device!!.name)
        service.nextDevice()
        assertEquals("First", service.device!!.name)
        service.previousDevice()
        assertEquals("Second", service.device!!.name)
        assertFalse(service.damaged)
        assertNotNull(service.device)
    }
}
