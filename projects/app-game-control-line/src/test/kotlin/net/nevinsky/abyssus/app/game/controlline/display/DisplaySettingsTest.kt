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
import java.nio.file.Files
import java.nio.file.Path

class DisplaySettingsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val home get() = temp.root.toPath().resolve("home/.abyssus-control-line")

    private fun file(): Path = home.resolve("display.json")

    @Test
    fun theDefaultFileIsInTheHomeFolder() =
        assertEquals(Path.of("/home/pilot/.abyssus-control-line/display.json"), defaultDisplayFile("/home/pilot"))

    @Test
    fun noFileMeansAWindow() {
        val settings = DisplaySettings(file())
        assertFalse(settings.fullscreen)
        assertFalse(settings.damaged)
        assertFalse("reading writes nothing", Files.exists(file()))
    }

    @Test
    fun aChangeSurvivesAReload() {
        DisplaySettings(file()).setFullscreen(true)
        assertTrue(DisplaySettings(file()).fullscreen)
        assertTrue(Files.readString(file()).contains("\"version\" : 1"))
        DisplaySettings(file()).setFullscreen(false)
        assertFalse(DisplaySettings(file()).fullscreen)
    }

    @Test
    fun anUnreadableFileIsRenamedAndAWindowIsUsed() {
        Files.createDirectories(home)
        Files.writeString(file(), "not json")
        val settings = DisplaySettings(file())
        assertTrue(settings.damaged)
        assertFalse(settings.fullscreen)
        assertTrue(Files.exists(home.resolve("display.json.bad")))
        assertFalse(Files.exists(file()))
    }

    @Test
    fun anUnknownVersionIsRefusedLikeAnUnreadableFile() {
        Files.createDirectories(home)
        Files.writeString(file(), """{"version": 2, "fullscreen": true}""")
        val settings = DisplaySettings(file())
        assertTrue(settings.damaged)
        assertFalse(settings.fullscreen)
    }

    @Test
    fun aMissingFieldIsAWindowAndANullIsRefused() {
        Files.createDirectories(home)
        Files.writeString(file(), """{"version": 1}""")
        assertFalse(DisplaySettings(file()).fullscreen)
        Files.writeString(file(), """{"version": 1, "fullscreen": null}""")
        assertTrue(DisplaySettings(file()).damaged)
    }
}
