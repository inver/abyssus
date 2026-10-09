/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.lib.core.editor.content.RenderAsset

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.schema.ComponentSchemas
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.components.AddedEntity
import net.nevinsky.abyssus.lib.core.editor.components.AddedLight
import net.nevinsky.abyssus.lib.core.editor.components.AssetEntities
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.editor.components.LightEntities
import net.nevinsky.abyssus.lib.core.editor.components.LightPreset
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.plugin.filetype.editSceneJson
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache

/**
 * Adds, changes and removes components of a scene's entities as undoable commands on the scene file. The rules are
 * [net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor]'s; nothing is written unless an edit comes back [EditResult.Changed].
 */
object SceneComponentEdits {
    private fun readMeta(folder: VirtualFile, metaFiles: MetaFiles) =
        net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation { metaFiles.inEditor(folder) }.getOrNull()

    /** The models and terrains of the project [sceneFile] belongs to; empty for a scene outside a project. */
    fun renderAssets(sceneFile: VirtualFile, metaFiles: MetaFiles): List<RenderAsset> {
        val abss = ProjectLayout.abssFor(sceneFile) ?: return emptyList()
        return ProjectLayout.assetFolders(abss).mapNotNull { folder ->
            val type = readMeta(folder, metaFiles)?.type
            if (type == MetaType.MODEL || type == MetaType.TERRAIN) RenderAsset(type.name, folder.name) else null
        }.sortedBy { it.name }
    }

    private fun assetNames(sceneFile: VirtualFile, metaFiles: MetaFiles): Set<String>? =
        ProjectLayout.abssFor(sceneFile)?.let { renderAssets(sceneFile, metaFiles).mapTo(HashSet()) { it.name } }

    /** The asset folders of the project [sceneFile] belongs to by meta type (`MODEL`, ...); null outside a project. */
    fun assetsByType(sceneFile: VirtualFile, metaFiles: MetaFiles): Map<String, Set<String>>? {
        val abss = ProjectLayout.abssFor(sceneFile) ?: return null
        return ProjectLayout.assetFolders(abss).mapNotNull { folder ->
            readMeta(folder, metaFiles)?.type?.let { it.name to folder.name }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSortedSet() }
    }

    private fun editor(project: Project, file: VirtualFile) = ComponentSchemas.of(project).editorFor(file)

    private fun run(project: Project, file: VirtualFile, command: String, edit: (JsonNode) -> EditResult): EditResult {
        var result: EditResult = EditResult.Rejected(EditorBundle.message("componentSceneUnreadable"))
        editSceneJson(project, file, command) { root ->
            result = edit(root)
            result == EditResult.Changed
        }
        return result
    }

    fun addLight(project: Project, file: VirtualFile, preset: LightPreset, position: Vec3, cache: SceneDocumentCache): AddedLight {
        var added = AddedLight(EditResult.Rejected(EditorBundle.message("componentSceneUnreadable")))
        editSceneJson(project, file, AbyssusBundle.message("commandAddLight")) { root ->
            if (cache.read(file) == null) return@editSceneJson false
            added = LightEntities(EditorBundle).add(root, preset, position)
            added.result == EditResult.Changed
        }
        return added
    }

    /**
     * Adds [asset] of the project to the scene at [position] as one undoable command (a terrain centred on it, sized from
     * its `meta.json`); the result names the new entity.
     */
    fun addAsset(project: Project, file: VirtualFile, asset: RenderAsset, position: Vec3, cache: SceneDocumentCache, metaFiles: MetaFiles): AddedEntity {
        val size = if (asset.type == MetaType.TERRAIN.name) terrainSize(file, asset.name, metaFiles) else null
        var added = AddedEntity(EditResult.Rejected(EditorBundle.message("componentSceneUnreadable")))
        editSceneJson(project, file, AbyssusBundle.message("commandAddAsset")) { root ->
            if (cache.read(file) == null) return@editSceneJson false
            added = AssetEntities(EditorBundle).add(root, asset, position, size)
            added.result == EditResult.Changed
        }
        return added
    }

    /** A terrain asset's world size from its `meta.json`, or null when it cannot be read. */
    private fun terrainSize(sceneFile: VirtualFile, name: String, metaFiles: MetaFiles): Float? {
        val abss = ProjectLayout.abssFor(sceneFile) ?: return null
        val folder = ProjectLayout.assetFolders(abss).firstOrNull { it.name == name } ?: return null
        val size = readMeta(folder, metaFiles)?.json?.path("additional")?.path("size") ?: return null
        return size.takeIf { it.isNumber }?.floatValue()
    }

    /**
     * Creates a new entity (`Entity <id>`) holding a [kindName] component started from [initial], as one undoable command;
     * nothing is written when the component is refused. In an older wrapped scene its archetype follows its components.
     */
    fun addAsNewEntity(project: Project, file: VirtualFile, kindName: String, metaFiles: MetaFiles, initial: Map<String, String> = emptyMap()): AddedEntity {
        var added = AddedEntity(EditResult.Rejected(EditorBundle.message("componentSceneUnreadable")))
        editSceneJson(project, file, AbyssusBundle.message("commandAddComponent")) { root ->
            val id = SceneEntityTree(root).insert { id ->
                JsonNodeFactory.instance.objectNode().set(
                    "NameComponent", JsonNodeFactory.instance.objectNode().put("name", AbyssusBundle.message("newEntityName", id)),
                )
            } ?: return@editSceneJson false
            val result = editor(project, file).add(root, id, kindName, initial, assetNames(file, metaFiles), assetsByType(file, metaFiles))
            added = AddedEntity(result, id.takeIf { result == EditResult.Changed })
            result == EditResult.Changed && SceneEntityTree(root).matchArchetype(id)
        }
        return added
    }

    fun add(
        project: Project, file: VirtualFile, entityId: String, kindName: String, metaFiles: MetaFiles,
        initial: Map<String, String> = emptyMap(),
    ): EditResult = run(project, file, AbyssusBundle.message("commandAddComponent")) {
        editor(project, file).add(it, entityId, kindName, initial, assetNames(file, metaFiles), assetsByType(file, metaFiles))
    }

    fun update(
        project: Project, file: VirtualFile, entityId: String, kindName: String, field: String, text: String, metaFiles: MetaFiles,
    ): EditResult =
        run(project, file, AbyssusBundle.message("commandEditComponent")) {
            editor(project, file).update(it, entityId, kindName, field, text, assetNames(file, metaFiles), assetsByType(file, metaFiles))
        }

    fun remove(project: Project, file: VirtualFile, entityId: String, kindName: String): EditResult =
        run(project, file, AbyssusBundle.message("commandRemoveComponent")) { editor(project, file).remove(it, entityId, kindName) }
}
