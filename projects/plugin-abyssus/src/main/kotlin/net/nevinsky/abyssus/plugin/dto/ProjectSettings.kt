/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

data class ProjectSettings(
    val physicsEnabled: Boolean,
    val problems: List<String>,
    val supported: Boolean = true,
    val antialiasing: AntialiasingLevel = AntialiasingLevel.DEFAULT,
)

class ProjectSettingsReader(private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()) {

    fun read(abssText: String): ProjectSettings = runCatchingKeepingCancellation {
        val root = parseSupportedDocument(abssText, DocumentKind.PROJECT)
        val physicsEnabled = root.boolAt("physicsEnabled") ?: false
        val problems = mutableListOf<String>()
        if (root.hasMember("physicsEnabled") && root.boolAt("physicsEnabled") == null) {
            problems += "physicsEnabled"
        }
        val raw = root.textAt("sceneViewAntialiasing")
        val level = AntialiasingLevel.entries.firstOrNull { it.name == raw }
        if (root.hasMember("sceneViewAntialiasing") && level == null) problems += "sceneViewAntialiasing"
        ProjectSettings(physicsEnabled, problems, antialiasing = level ?: AntialiasingLevel.DEFAULT)
    }.getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString()), supported = false) }
}
