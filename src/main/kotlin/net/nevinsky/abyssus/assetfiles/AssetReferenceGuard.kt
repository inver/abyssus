/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assetfiles

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.fileEditor.FileDocumentManager
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.editor.document.SceneJson
import java.io.File

/**
 * Finds what depends on an asset, so Undo of its creation does not delete something in use: the project's scenes that
 * name its folder, and the other assets whose metadata holds its `uuid`. Saved files and unsaved editor text are both
 * read (unsaved text wins). UI thread, since it reads documents.
 */
class AssetReferenceGuard(private val projectDir: File) {
    /** A sentence naming the first dependent of the asset in folder [name] with [uuid], or null when nothing uses it. */
    fun blocker(name: String, uuid: String): String? {
        val unsaved = unsavedTexts()
        fun text(file: File): String? = unsaved[file.absoluteFile] ?: runCatchingKeepingCancellation { file.readText() }.getOrNull()

        val scenes = File(projectDir, ProjectLayout.SCENES_DIR).listFiles { f -> f.isFile && f.extension == ProjectLayout.SCENE_EXTENSION }.orEmpty()
        val sceneFiles = (scenes.map { it.absoluteFile } + unsaved.keys.filter { it.extension == ProjectLayout.SCENE_EXTENSION && it.parentFile?.name == ProjectLayout.SCENES_DIR })
            .distinct().sortedBy { it.name }
        for (scene in sceneFiles) {
            val root = text(scene)?.let { runCatchingKeepingCancellation { SceneJson().parse(it).also { root -> net.nevinsky.abyssus.editor.document.AbyssusDocumentFormat().requireSupported(root, net.nevinsky.abyssus.editor.document.DocumentKind.SCENE) } }.getOrNull() } ?: continue
            if (names(root).contains(name)) return "${scene.name} uses $name"
        }
        val assets = File(projectDir, ASSETS_DIR)
        val metas = assets.listFiles { f -> f.isDirectory && f.name != name }.orEmpty().map { File(it, META_FILE).absoluteFile }
        for (meta in (metas + unsaved.keys.filter { it.name == META_FILE && it.parentFile?.parentFile?.absoluteFile == assets.absoluteFile && it.parentFile.name != name }).distinct().sortedBy { it.path }) {
            val root = text(meta)?.let { runCatchingKeepingCancellation { SceneJson().parse(it).also { root -> net.nevinsky.abyssus.editor.document.AbyssusDocumentFormat().requireSupported(root, net.nevinsky.abyssus.editor.document.DocumentKind.ASSET) } }.getOrNull() } ?: continue
            if (holds(root.get("additional"), uuid)) return "${meta.parentFile.name} uses $name"
        }
        return null
    }

    /** The asset and sky names a scene's JSON holds. */
    private fun names(root: JsonNode): Set<String> = buildSet {
        for (field in listOf("assetName", "shaderKey")) root.findValues(field).forEach { if (it.isTextual) add(it.asText()) }
        root.get("skyboxName")?.takeIf { it.isTextual }?.let { add(it.asText()) }
    }

    private fun holds(node: JsonNode?, uuid: String): Boolean = when {
        node == null -> false
        node.isTextual -> node.asText() == uuid
        node.isContainerNode -> node.any { holds(it, uuid) }
        else -> false
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
