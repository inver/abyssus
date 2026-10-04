/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.AssetReadResult
import net.nevinsky.abyssus.dto.ProjectDto
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.sceneReferences
import net.nevinsky.abyssus.assets.json.obj
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.text
import net.nevinsky.abyssus.assets.json.text
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.SceneDto
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assets.sky.hdr.HdrSkyFiles
import net.nevinsky.abyssus.assets.sky.hdr.RadianceDecoder
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.sky.cube.SKYBOX_FACES
import net.nevinsky.abyssus.dto.MetaFiles
import net.nevinsky.abyssus.assets.sky.hdr.HdrPreview

/** The `meta.json` types of the assets a scene's `skyboxName` can name. */
private val SKY_TYPES = setOf(MetaType.SKYBOX, MetaType.SKYBOX_PROCEDURAL, MetaType.SKYBOX_HDR)

/** The Radiance sky pieces the chooser and the Properties panel need: choosing a file, reading its header, a thumbnail. */
interface HdrPreviewSource {
    val files: HdrSkyFiles
    val decoder: RadianceDecoder
    val preview: HdrPreview
}

/** What the chooser shows of an HDR sky: its image [file] (null when the folder has none) and size (0 when unreadable). */
data class HdrSkyInfo(val file: String?, val width: Int = 0, val height: Int = 0)

/** The `additional` keys of a skybox's `meta.json` that name its face images. */

/** The faces as the chooser's thumbnail strip shows them. */
private val THUMB_ORDER = listOf("left", "right", "top", "bottom", "front", "back")

/**
 * A skybox asset as the skybox chooser lists it: its folder [name], how many face images its `meta.json` names and their
 * file extensions, how many of the project's scenes reference it and whether the Abyssus view marks it unused.
 */
class SkyboxChoice(
    val name: String,
    val faces: Int,
    val formats: List<String>,
    val sceneCount: Int,
    val unused: Boolean,
    val procedural: Boolean = false,
    val hdr: HdrSkyInfo? = null,
) {
    /** The asset folder; null for a choice that was not read from disk. */
    var folder: VirtualFile? = null

    /** The face file names in thumbnail order (left, right, top, bottom, front, back); null where `meta.json` names none. */
    var faceFiles: List<String?> = emptyList()

    /** Face thumbnails by [faceFiles], filled in after the dialog opened; null where a face cannot be shown. */
    @Volatile
    var thumbs: List<java.awt.image.BufferedImage?> = emptyList()

    /** The asset's `meta.json` type, which picks the row's icon. */
    val type: MetaType get() = if (hdr != null) MetaType.SKYBOX_HDR else if (procedural) MetaType.SKYBOX_PROCEDURAL else MetaType.SKYBOX

    /** `6 faces · png`; just the count when no face names an extension; `HDR · 64 × 32` or `HDR` for an HDR sky. */
    val detail: String
        get() = if (hdr != null) {
            if (hdr.width > 0) AbyssusBundle.message("skyboxHdrSize", hdr.width.toString(), hdr.height.toString())
            else AbyssusBundle.message("skyboxHdr")
        } else if (procedural) AbyssusBundle.message("skyboxProcedural")
        else if (formats.isEmpty()) AbyssusBundle.message("skyboxFaces", faces)
        else AbyssusBundle.message("skyboxFacesFormats", faces, formats.joinToString(", "))
}

/**
 * The project's `SKYBOX`, `SKYBOX_PROCEDURAL` and `SKYBOX_HDR` assets by folder name; [metas] holds each folder's parsed
 * `meta.json` (absent or null when unreadable) and [hdr] what was read of each HDR sky's image (absent: nothing).
 */
