/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.editSceneSettings
import net.nevinsky.abyssus.plugin.filetype.setEnabled
import net.nevinsky.abyssus.plugin.filetype.setSceneToggle
import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.SceneEntry

/**
 * Flips the `<x>Enabled` boolean that gates [entry] in the file it was read from.
 * Returns false when the entry has no toggle or the file no longer matches what was read.
 */
fun toggleEnabled(project: Project, entry: DtoEntry): Boolean {
    val file = entry.source ?: return false
    val toggle = entry.toggleName ?: return false
    val current = entry.enabled ?: return false
    return setSceneToggle(project, file, toggle, !current)
}

/** Flips the eye of [entry]: an entity's or a component's own state, else the `<x>Enabled` toggle that gates it. */
fun toggleRow(project: Project, entry: DtoEntry): Boolean {
    entityOrComponentTarget(entry)?.let { (file, target) ->
        val enabled = entry.enabled ?: return false
        return setEnabled(project, file, target, !enabled)
    }
    return toggleEnabled(project, entry)
}

/** The `.scene` file behind a scene entry listed under a project, or null for any other entry. */
fun sceneFileOf(entry: DtoEntry): VirtualFile? =
    (entry.value as? SceneEntry)?.file?.takeIf { it.extension == ProjectLayout.SCENE_EXTENSION }

fun sceneNameOf(project: Project, file: VirtualFile): String? = SceneDocumentCache.of(project).read(file)?.scene?.name

/**
 * Sets the scene's `skyboxName` to [name] (an asset folder, or null for none); false, writing nothing, when it already
 * is.
 */
fun setSkybox(project: Project, file: VirtualFile, name: String?): Boolean =
    editSceneSettings(project, file, AbyssusBundle.message("commandSetSkybox")) { it.copy(skyboxName = name) }

/** The scene property holding the skybox asset folder. */
const val SKYBOX_KEY = "skyboxName"

fun renameScene(project: Project, file: VirtualFile, newName: String): Boolean =
    editSceneSettings(project, file, AbyssusBundle.message("commandRenameScene")) { it.copy(name = newName) }


/** The scene file and target an entity row or a component row of a scene switches, or null for any other row. */
private fun entityOrComponentTarget(entry: DtoEntry): Pair<VirtualFile, EnabledTarget>? {
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return when (val value = entry.value) {
        is EntityNode -> file to EnabledTarget.Entity(value.id)
        is ComponentNode -> file to EnabledTarget.Component(value.entityId, value.kind)
        else -> null
    }
}
