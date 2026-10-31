/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

data class ProjectSettings(
    val physicsEnabled: Boolean,
    val problems: List<String>,
    val supported: Boolean = true,
    val antialiasing: AntialiasingLevel = AntialiasingLevel.DEFAULT,
)

class ProjectSettingsReader(private val parsing: DocumentParsing) {

    fun read(abssText: String): ProjectSettings = runCatchingKeepingCancellation {
        val project = parsing.project(abssText)
        val problems = mutableListOf<String>()
        val raw = project.sceneViewAntialiasing
        val level = AntialiasingLevel.entries.firstOrNull { it.name == raw }
        if (raw != null && level == null) problems += "sceneViewAntialiasing"
        ProjectSettings(project.physicsEnabled ?: false, problems, antialiasing = level ?: AntialiasingLevel.DEFAULT)
    }.getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString()), supported = false) }
}
