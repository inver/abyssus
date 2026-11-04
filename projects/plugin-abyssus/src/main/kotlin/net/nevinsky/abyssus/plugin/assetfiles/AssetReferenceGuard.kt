/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.fileEditor.FileDocumentManager
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.util.containsText
import net.nevinsky.abyssus.lib.core.util.rawAt
import net.nevinsky.abyssus.lib.core.util.textAt
import net.nevinsky.abyssus.lib.core.util.textValuesOf
import net.nevinsky.abyssus.plugin.dto.ProjectLayout

/** An entity [entityId] of the scene file [scene]. */
data class PlacedEntity(val scene: File, val entityId: Long)

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
        fun text(file: File): String? =
            unsaved[file.absoluteFile] ?: runCatchingKeepingCancellation { file.readText() }.getOrNull()

        val scenes =
            File(projectDir, ProjectLayout.SCENES_DIR)
                .listFiles { f -> f.isFile && f.extension == ProjectLayout.SCENE_EXTENSION }
                .orEmpty()
        val sceneFiles =
            (scenes.map { it.absoluteFile } +
                    unsaved.keys.filter {
                        it.extension == ProjectLayout.SCENE_EXTENSION && it.parentFile?.name == ProjectLayout.SCENES_DIR
                    })
                .distinct()
                .sortedBy { it.name }
        for (scene in sceneFiles) {
            val ignored = ignoring?.entityId?.takeIf { ignoring.scene.absoluteFile == scene }
            val names = text(scene)?.let { sceneNamedAssets(it, ignored) } ?: continue
            if (name in names) {
                return "${scene.name} uses $name"
            }
        }
        val assets = File(projectDir, ASSETS_DIR)
        val metas =
            assets.listFiles { f -> f.isDirectory && f.name != name }.orEmpty().map { File(it, META_FILE).absoluteFile }
        for (meta in
            (metas +
                    unsaved.keys.filter {
                        it.name == META_FILE &&
                            it.parentFile?.parentFile?.absoluteFile == assets.absoluteFile &&
                            it.parentFile.name != name
                    })
                .distinct()
                .sortedBy { it.path }) {
            val holds = text(meta)?.let { metaReferencesUuid(it, uuid) } ?: continue
            if (holds) {
                return "${meta.parentFile.name} uses $name"
            }
        }
        return null
    }

    private fun unsavedTexts(): Map<File, String> {
        val manager = FileDocumentManager.getInstance()
        val base = projectDir.absoluteFile.path + File.separator
        return manager.unsavedDocuments
            .mapNotNull { doc ->
                val file = manager.getFile(doc)?.let { File(it.path).absoluteFile } ?: return@mapNotNull null
                if (file.path.startsWith(base)) {
                    file to doc.text
                } else {
                    null
                }
            }
            .toMap()
    }
}

private val documents
    get() = com.intellij.openapi.components.service<net.nevinsky.abyssus.plugin.AbyssusPluginCtx>().documents

/**
 * The asset folders the scene [text] names directly, leaving out the entity [ignored]; null when it is not a readable
 * scene.
 */
internal fun sceneNamedAssets(text: String, ignored: Long?): Set<String>? = runCatchingKeepingCancellation {
    val scene = documents.parsing.parse(text)
    if (ignored != null) {
        scene.ecs.remove(ignored)
    }
    net.nevinsky.abyssus.plugin.dto.sceneReferences(
        scene,
        net.nevinsky.abyssus.lib.core.editor.document.RawComponentValues(documents.json),
    )
}
    .getOrNull()

/**
 * Whether the `meta.json` [text] holds [uuid] in a field that names another asset; null when it is not a readable meta.
 */
internal fun metaReferencesUuid(text: String, uuid: String): Boolean? = runCatchingKeepingCancellation {
    uuid in net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader(documents.json).read(text).referenceUuids()
}
    .getOrNull()
