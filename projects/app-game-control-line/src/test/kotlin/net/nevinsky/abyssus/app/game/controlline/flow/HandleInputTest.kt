/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flow

import net.nevinsky.abyssus.app.game.controlline.input.HandleInput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleInputTest {
    private val input = HandleInput()

    /** Updates in frames of 1/120 s for [seconds]. */
    private fun run(seconds: Float) = repeat(Math.round(seconds * 120)) { input.update(1f / 120f) }

    @Test
    fun wReachesFullUpInFifteenHundredthsAndReturnsOnRelease() {
        input.key("W", true)
        run(0.075f)
        assertEquals(0.5f, input.tilt, 0.01f)
        run(0.075f)
        assertEquals(1f, input.tilt, 0f)
        input.key("W", false)
        run(0.15f)
        assertEquals(0f, input.tilt, 0f)
    }

    @Test
    fun downKeysTiltDown() {
        assertTrue(input.key("Down", true))
        run(0.2f)
        assertEquals(-1f, input.tilt, 0f)
        input.key("Down", false)
        assertTrue(input.key("S", true))
        run(0.2f)
        assertEquals(-1f, input.tilt, 0f)
    }

    @Test
    fun theProtocolsKeyNamesWorkToo() {
        assertTrue(input.key("UP", true))
        run(0.2f)
        assertEquals(1f, input.tilt, 0f)
        assertFalse(input.key("SPACE", true))
    }

    @Test
    fun mouseHeightSetsTiltByDistanceFromTheCentre() {
        input.mouseMoved(360f, 720f)
        input.update(1f / 120f)
        assertEquals(0f, input.tilt, 0f)
        input.mouseMoved(360f - 90f, 720f)
        input.update(1f / 120f)
        assertEquals(0.5f, input.tilt, 1e-6f)
        input.mouseMoved(720f, 720f)
        input.update(1f / 120f)
        assertEquals(-1f, input.tilt, 0f)
    }

    @Test
    fun theLastUsedDeviceWins() {
        input.mouseMoved(0f, 720f)
        input.update(1f / 120f)
        assertEquals(1f, input.tilt, 0f)
        input.key("S", true)
        run(0.3f)
        assertEquals(-1f, input.tilt, 0f)
        input.mouseMoved(360f, 720f)
        input.update(1f / 120f)
        assertEquals(0f, input.tilt, 0f)
    }

    @Test
    fun aStickSetsTheTiltAtOnceWithNoRamp() {
        input.pad(0.6f)
        run(1f / 120f)
        assertEquals(0.6f, input.tilt, 0f)
        input.pad(-1f)
        run(1f / 120f)
        assertEquals(-1f, input.tilt, 0f)
    }

    @Test
    fun aKeyAfterTheStickTakesControlBack() {
        input.pad(1f)
        run(0.01f)
        assertEquals(1f, input.tilt, 0f)
        input.key("S", true)
        run(0.3f)
        assertEquals(-1f, input.tilt, 0f)
    }

    @Test
    fun theStickAfterAKeyTakesControlWhenItLeavesTheDeadZone() {
        input.key("S", true)
        run(0.3f)
        input.pad(0f)
        run(0.01f)
        assertEquals("a resting stick leaves the keys in control", -1f, input.tilt, 0f)
        input.pad(0.5f)
        run(0.01f)
        assertEquals(0.5f, input.tilt, 0f)
    }

    @Test
    fun aRestingStickNeverOverridesW() {
        input.key("W", true)
        repeat(30) { input.pad(0f); run(0.01f) }
        assertEquals(1f, input.tilt, 0f)
    }

    @Test
    fun theStickReturnedToCentreIsNeutralAndStaysInControl() {
        input.pad(1f)
        run(0.01f)
        input.pad(0f)
        run(0.01f)
        assertEquals(0f, input.tilt, 0f)
        // a held key is not seen until it is pressed again
        input.key("W", true)
        run(0.3f)
        assertEquals(1f, input.tilt, 0f)
    }

    @Test
    fun theStickAfterTheMouseTakesControlAndTheMouseTakesItBack() {
        input.mouseMoved(100f, 720f)
        run(0.01f)
        input.pad(-0.4f)
        run(0.01f)
        assertEquals(-0.4f, input.tilt, 0f)
        input.mouseMoved(0f, 720f)
        run(0.01f)
        assertEquals(1f, input.tilt, 0f)
    }

    @Test
    fun unplugMidHoldGoesNeutralAndTheKeysWorkAgain() {
        input.pad(1f)
        run(0.01f)
        input.padLost()
        run(0.01f)
        assertEquals(0f, input.tilt, 0f)
        input.key("W", true)
        run(0.3f)
        assertEquals(1f, input.tilt, 0f)
    }

    @Test
    fun losingAControllerThatWasNotControllingChangesNothing() {
        input.key("W", true)
        run(0.3f)
        input.padLost()
        run(0.01f)
        assertEquals(1f, input.tilt, 0f)
    }

    @Test
    fun resetAfterAStickFlightIsNeutralOnTheKeys() {
        input.pad(1f)
        run(0.01f)
        input.reset()
        run(0.01f)
        assertEquals(0f, input.tilt, 0f)
        input.pad(0f)
        run(0.01f)
        assertEquals(0f, input.tilt, 0f)
    }

    @Test
    fun anOutOfRangeOrNanStickIsClampedOrNeutral() {
        input.pad(5f)
        run(0.01f)
        assertEquals(1f, input.tilt, 0f)
        input.pad(Float.NaN)
        run(0.01f)
        assertEquals(0f, input.tilt, 0f)
    }
}
