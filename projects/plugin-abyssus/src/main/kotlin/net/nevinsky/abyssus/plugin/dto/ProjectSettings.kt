/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

data class ProjectSettings(
    val physicsEnabled: Boolean,
    val problems: List<String>,
    val supported: Boolean = true,
    val antialiasing: AntialiasingLevel = AntialiasingLevel.DEFAULT,
)

class ProjectSettingsReader(
    private val parsing: DocumentParsing,
    private val json: net.nevinsky.abyssus.lib.core.io.JsonProcessor,
) {

    /**
     * The two settings as the file holds them, of any type, so a wrong type is reported instead of failing the read.
     */
    private class RawFlags {
        var physicsPresent = false
        var physicsEnabled: Any? = null
            set(value) {
                physicsPresent = true
                field = value
            }

        var antialiasingPresent = false
        var sceneViewAntialiasing: Any? = null
            set(value) {
                antialiasingPresent = true
                field = value
            }

        @com.fasterxml.jackson.annotation.JsonAnySetter fun other(key: String, value: Any?) {}
    }

    fun read(abssText: String): ProjectSettings = runCatchingKeepingCancellation {
        parsing.requireSupported(abssText, net.nevinsky.abyssus.lib.core.format.DocumentKind.PROJECT)
        val flags = json.parse(abssText, RawFlags::class.java)
        val problems = mutableListOf<String>()
        val physics =
            when (val v = flags.physicsEnabled) {
                null -> {
                    if (flags.physicsPresent) {
                        problems += "physicsEnabled"
                    }
                    false
                }
                is Boolean -> v
                else -> {
                    problems += "physicsEnabled"
                    false
                }
            }
        val raw = flags.sceneViewAntialiasing
        val level = AntialiasingLevel.entries.firstOrNull { it.name == raw }
        if ((raw != null || flags.antialiasingPresent) && level == null) {
            problems += "sceneViewAntialiasing"
        }
        ProjectSettings(physics, problems, antialiasing = level ?: AntialiasingLevel.DEFAULT)
    }
        .getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString()), supported = false) }
}
