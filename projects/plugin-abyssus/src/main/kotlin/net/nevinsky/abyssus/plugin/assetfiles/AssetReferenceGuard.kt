/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import net.nevinsky.abyssus.lib.core.editor.document.metaReferencesUuid
import net.nevinsky.abyssus.lib.core.editor.document.sceneNamedAssets
import net.nevinsky.abyssus.lib.core.editor.document.removeEntityNode
import net.nevinsky.abyssus.lib.core.util.textAt
import net.nevinsky.abyssus.lib.core.util.textValuesOf
import net.nevinsky.abyssus.lib.core.util.containsText
import net.nevinsky.abyssus.lib.core.util.rawAt
import com.intellij.openapi.fileEditor.FileDocumentManager
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.io.File

/** An entity [entityId] of the scene file [scene]. */
data class PlacedEntity(val scene: File, val entityId: String)

/**
 * Finds what depends on an asset, so Undo of its creation does not delete something in use: the project's scenes that
 * name its folder, and the other assets whose metadata holds its `uuid`. Saved files and unsaved editor text are both
 * read (unsaved text wins). UI thread, since it reads documents.
 */
class AssetReferenceGuard(private val projectDir: File) {
    /**
     * A sentence naming the first dependent of the asset in folder [name] with [uuid], or null when nothing uses it.
     * [ignoring] is an entity the same command placed (Import Model with Add to scene): the platform undoes the folder
     * before that scene edit, so its reference is not a dependent.
     */
    fun blocker(name: String, uuid: String, ignoring: PlacedEntity? = null): String? {
        val unsaved = unsavedTexts()
        fun text(file: File): String? = unsaved[file.absoluteFile] ?: runCatchingKeepingCancellation { file.readText() }.getOrNull()

        val scenes = File(projectDir, ProjectLayout.SCENES_DIR).listFiles { f -> f.isFile && f.extension == ProjectLayout.SCENE_EXTENSION }.orEmpty()
        val sceneFiles = (scenes.map { it.absoluteFile } + unsaved.keys.filter { it.extension == ProjectLayout.SCENE_EXTENSION && it.parentFile?.name == ProjectLayout.SCENES_DIR })
            .distinct().sortedBy { it.name }
        for (scene in sceneFiles) {
            val ignored = ignoring?.entityId?.takeIf { ignoring.scene.absoluteFile == scene }
            val names = text(scene)?.let { sceneNamedAssets(it, ignored) } ?: continue
            if (name in names) return "${scene.name} uses $name"
        }
        val assets = File(projectDir, ASSETS_DIR)
        val metas = assets.listFiles { f -> f.isDirectory && f.name != name }.orEmpty().map { File(it, META_FILE).absoluteFile }
        for (meta in (metas + unsaved.keys.filter { it.name == META_FILE && it.parentFile?.parentFile?.absoluteFile == assets.absoluteFile && it.parentFile.name != name }).distinct().sortedBy { it.path }) {
            val holds = text(meta)?.let { metaReferencesUuid(it, uuid) } ?: continue
            if (holds) return "${meta.parentFile.name} uses $name"
        }
        return null
    }

    private fun unsavedTexts(): Map<File, String> {
        val manager = FileDocumentManager.getInstance()
        val base = projectDir.absoluteFile.path + File.separator
        return manager.unsavedDocuments.mapNotNull { doc ->
            val file = manager.getFile(doc)?.let { File(it.path).absoluteFile } ?: return@mapNotNull null
            if (file.path.startsWith(base)) file to doc.text else null
        }.toMap()
    }
}
