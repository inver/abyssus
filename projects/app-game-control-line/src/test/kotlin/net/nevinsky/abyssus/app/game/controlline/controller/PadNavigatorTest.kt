/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import com.badlogic.gdx.Input
import org.junit.Assert.assertEquals
import org.junit.Test

class PadNavigatorTest {
    private val source = FakeControllerSource().plug("pad", gamepad = true).plug("stick", gamepad = false)
    private val nav = PadNavigator()

    private fun step(seconds: Float = 1f / 60f, enabled: Boolean = true) = nav.update(source.poll(), seconds, enabled)

    @Test
    fun aDirectionPressGivesOneKey() {
        source.hat("pad", HAT_DOWN)
        assertEquals(listOf(Input.Keys.DOWN), step())
        assertEquals(emptyList<Int>(), step())
    }

    @Test
    fun aHeldDirectionRepeatsAfterAFifthOfASecondThenEveryTwelveHundredths() {
        source.hat("pad", HAT_DOWN)
        val keys = ArrayList<Pair<Float, Int>>()
        var t = 0f
        repeat(120) {
            step().forEach { keys += t to it }
            t += 1f / 60f
        }
        // the first at once, the second near 0.4 s, then about every 0.12 s: 1 + 1 + floor((2 - 0.4) / 0.12) = 15 within 2 s
        assertEquals(Input.Keys.DOWN, keys.first().second)
        assertEquals(0f, keys[0].first, 0f)
        assertEquals(0.4f, keys[1].first, 0.02f)
        assertEquals(0.52f, keys[2].first, 0.02f)
        assertEquals(15, keys.size)
    }

    @Test
    fun releasingStopsTheRepeat() {
        source.hat("pad", HAT_UP)
        step()
        source.hat("pad", 0)
        assertEquals(emptyList<Int>(), (1..60).flatMap { step() })
    }

    @Test
    fun theFourDirectionsMapToTheArrowKeys() {
        for ((hat, key) in listOf(HAT_UP to Input.Keys.UP, HAT_RIGHT to Input.Keys.RIGHT, HAT_DOWN to Input.Keys.DOWN, HAT_LEFT to Input.Keys.LEFT)) {
            source.hat("pad", hat)
            assertEquals(listOf(key), step())
            source.hat("pad", 0)
            step()
        }
    }

    @Test
    fun aJoysticksFirstHatNavigatesToo() {
        source.hat("stick", HAT_RIGHT)
        assertEquals(listOf(Input.Keys.RIGHT), step())
    }

    @Test
    fun theLeftStickOfAGamepadActsPastHalfItsTravelOnly() {
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.4f)
        assertEquals(emptyList<Int>(), step())
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.6f)
        assertEquals(listOf(Input.Keys.UP), step())
        // a little hysteresis: still on at 0.4, off below 0.3
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.4f)
        assertEquals(emptyList<Int>(), step())
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.2f)
        step()
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, -0.7f)
        assertEquals(listOf(Input.Keys.DOWN), step())
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0f)
        source.axis("pad", GAMEPAD_AXIS_LEFT_X, -0.9f)
        step()
        assertEquals(emptyList<Int>(), step())
    }

    @Test
    fun aJoysticksAxesDoNotNavigate() {
        source.axis("stick", 0, 1f)
        source.axis("stick", 1, 1f)
        assertEquals(emptyList<Int>(), step())
    }

    @Test
    fun confirmIsEnterAndBackIsEscapeOnePressEach() {
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals(listOf(Input.Keys.ENTER), step())
        assertEquals(emptyList<Int>(), step())
        source.press("pad", GAMEPAD_BUTTON_A, false)
        source.press("pad", GAMEPAD_BUTTON_B)
        assertEquals(listOf(Input.Keys.ESCAPE), step())
        source.press("pad", GAMEPAD_BUTTON_B, false)
        source.press("stick", 0)
        assertEquals("button 0 of a joystick confirms", listOf(Input.Keys.ENTER), step())
        source.press("stick", 0, false)
        source.press("stick", 1)
        assertEquals("button 1 of a joystick goes back", listOf(Input.Keys.ESCAPE), step())
    }

    @Test
    fun nothingIsSentWhileFlyingOrDetecting() {
        source.hat("pad", HAT_DOWN)
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals(emptyList<Int>(), (1..60).flatMap { step(enabled = false) })
    }

    @Test
    fun aButtonStillHeldWhenNavigationComesBackIsNotANewPress() {
        source.press("pad", GAMEPAD_BUTTON_A)
        source.hat("pad", HAT_DOWN)
        step(enabled = false)
        assertEquals(emptyList<Int>(), step(1f / 60f, enabled = true))
        source.press("pad", GAMEPAD_BUTTON_A, false)
        step()
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals("a fresh press counts", listOf(Input.Keys.ENTER), step().filter { it == Input.Keys.ENTER })
    }

    @Test
    fun anyOfSeveralDevicesNavigates() {
        source.hat("stick", HAT_LEFT)
        assertEquals(listOf(Input.Keys.LEFT), step())
        source.hat("stick", 0)
        step()
        source.press("pad", GAMEPAD_BUTTON_A)
        assertEquals(listOf(Input.Keys.ENTER), step())
    }

    @Test
    fun unpluggingWhileHeldDoesNotLeaveAKeyStuck() {
        source.hat("pad", HAT_DOWN)
        step()
        source.unplug("pad")
        source.unplug("stick")
        assertEquals(emptyList<Int>(), (1..60).flatMap { step() })
    }
}
