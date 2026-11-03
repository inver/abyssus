/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectSessionTest {
    private val source = FakeControllerSource().plug("stick", "Extreme 3D Pro").plug("pad", "F310", gamepad = true)

    private fun device(guid: String) = source.poll().single { it.guid == guid }

    private fun axisSession(guid: String = "stick") = DetectSession(DetectKind.AXIS, device(guid))

    private fun buttonSession(guid: String = "stick") = DetectSession(DetectKind.BUTTON, device(guid))

    @Test
    fun anAxisPushedFullyUpBecomesTheHandleAxisWithItsStartingValueAsRest() {
        source.axis("stick", 1, 0.1f)
        val session = axisSession()
        source.axis("stick", 1, 1f)
        val outcome = session.update(device("stick"), 0.1f)
        assertEquals(DetectOutcome.Axis(1, 0.1f), outcome)
        val profile = joystickDefault().applied(outcome)
        assertEquals(1, profile.axis)
        assertEquals(0.1f, profile.rest, 0f)
    }

    @Test
    fun ofTwoAxesTheOneThatMovedMoreWins() {
        val session = axisSession()
        source.axis("stick", 0, 0.7f)
        source.axis("stick", 2, -1f)
        assertEquals(DetectOutcome.Axis(2, 0f), session.update(device("stick"), 0.1f))
    }

    @Test
    fun aPedalMovedFromItsEndIsTakenWithThatEndAsRest() {
        source.axis("stick", 3, -1f)
        val session = axisSession()
        source.axis("stick", 3, 0f)
        assertEquals(DetectOutcome.Axis(3, -1f), session.update(device("stick"), 0.1f))
    }

    @Test
    fun aNudgeUnderHalfTheTravelPicksNothing() {
        val session = axisSession()
        source.axis("stick", 1, 0.3f)
        assertEquals(DetectOutcome.Waiting, session.update(device("stick"), 0.1f))
        source.axis("stick", 1, -0.3f)
        assertEquals(DetectOutcome.Waiting, session.update(device("stick"), 0.1f))
        source.axis("stick", 1, 0.5f)
        assertEquals(DetectOutcome.Axis(1, 0f), session.update(device("stick"), 0.1f))
    }

    @Test
    fun aButtonPressIsTaken() {
        val session = buttonSession()
        source.press("stick", 6)
        val outcome = session.update(device("stick"), 0.1f)
        assertEquals(DetectOutcome.Button(6), outcome)
        assertEquals(6, joystickDefault().applied(outcome).pauseButton)
    }

    @Test
    fun aButtonAlreadyHeldAtTheStartIsNotTakenUntilPressedAgain() {
        source.press("pad", GAMEPAD_BUTTON_A)
        val session = buttonSession("pad")
        assertEquals(DetectOutcome.Waiting, session.update(device("pad"), 0.1f))
        source.press("pad", GAMEPAD_BUTTON_A, false)
        assertEquals(DetectOutcome.Waiting, session.update(device("pad"), 0.1f))
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals(DetectOutcome.Button(GAMEPAD_BUTTON_A), session.update(device("pad"), 0.1f))
    }

    @Test
    fun nothingHappeningForTenSecondsLeavesTheProfileUnchanged() {
        val session = axisSession()
        repeat(99) { assertEquals(DetectOutcome.Waiting, session.update(device("stick"), 0.1f)) }
        val outcome = session.update(device("stick"), 0.1f)
        assertEquals(DetectOutcome.Stopped, outcome)
        assertEquals(gamepadDefault(), gamepadDefault().applied(outcome))
    }

    @Test
    fun cancelStopsWithNoChange() {
        val session = axisSession()
        assertEquals(DetectOutcome.Stopped, session.cancel())
        source.axis("stick", 1, 1f)
        assertEquals("a stopped session takes nothing more", DetectOutcome.Stopped, session.update(device("stick"), 0.1f))
    }

    @Test
    fun theBackButtonCancelsAnAxisDetectButIsTakenByAButtonDetect() {
        val axis = axisSession("pad")
        source.press("pad", GAMEPAD_BUTTON_B)
        assertEquals(DetectOutcome.Stopped, axis.update(device("pad"), 0.1f))
        source.press("pad", GAMEPAD_BUTTON_B, false)

        val button = buttonSession("pad")
        source.press("pad", GAMEPAD_BUTTON_B)
        assertEquals(DetectOutcome.Button(GAMEPAD_BUTTON_B), button.update(device("pad"), 0.1f))

        val stick = axisSession()
        source.press("stick", 1)
        assertEquals("button 1 of a joystick goes back", DetectOutcome.Stopped, stick.update(device("stick"), 0.1f))
    }

    @Test
    fun aDeviceThatGoesAwayStopsTheDetect() {
        val session = axisSession()
        assertEquals(DetectOutcome.Stopped, session.update(null, 0.1f))
    }

    @Test
    fun remainingCountsDown() {
        val session = axisSession()
        session.update(device("stick"), 2.5f)
        assertEquals(7.5f, session.remaining, 1e-4f)
    }
}
