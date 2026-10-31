/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.nevinsky.abyssus.app.game.controlline.io.AtomicJsonStore
import java.nio.file.Files
import java.nio.file.Path

/** The version of `controls.json` this game reads and writes. */
const val CONTROLS_VERSION = 1

private data class ControlsFile(val version: Int = CONTROLS_VERSION, val devices: Map<String, ControllerProfile> = emptyMap())

/** Where the game keeps its controller settings: `~/.abyssus-control-line/controls.json`. */
fun defaultControlsFile(home: String = System.getProperty("user.home")): Path =
    Path.of(home, ".abyssus-control-line", "controls.json")

/**
 * The profile of each device by its GUID, kept in [file] (`{version: 1, devices: {<guid>: {...}}}`) the way the score
 * tables are: written through a temporary file and an atomic move, and a file that cannot be read renamed to
 * `controls.json.bad` with the defaults used and [damaged] set. A device with no saved profile has the default of its kind.
 * Main thread only.
 */
class ControllerProfiles(private val file: Path, private val json: ObjectMapper = jacksonObjectMapper()) {
    private val store = AtomicJsonStore()
    private var saved: Map<String, ControllerProfile> = emptyMap()

    /** The file there was could not be read; the settings screen says so. */
    var damaged = false
        private set

    init {
        saved = store.read(file, {
            val read = json.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .readValue(it.toFile(), ControlsFile::class.java)
            require(read.version == CONTROLS_VERSION) { "unknown version ${read.version}" }
            read.devices
        }, { damaged = true }).orEmpty()
    }

    /** The profile of the device [guid]: its saved one, or the default of a [gamepad] or a joystick. */
    fun profile(guid: String, gamepad: Boolean): ControllerProfile = saved[guid] ?: if (gamepad) gamepadDefault() else joystickDefault()

    /** Keeps [profile] for [guid] and saves the file. */
    fun put(guid: String, profile: ControllerProfile) {
        if (saved[guid] == profile) return
        saved = saved + (guid to profile)
        save()
    }

    /** Drops the saved profile of [guid], so its kind's default applies again, and saves the file. */
    fun reset(guid: String) {
        if (guid !in saved) return
        saved = saved - guid
        save()
    }

    private fun save() {
        store.save(file, "controls") { temp ->
            Files.writeString(temp, json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(ControlsFile(CONTROLS_VERSION, saved)).replace("\r\n", "\n") + "\n")
        }
    }
}