@JvmOverloads
fun skyboxChoices(project: ProjectDto, metas: Map<String, JsonNode?>, hdr: Map<String, HdrSkyInfo> = emptyMap()): List<SkyboxChoice> {
    val references = project.scenes.filterIsInstance<SceneDto>().map(::sceneReferences)
    return project.assets.filter { it.meta.type in SKY_TYPES }.sortedBy { it.name }.map { asset ->
        val additional = metas[asset.name]?.obj("additional")
        val files = SKYBOX_FACES.mapNotNull { key -> additional?.text(key)?.takeIf { it.isNotBlank() } }
        val formats = files.mapNotNull { f -> f.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() } }.distinct().sorted()
        val procedural = asset.meta.type == MetaType.SKYBOX_PROCEDURAL
        val hdrInfo = if (asset.meta.type == MetaType.SKYBOX_HDR) hdr[asset.name] ?: HdrSkyInfo(null) else null
        SkyboxChoice(asset.name, files.size, formats, references.count { asset.name in it }, asset.unused, procedural, hdrInfo).also { choice ->
            choice.faceFiles = if (hdrInfo != null) listOfNotNull(hdrInfo.file)
            else THUMB_ORDER.map { key -> additional?.text(key)?.takeIf { it.isNotBlank() } }
        }
    }
}

/** The skybox choices of the `.abss` project [abss], read as the Abyssus view reads it; null when the project cannot be read. */
fun loadSkyboxChoices(project: Project, abss: VirtualFile, metaFiles: MetaFiles, hdrSource: HdrPreviewSource): List<SkyboxChoice>? {
    val dto = AssetReadCache.of(project).read(abss)?.obj as? ProjectDto ?: return null
    val skyboxes = dto.assets.filter { it.meta.type in SKY_TYPES }.map { it.name }.toSet()
    val metas = ProjectLayout.assetFolders(abss).filter { it.name in skyboxes }.associate { dir ->
        dir.name to runCatchingKeepingCancellation {
            metaFiles.saved(dir)?.json
        }.getOrNull()
    }
    val folders = ProjectLayout.assetFolders(abss).associateBy { it.name }
    val hdr = dto.assets.filter { it.meta.type == MetaType.SKYBOX_HDR }.mapNotNull { asset ->
        folders[asset.name]?.let { asset.name to hdrSkyInfo(it, metas[asset.name], hdrSource) }
    }.toMap()
    return skyboxChoices(dto, metas, hdr).onEach { it.folder = folders[it.name] }
}

/** The image an HDR sky folder uses and the size its header declares; size 0 when the header cannot be read. */
fun hdrSkyInfo(folder: VirtualFile, meta: JsonNode?, source: HdrPreviewSource): HdrSkyInfo {
    val named = meta?.obj("additional")?.properties()?.mapNotNull { it.value.takeIf(JsonNode::isTextual)?.asText() }.orEmpty()
    val file = source.files.choose(folder.children.filter { !it.isDirectory }.map { it.name }, named)?.file ?: return HdrSkyInfo(null)
    val header = runCatchingKeepingCancellation { folder.findChild(file)?.inputStream?.buffered()?.use(source.decoder::header) }.getOrNull()
    return HdrSkyInfo(file, header?.width ?: 0, header?.height ?: 0)
}

/** The `.abss` project of a scene's own `skyboxName` row, which is what gets the chooser; null for any other row. */
fun skyboxProjectOf(entry: DtoEntry): VirtualFile? =
    entry.takeIf { it.name == SKYBOX_KEY && it.parentKeys.isEmpty() }?.source?.let(ProjectLayout::abssFor)

/** Opens the skybox chooser for [entry] and writes the assigned skybox; true when the scene file changed. */
fun chooseSkybox(project: Project, entry: DtoEntry, abss: VirtualFile, metaFiles: MetaFiles, hdrSource: HdrPreviewSource): Boolean {
    val file = entry.source ?: return false
    // reads meta.json files and HDR headers, which the EDT must not do
    val choices = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<SkyboxChoice>?, RuntimeException>(
        { runReadAction { loadSkyboxChoices(project, abss, metaFiles, hdrSource) } },
        AbyssusBundle.message("skyboxChooserLoading"), true, project,
    ) ?: return false
    val dialog = SkyboxChooserDialog(project, choices, scalarOf(entry.value) as? String, hdrSource.preview)
    return dialog.showAndGet() && setSkybox(project, file, dialog.chosen)
}
