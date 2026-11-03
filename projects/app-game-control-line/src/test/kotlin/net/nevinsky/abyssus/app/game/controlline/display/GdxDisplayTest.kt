/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GdxDisplayTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class FakeWindow(override var fullscreen: Boolean = false, var refuse: Boolean = false) : DisplayWindow {
        val calls = ArrayList<String>()

        override fun enterFullscreen(): Boolean {
            calls += "fullscreen"
            if (refuse) return false
            fullscreen = true
            return true
        }

        override fun enterWindowed(width: Int, height: Int): Boolean {
            calls += "window ${width}x$height"
            if (refuse) return false
            fullscreen = false
            return true
        }
    }

    private val file get() = temp.root.toPath().resolve("display.json")
    private val window = FakeWindow()
    private val display by lazy { GdxDisplay(DisplaySettings(file), window) }

    @Test
    fun aRequestIsSavedAtOnceAndAppliedOnTheNextFrameOnly() {
        assertFalse(display.fullscreen)
        display.setFullscreen(true)
        assertTrue("the checkbox shows the request at once", display.fullscreen)
        assertTrue("saved before the switch", DisplaySettings(file).fullscreen)
        assertEquals("not switched yet", emptyList<String>(), window.calls)

        display.applyPending()
        assertEquals(listOf("fullscreen"), window.calls)
        assertTrue(window.fullscreen)
        display.applyPending()
        assertEquals("applied once", listOf("fullscreen"), window.calls)
    }

    @Test
    fun turningItOffGoesBackToTheStandardWindow() {
        display.setFullscreen(true)
        display.applyPending()
        display.setFullscreen(false)
        display.applyPending()
        assertEquals(listOf("fullscreen", "window 1280x720"), window.calls)
        assertFalse(window.fullscreen)
        assertFalse(DisplaySettings(file).fullscreen)
    }

    @Test
    fun nothingAppliesWithoutARequestAndARepeatedRequestIsNotASwitch() {
        display.applyPending()
        assertEquals(emptyList<String>(), window.calls)
        display.setFullscreen(false)
        display.applyPending()
        assertEquals("already windowed", emptyList<String>(), window.calls)
    }

    @Test
    fun aSwitchTheWindowRefusesIsDroppedAndTheCheckboxShowsTheRealMode() {
        window.refuse = true
        display.setFullscreen(true)
        display.applyPending()
        assertFalse(display.fullscreen)
        assertFalse("the saved choice goes back to the real mode", DisplaySettings(file).fullscreen)
        display.applyPending()
        assertEquals("not retried every frame", listOf("fullscreen"), window.calls)
    }

    @Test
    fun theCheckboxShowsTheRealModeWhenNothingIsPending() {
        window.fullscreen = true
        assertTrue(display.fullscreen)
    }

    @Test
    fun anUnreadableFileIsReported() {
        java.nio.file.Files.writeString(file, "not json")
        assertTrue(display.damaged)
    }
}
