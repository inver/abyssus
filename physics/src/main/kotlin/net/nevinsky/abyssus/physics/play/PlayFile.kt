/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.play

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
class PlayFile(private val json: ObjectMapper = ObjectMapper()) {
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
        val root = json.readTree(text) as? ObjectNode ?: throw IllegalArgumentException("play.json is not a JSON object")
        val protocol = root.get("protocol")?.takeIf { it.isIntegralNumber }?.intValue()
            ?: throw IllegalArgumentException("play.json has no whole-number protocol")
        val module = root.get("module")?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("play.json names no module")
        return PlayConfig(protocol, module, strings(root, "classpath") ?: throw IllegalArgumentException("play.json has no classpath list"),
            strings(root, "jvmArgs").orEmpty())
    }

    private fun strings(root: ObjectNode, name: String): List<String>? =
        (root.get(name) as? ArrayNode)?.map { it.takeIf { e -> e.isTextual }?.textValue() ?: throw IllegalArgumentException("play.json $name holds a non-text entry") }

    private fun quoted(text: String) = json.writeValueAsString(text)

    private fun list(values: List<String>): String =
        if (values.isEmpty()) "[]" else values.joinToString(",\n", "[\n", "\n  ]") { "    " + quoted(it) }
}
