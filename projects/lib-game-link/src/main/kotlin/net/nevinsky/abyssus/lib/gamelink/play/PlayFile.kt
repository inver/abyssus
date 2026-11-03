/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gamelink.play

import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.lib.core.editor.json.JsonObjectWriter
import net.nevinsky.abyssus.lib.core.io.JsonObjectReader
import net.nevinsky.abyssus.lib.core.io.JsonObjectView
import java.nio.file.Files
import java.nio.file.Path

/** Where a project keeps its play launch file, relative to the project folder (next to the component schema). */
const val PLAY_FILE = "abyssus/play.json"

/**
 * What Abyssus Physics launches for Play: the [module] class ([PlayModule]) on [classpath] (absolute paths), with
 * [jvmArgs], speaking play [protocol].
 */
data class PlayConfig(val protocol: Int, val module: String, val classpath: List<String>, val jvmArgs: List<String> = emptyList())

/**
 * Writes and reads `abyssus/play.json`. Writing is stable: two-space indent, LF line ends and a final newline, so the
 * same configuration gives the same bytes.
 */
class PlayFile(json: ObjectMapper = ObjectMapper()) {
    private val reader = JsonObjectReader(json)
    private val writer = JsonObjectWriter(json)

    fun write(config: PlayConfig): String {
        val out = StringBuilder("{\n")
        out.append("  \"protocol\": ").append(config.protocol).append(",\n")
        out.append("  \"module\": ").append(quoted(config.module)).append(",\n")
        out.append("  \"classpath\": ").append(list(config.classpath))
        if (config.jvmArgs.isNotEmpty()) out.append(",\n  \"jvmArgs\": ").append(list(config.jvmArgs))
        return out.append("\n}\n").toString()
    }

    /** Writes [config] to `<projectDir>/abyssus/play.json`, creating the folder; returns the file. */
    fun export(config: PlayConfig, projectDir: Path): Path {
        val file = projectDir.resolve(PLAY_FILE)
        Files.createDirectories(file.parent)
        Files.writeString(file, write(config))
        return file
    }

    /** The configuration [text] holds; throws [IllegalArgumentException] naming what is missing or malformed. */
    fun parse(text: String): PlayConfig {
        val root = reader.parse(text) ?: throw IllegalArgumentException("play.json is not a JSON object")
        val protocol = root.int("protocol") ?: throw IllegalArgumentException("play.json has no whole-number protocol")
        val module = root.text("module")?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("play.json names no module")
        return PlayConfig(protocol, module, strings(root, "classpath") ?: throw IllegalArgumentException("play.json has no classpath list"),
            strings(root, "jvmArgs").orEmpty())
    }

    private fun strings(root: JsonObjectView, name: String): List<String>? =
        try { root.strings(name) } catch (e: IllegalArgumentException) { throw IllegalArgumentException("play.json ${e.message}") }

    private fun quoted(text: String) = writer.quoted(text)

    private fun list(values: List<String>): String =
        if (values.isEmpty()) "[]" else values.joinToString(",\n", "[\n", "\n  ]") { "    " + quoted(it) }
}
