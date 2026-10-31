/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView.scene

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.components.*
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.filetype.editSceneDocument
import net.nevinsky.abyssus.plugin.sceneview.ArrangementHeights
import net.nevinsky.abyssus.plugin.schema.ComponentSchemas

/**
 * Adds, changes and removes components of a scene's entities as undoable commands on the scene file. The rules are
 * [net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor]'s; nothing is written unless an edit comes back [EditResult.Changed].
 */
object SceneComponentEdits {
    private fun readMeta(folder: VirtualFile, metaFiles: MetaFiles) =
        runCatchingKeepingCancellation { metaFiles.inEditor(folder) }.getOrNull()

    /** The models and terrains of the project [sceneFile] belongs to; empty for a scene outside a project. */
    fun renderAssets(sceneFile: VirtualFile, metaFiles: MetaFiles): List<BaseMeta> {
        val abss = ProjectLayout.abssFor(sceneFile) ?: return emptyList()
        return ProjectLayout.assetFolders(abss).mapNotNull { folder ->
            val type = readMeta(folder, metaFiles)?.type
            if (type == MetaType.MODEL || type == MetaType.TERRAIN) {
                BaseMeta(type, folder.name)
            } else null
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

    private fun run(
        project: Project, file: VirtualFile, command: String, global: Boolean = false,
        edit: (SceneDto) -> EditResult,
    ): EditResult {
        var result: EditResult = EditResult.Rejected(EditorBundle.message("componentSceneUnreadable"))
        editSceneDocument(project, file, command, global) { scene ->
            result = edit(scene)
            result == EditResult.Changed
        }
        return result
    }

    fun addLight(
        project: Project,
        file: VirtualFile,
        preset: LightPreset,
        position: Vector3,
        cache: SceneDocumentCache
    ): AddedLight {
        var added = AddedLight(EditResult.Rejected(EditorBundle.message("componentSceneUnreadable")))
        editSceneDocument(project, file, AbyssusBundle.message("commandAddLight")) { scene ->
            if (cache.read(file) == null) return@editSceneDocument false
            added = LightEntities(EditorBundle).add(scene, preset, position)
            added.result == EditResult.Changed
        }
        return added
    }

    /**
     * Adds [asset] of the project to the scene at [position] as one undoable command (a terrain centred on it, sized from
     * its `meta.json`); the result names the new entity. A model with [meshId] draws only that mesh.
     */
    fun addAsset(
        project: Project,
        file: VirtualFile,
        asset: BaseMeta,
        position: Vector3,
        cache: SceneDocumentCache,
        metaFiles: MetaFiles,
        meshId: String? = null,
    ): AddedEntity {
        val size = if (asset.isTerrain()) {
            terrainSize(file, asset.name, metaFiles)
        } else null
        var added = AddedEntity(EditResult.Rejected(EditorBundle.message("componentSceneUnreadable")))
        editSceneDocument(project, file, AbyssusBundle.message("commandAddAsset")) { scene ->
            if (cache.read(file) == null) return@editSceneDocument false
            added = AssetEntities(EditorBundle).add(scene, asset, position, size, meshId)
            added.result == EditResult.Changed
        }
        return added
    }

    /** A terrain asset's world size from its `meta.json`, or null when it cannot be read. */
    private fun terrainSize(sceneFile: VirtualFile, name: String, metaFiles: MetaFiles): Float? {
        val abss = ProjectLayout.abssFor(sceneFile) ?: return null
        val folder = ProjectLayout.assetFolders(abss).firstOrNull { it.name == name } ?: return null
        val terrain = readMeta(folder, metaFiles)?.bound()?.additional as? TerrainMeta ?: return null
        return terrain.size.takeIf { it > 0 }?.toFloat()
    }

    /**
     * Creates a new entity (`Entity <id>`) holding a [kindName] component started from [initial], as one undoable command;
     * nothing is written when the component is refused.
     */
    fun addAsNewEntity(
        project: Project,
        file: VirtualFile,
        kindName: String,
        metaFiles: MetaFiles,
        initial: Map<String, String> = emptyMap()
    ): AddedEntity {
        var added = AddedEntity(EditResult.Rejected(EditorBundle.message("componentSceneUnreadable")))
        editSceneDocument(project, file, AbyssusBundle.message("commandAddComponent")) { scene ->
            val id = scene.add(EntityType.OBJECT) { id ->
                listOf(NameComponent(AbyssusBundle.message("newEntityName", id.toString())))
            }
            val result = editor(project, file).add(
                scene,
                id,
                kindName,
                initial,
                assetNames(file, metaFiles),
                assetsByType(file, metaFiles)
            )
            added = AddedEntity(result, id.takeIf { result == EditResult.Changed })
            // a refused component leaves the new entity out of the scene again
            if (result != EditResult.Changed) scene.ecs.remove(id)
            result == EditResult.Changed
        }
        return added
    }

    fun add(
        project: Project, file: VirtualFile, entityId: Long, kindName: String, metaFiles: MetaFiles,
        initial: Map<String, String> = emptyMap(),
    ): EditResult = run(project, file, AbyssusBundle.message("commandAddComponent")) {
        editor(project, file).add(
            it,
            entityId,
            kindName,
            initial,
            assetNames(file, metaFiles),
            assetsByType(file, metaFiles)
        )
    }

    fun update(
        project: Project,
        file: VirtualFile,
        entityId: Long,
        kindName: String,
        field: String,
        text: String,
        metaFiles: MetaFiles,
    ): EditResult {
        // a path arrangement regenerates its copies on the ground, which is read before the command starts
        val heights = if (kindName == "PathArrangementComponent") {
            ArrangementHeights.of(project).forScene(file) ?: return EditResult.Unchanged
        } else null
        return updateWith(project, file, entityId, kindName, field, text, metaFiles, heights)
    }

    private fun updateWith(
        project: Project,
        file: VirtualFile,
        entityId: Long,
        kindName: String,
        field: String,
        text: String,
        metaFiles: MetaFiles,
        heights: GroundHeight?,
    ): EditResult =
        run(project, file, AbyssusBundle.message("commandEditComponent")) {
            editor(project, file).update(
                it,
                entityId,
                kindName,
                field,
                text,
                assetNames(file, metaFiles),
                assetsByType(file, metaFiles),
                heights
            )
        }

    fun remove(project: Project, file: VirtualFile, entityId: Long, kindName: String): EditResult =
        run(project, file, AbyssusBundle.message("commandRemoveComponent")) {
            editor(project, file).remove(
                it,
                entityId,
                kindName
            )
        }

    /**
     * Removes the one entity [entityId] as one undoable command; refused, with nothing written, when another entity
     * still refers to it, it owns arrangement copies, or the entity is gone from the latest document text.
     */
    fun removeEntity(project: Project, file: VirtualFile, entityId: Long): EditResult =
        // global: the tree that starts a removal has the focus, and Undo and Redo must reach it from there
        run(project, file, AbyssusBundle.message("commandRemoveEntity"), global = true) {
            EntityRemoval(EditorBundle, editor(project, file).kinds).remove(it, entityId)
        }
}
