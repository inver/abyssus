/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeControllerSourceTest {
    @Test
    fun devicesAppearAndDisappear() {
        val source = FakeControllerSource()
        assertEquals(emptyList<String>(), source.poll().map { it.guid })
        source.plug("pad", "Logitech F310", gamepad = true).plug("stick", "Extreme 3D Pro")
        assertEquals(listOf("pad", "stick"), source.poll().map { it.guid })
        assertEquals(listOf(true, false), source.poll().map { it.gamepad })
        source.unplug("pad")
        assertEquals(listOf("stick"), source.poll().map { it.guid })
    }

    @Test
    fun stateChangesBetweenPollsAndASnapshotIsNotChangedAfterwards() {
        val source = FakeControllerSource().plug("pad", gamepad = true)
        val before = source.poll().single()
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.75f)
        source.press("pad", GAMEPAD_BUTTON_START)
        source.hat("pad", HAT_UP or HAT_LEFT)
        val after = source.poll().single()
        assertEquals(0f, before.axis(GAMEPAD_AXIS_LEFT_Y), 0f)
        assertFalse(before.pressed(GAMEPAD_BUTTON_START))
        assertEquals(0.75f, after.axis(GAMEPAD_AXIS_LEFT_Y), 0f)
        assertTrue(after.pressed(GAMEPAD_BUTTON_START))
        assertEquals(HAT_UP or HAT_LEFT, after.hat)
    }

    @Test
    fun aGamepadHasTheStandardLayoutAndOutOfRangeReadsAreSafe() {
        val pad = FakeControllerSource().plug("pad", gamepad = true).poll().single()
        assertEquals(6, pad.axes.size)
        assertEquals(15, pad.buttons.size)
        assertEquals(0f, pad.axis(99), 0f)
        assertFalse(pad.pressed(99))
    }
}
