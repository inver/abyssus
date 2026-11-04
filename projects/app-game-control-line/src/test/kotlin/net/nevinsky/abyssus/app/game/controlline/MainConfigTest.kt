/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Test

/** Headless: the window configuration of `main` (spec `control-line-runtime-stability`, "starts without a reported monitor"). */
class MainConfigTest {
    // libGDX keeps the window's position and fullscreen mode package-private
    private fun field(config: Lwjgl3ApplicationConfiguration, name: String): Any? =
        Lwjgl3WindowConfiguration::class.java.getDeclaredField(name).apply { isAccessible = true }.get(config)

    private val noDisplayMode: () -> Graphics.DisplayMode = { fail("no display mode is asked for without a monitor"); error("") }

    @Test fun withoutAMonitorASavedFullscreenStartsWindowedAtAFixedPosition() {
        val log = RecordingLogger()
        val config = gameConfiguration(savedFullscreen = true, primaryMonitor = false, log = log, displayMode = noDisplayMode)

        assertNull("windowed for this start", field(config, "fullscreenMode"))
        assertEquals(NO_MONITOR_WINDOW_X, field(config, "windowX"))
        assertEquals(NO_MONITOR_WINDOW_Y, field(config, "windowY"))
        val warning = log.warnings.single()
        assertTrue(warning, warning.contains("No primary monitor") && warning.contains("instead of fullscreen"))
    }

    @Test fun withoutAMonitorAWindowedStartIsPositionedAndLogged() {
        val log = RecordingLogger()
        val config = gameConfiguration(savedFullscreen = false, primaryMonitor = false, log = log, displayMode = noDisplayMode)
        assertEquals(NO_MONITOR_WINDOW_X, field(config, "windowX"))
        assertTrue(log.warnings.single().contains("No primary monitor"))
    }

    @Test fun withAMonitorTheWindowIsCentredByLibGdx() {
        val log = RecordingLogger()
        val config = gameConfiguration(savedFullscreen = false, primaryMonitor = true, log = log, displayMode = noDisplayMode)
        assertEquals("-1 is libGDX's 'centre the window'", -1, field(config, "windowX"))
        assertNull(field(config, "fullscreenMode"))
        assertTrue(log.warnings.isEmpty())
    }
}
