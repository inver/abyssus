/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

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
        val root = SceneJson().parse(abssText)
        format.requireSupported(root, DocumentKind.PROJECT)
        val physicsEnabled = root.get("physicsEnabled")?.let { it.isBoolean && it.asBoolean() } ?: false
        val problems = mutableListOf<String>()
        if (root.has("physicsEnabled") && !root.get("physicsEnabled")!!.isBoolean) {
            problems += "physicsEnabled"
        }
        val raw = root.get("sceneViewAntialiasing")
        val level = AntialiasingLevel.entries.firstOrNull { raw?.isTextual == true && it.name == raw.textValue() }
        if (raw != null && level == null) problems += "sceneViewAntialiasing"
        ProjectSettings(physicsEnabled, problems, antialiasing = level ?: AntialiasingLevel.DEFAULT)
    }.getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString()), supported = false) }
}
