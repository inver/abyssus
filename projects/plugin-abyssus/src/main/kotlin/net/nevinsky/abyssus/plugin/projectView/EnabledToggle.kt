/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.plugin.filetype.setEnabled
import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.dto.SceneEntry
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface
import net.nevinsky.abyssus.plugin.water.WaterSceneEdits

private fun JsonNode.child(key: String): JsonNode? = when (this) {
    is ObjectNode -> get(key)
    is ArrayNode -> key.toIntOrNull()?.let { if (it in 0 until size()) get(it) else null }
    else -> null
}

private fun JsonNode.at(keys: List<String>): JsonNode? = keys.fold(this as JsonNode?) { e, k -> e?.child(k) }

/**
 * Flips the `<x>Enabled` boolean that gates [entry] in the file it was read from.
 * Returns false when the entry has no toggle or the file no longer matches what was read.
 */
fun toggleEnabled(project: Project, entry: DtoEntry): Boolean {
    val file = entry.source ?: return false
    val toggle = entry.toggleName ?: return false
    val current = entry.enabled ?: return false
    return editSceneJson(project, file, AbyssusBundle.message("commandToggleEnabled")) { root ->
        val target = root.at(entry.parentKeys) as? ObjectNode ?: return@editSceneJson false
        if (target.get(toggle)?.isBoolean != true) return@editSceneJson false
        target.set<JsonNode>(toggle, BooleanNode.valueOf(!current))
        true
    }
}

/** Flips the eye of [entry]: a water surface's own `enabled`, else the `<x>Enabled` toggle that gates it. */
fun toggleRow(project: Project, entry: DtoEntry): Boolean {
    entityOrComponentTarget(entry)?.let { (file, target) ->
        val enabled = entry.enabled ?: return false
        return setEnabled(project, file, target, !enabled)
    }
    val surface = entry.value as? WaterSurface ?: return toggleEnabled(project, entry)
    val file = entry.source ?: return false
    val enabled = entry.enabled ?: return false
    return WaterSceneEdits(project).setEnabled(file, surface.id, !enabled) == WaterEdit.Changed
}

/** The `.scene` file behind a scene entry listed under a project, or null for any other entry. */
fun sceneFileOf(entry: DtoEntry): VirtualFile? =
    (entry.value as? SceneEntry)?.file?.takeIf { it.extension == ProjectLayout.SCENE_EXTENSION }

fun sceneNameOf(project: Project, file: VirtualFile): String? = SceneDocumentCache.of(project).read(file)?.scene?.name

/** Sets the scene's `skyboxName` to [name] (an asset folder, or null for none); false, writing nothing, when it already is. */
fun setSkybox(project: Project, file: VirtualFile, name: String?): Boolean =
    editSceneJson(project, file, AbyssusBundle.message("commandSetSkybox")) { root ->
        val scene = root as? ObjectNode ?: return@editSceneJson false
        val value: JsonNode = name?.let(TextNode::valueOf) ?: NullNode.instance
        if ((scene.get(SKYBOX_KEY) ?: NullNode.instance) == value) return@editSceneJson false
        scene.set<JsonNode>(SKYBOX_KEY, value)
        true
    }

/** The scene property holding the skybox asset folder. */
const val SKYBOX_KEY = "skyboxName"

fun renameScene(project: Project, file: VirtualFile, newName: String): Boolean =
    editSceneJson(project, file, AbyssusBundle.message("commandRenameScene")) { root ->
        val scene = root as? ObjectNode ?: return@editSceneJson false
        scene.set<JsonNode>("name", TextNode.valueOf(newName))
        true
    }


/** The scene file and target an entity row or a component row of a scene switches, or null for any other row. */
private fun entityOrComponentTarget(entry: DtoEntry): Pair<VirtualFile, EnabledTarget>? {
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return when {
        isEntityEntry(entry) -> file to EnabledTarget.Entity(entry.name)
        isComponentEntry(entry) -> file to EnabledTarget.Component(entry.parentKeys[entry.parentKeys.size - 2], entry.name)
        else -> null
    }
}
