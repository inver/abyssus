/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.display

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.nevinsky.abyssus.app.game.controlline.io.AtomicJsonStore
import java.nio.file.Files
import java.nio.file.Path

/** The version of `display.json` this game reads and writes. */
const val DISPLAY_VERSION = 1

/** The window's size when it is not fullscreen. */
const val WINDOW_WIDTH = 1280
const val WINDOW_HEIGHT = 720

/** `display.json`: [highQualityPost] is missing from a file written before it existed, and then on. */
private data class DisplayFile(
    val version: Int = DISPLAY_VERSION,
    val fullscreen: Boolean = false,
    val highQualityPost: Boolean = true,
)

/** Where the game keeps its display setting: `~/.abyssus-control-line/display.json`. */
fun defaultDisplayFile(home: String = System.getProperty("user.home")): Path =
    Path.of(home, ".abyssus-control-line", "display.json")

/**
 * Whether the game runs fullscreen (design decision 8), kept in [file] (`{version: 1, fullscreen: <boolean>}`) the way
 * the score tables are: written through a temporary file and an atomic move, and a file that cannot be read renamed to
 * `display.json.bad` with a window used and [damaged] set. No file means a window.
 */
class DisplaySettings(private val file: Path, private val json: ObjectMapper = jacksonObjectMapper()) {
    private val store = AtomicJsonStore()

    var fullscreen = false
        private set

    /**
     * Whether the field's final pass draws its costlier effects, bloom and ambient occlusion (OpenSpec
     * `control-line-post-processing`, "Post settings and fallback"); off for a modest GPU.
     */
    var highQualityPost = true
        private set

    /** The file there was could not be read; the settings screen says so. */
    var damaged = false
        private set

    init {
        val read = store.read(file, {
            val read = json.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .readValue(it.toFile(), DisplayFile::class.java)
            require(read.version == DISPLAY_VERSION) { "unknown version ${read.version}" }
            read
        }, { damaged = true })
        fullscreen = read?.fullscreen ?: false
        highQualityPost = read?.highQualityPost ?: true
    }

    /** Keeps [on] and saves the file. */
    fun setFullscreen(on: Boolean) {
        if (fullscreen == on) {
            return
        }
        fullscreen = on
        save()
    }

    /** Keeps [on] (the field's bloom and ambient occlusion) and saves the file. */
    fun setHighQualityPost(on: Boolean) {
        if (highQualityPost == on) {
            return
        }
        highQualityPost = on
        save()
    }

    private fun save() {
        store.save(file, "display") { temp ->
            Files.writeString(temp, json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(DisplayFile(DISPLAY_VERSION, fullscreen, highQualityPost))
                .replace("\r\n", "\n") + "\n")
        }
    }
}

/** What the settings screen reads from and sends to the display (design decision 8). Main thread only. */
interface DisplayOptions {
    /** The mode the window is in (or will be after the next frame). */
    val fullscreen: Boolean

    /** The old settings file could not be read. */
    val damaged: Boolean

    fun setFullscreen(on: Boolean)

    /** Whether the field draws bloom and ambient occlusion (the settings screen's "High quality effects"). */
    val highQualityPost: Boolean
        get() = true

    fun setHighQualityPost(on: Boolean) {}
}

/** The display of a game with no window of its own (tests, Play). */
data object NoDisplayOptions : DisplayOptions {
    override val fullscreen = false
    override val damaged = false
    override fun setFullscreen(on: Boolean) = Unit
}
