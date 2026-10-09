/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind

data class ProjectSettings(
    val physicsEnabled: Boolean,
    val problems: List<String>,
    val supported: Boolean = true,
)

class ProjectSettingsReader(private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()) {

    fun read(abssText: String): ProjectSettings = runCatchingKeepingCancellation {
        val root = SceneJson().parse(abssText)
        format.requireSupported(root, DocumentKind.PROJECT)
        val physicsEnabled = root.get("physicsEnabled")?.let { it.isBoolean && it.asBoolean() } ?: false
        val problems = if (root.has("physicsEnabled") && !root.get("physicsEnabled")!!.isBoolean) {
            listOf("physicsEnabled")
        } else {
            emptyList<String>()
        }
        ProjectSettings(physicsEnabled, problems)
    }.getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString()), supported = false) }
}
