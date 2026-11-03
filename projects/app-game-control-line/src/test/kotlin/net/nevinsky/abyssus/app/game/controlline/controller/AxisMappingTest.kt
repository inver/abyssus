/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisMappingTest {
    private val pad = gamepadDefault()

    @Test
    fun aStickFullyUpGivesFullTilt() = assertEquals(1f, pad.tiltOf(1f), 1e-6f)

    @Test
    fun aStickFullyDownGivesFullDownTilt() = assertEquals(-1f, pad.tiltOf(-1f), 1e-6f)

    @Test
    fun insideTheDeadZoneIsNeutral() {
        assertEquals(0f, pad.tiltOf(0.08f), 0f)
        assertEquals(0f, pad.tiltOf(-0.1f), 0f)
    }

    @Test
    fun pastTheDeadZoneRisesFromZeroToOne() {
        assertEquals(0.5f, pad.tiltOf(0.55f), 1e-5f)
        assertEquals(-0.5f, pad.tiltOf(-0.55f), 1e-5f)
        assertEquals(0.0111f, pad.tiltOf(0.11f), 1e-3f)
    }

    @Test
    fun aPedalThatRestsAtOneEndUsesTheWholeTravel() {
        val pedal = ControllerProfile(axis = 0, rest = -1f, deadZone = 0f)
        assertEquals(0f, pedal.tiltOf(-1f), 0f)
        assertEquals(0.5f, pedal.tiltOf(0f), 1e-6f)
        assertEquals(1f, pedal.tiltOf(1f), 1e-6f)
    }

    @Test
    fun aStickThatRestsOffCentreScalesEachSideToFullTravel() {
        val drifted = ControllerProfile(axis = 0, rest = 0.2f, deadZone = 0f)
        assertEquals(0f, drifted.tiltOf(0.2f), 1e-6f)
        assertEquals(1f, drifted.tiltOf(1f), 1e-6f)
        assertEquals(-1f, drifted.tiltOf(-1f), 1e-6f)
        assertEquals(0.5f, drifted.tiltOf(0.6f), 1e-6f)
    }

    @Test
    fun invertFlipsTheSign() {
        assertEquals(-1f, pad.copy(invert = true).tiltOf(1f), 1e-6f)
        assertEquals(1f, pad.copy(invert = true).tiltOf(-1f), 1e-6f)
    }

    @Test
    fun theCubicCurveSoftensTheMiddle() {
        val cubic = pad.copy(deadZone = 0f, curve = 1f)
        assertEquals(0.125f, cubic.tiltOf(0.5f), 1e-6f)
        assertEquals(1f, cubic.tiltOf(1f), 1e-6f)
        val half = pad.copy(deadZone = 0f, curve = 0.5f)
        assertEquals(0.3125f, half.tiltOf(0.5f), 1e-6f)
    }

    @Test
    fun valuesOutsideTheRangeAreClampedAndNanIsNeutral() {
        assertEquals(1f, pad.tiltOf(3f), 1e-6f)
        assertEquals(-1f, pad.tiltOf(-3f), 1e-6f)
        assertEquals(0f, pad.tiltOf(Float.NaN), 0f)
    }

    @Test
    fun withoutAChosenAxisOrWithTooFewAxesTheTiltIsZero() {
        assertEquals(0f, joystickDefault().tiltOf(floatArrayOf(1f, 1f)), 0f)
        assertEquals(0f, ControllerProfile(axis = 5).tiltOf(floatArrayOf(1f, 1f)), 0f)
        assertEquals(1f, ControllerProfile(axis = 1, deadZone = 0f).tiltOf(floatArrayOf(0f, 1f)), 1e-6f)
    }
}
