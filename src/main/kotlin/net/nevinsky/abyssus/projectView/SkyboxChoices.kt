/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.projectView

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.AssetReadResult
import net.nevinsky.abyssus.dto.ProjectDto
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.sceneReferences
import net.nevinsky.abyssus.dto.obj
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.text
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.SceneDto

/** The `meta.json` type of an asset a scene's `skyboxName` can name. */
const val SKYBOX_TYPE = "SKYBOX"

/** The `additional` keys of a skybox's `meta.json` that name its face images. */
private val FACE_KEYS = listOf("top", "bottom", "left", "right", "front", "back")

/** The faces as the chooser's thumbnail strip shows them. */
private val THUMB_ORDER = listOf("left", "right", "top", "bottom", "front", "back")

/**
 * A skybox asset as the skybox chooser lists it: its folder [name], how many face images its `meta.json` names and their
 * file extensions, how many of the project's scenes reference it and whether the Abyssus view marks it unused.
 */
data class SkyboxChoice(
    val name: String,
    val faces: Int,
    val formats: List<String>,
    val sceneCount: Int,
    val unused: Boolean,
) {
    /** The asset folder; null for a choice that was not read from disk. */
    var folder: VirtualFile? = null

    /** The face file names in thumbnail order (left, right, top, bottom, front, back); null where `meta.json` names none. */
    var faceFiles: List<String?> = emptyList()

    /** Face thumbnails by [faceFiles], filled in after the dialog opened; null where a face cannot be shown. */
    @Volatile
    var thumbs: List<java.awt.image.BufferedImage?> = emptyList()

    /** `6 faces · png`; just the count when no face names an extension. */
    val detail: String
        get() = if (formats.isEmpty()) AbyssusBundle.message("skyboxFaces", faces)
        else AbyssusBundle.message("skyboxFacesFormats", faces, formats.joinToString(", "))
}

/** The project's `SKYBOX` assets by folder name; [metas] holds each folder's parsed `meta.json` (absent or null when unreadable). */
fun skyboxChoices(project: ProjectDto, metas: Map<String, JsonNode?>): List<SkyboxChoice> {
    val references = project.scenes.filterIsInstance<SceneDto>().map(::sceneReferences)
    return project.assets.filter { it.meta.type.name == SKYBOX_TYPE }.sortedBy { it.name }.map { asset ->
        val additional = metas[asset.name]?.obj("additional")
        val files = FACE_KEYS.mapNotNull { key -> additional?.text(key)?.takeIf { it.isNotBlank() } }
        val formats = files.mapNotNull { f -> f.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() } }.distinct().sorted()
        SkyboxChoice(asset.name, files.size, formats, references.count { asset.name in it }, asset.unused).also { choice ->
            choice.faceFiles = THUMB_ORDER.map { key -> additional?.text(key)?.takeIf { it.isNotBlank() } }
        }
    }
}

/** The skybox choices of the `.abss` project [abss], read as the Abyssus view reads it; null when the project cannot be read. */
fun loadSkyboxChoices(project: Project, abss: VirtualFile): List<SkyboxChoice>? {
    val dto = AssetReadCache.of(project).read(abss)?.obj as? ProjectDto ?: return null
    val skyboxes = dto.assets.filter { it.meta.type.name == SKYBOX_TYPE }.map { it.name }.toSet()
    val metas = ProjectLayout.assetFolders(abss).filter { it.name in skyboxes }.associate { dir ->
        dir.name to runCatchingKeepingCancellation {
            dir.findChild(ProjectLayout.META_FILE)?.let { SceneJson.parseObject(it.text()) }
        }.getOrNull()
    }
    val folders = ProjectLayout.assetFolders(abss).associateBy { it.name }
    return skyboxChoices(dto, metas).onEach { it.folder = folders[it.name] }
}

/** The `.abss` project of a scene's own `skyboxName` row, which is what gets the chooser; null for any other row. */
fun skyboxProjectOf(entry: DtoEntry): VirtualFile? =
    entry.takeIf { it.name == SKYBOX_KEY && it.parentKeys.isEmpty() }?.source?.let(ProjectLayout::abssFor)

/** Opens the skybox chooser for [entry] and writes the assigned skybox; true when the scene file changed. */
fun chooseSkybox(project: Project, entry: DtoEntry, abss: VirtualFile): Boolean {
    val file = entry.source ?: return false
    val choices = loadSkyboxChoices(project, abss) ?: return false
    val dialog = SkyboxChooserDialog(project, choices, scalarOf(entry.value) as? String)
    return dialog.showAndGet() && setSkybox(project, file, dialog.chosen)
}
