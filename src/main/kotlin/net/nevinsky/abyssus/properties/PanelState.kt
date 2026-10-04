/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.ecs.scene.FieldValue
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.projectView.ComponentTarget
import net.nevinsky.abyssus.projectView.hdrSkyInfo
import net.nevinsky.abyssus.projectView.SceneComponentEdits
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.dto.textOf
import net.nevinsky.abyssus.projectView.describeNonAsset
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import net.nevinsky.abyssus.assets.META_FILE
import net.nevinsky.abyssus.assets.displayMessage
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsPaths
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.sky.cube.SKYBOX_FACES
import net.nevinsky.abyssus.projectView.HdrPreviewSource

/** What the panel shows. */
sealed interface PanelState {
    /** No asset to describe: [message], and under it [hint] when there is one. */
    data class Empty(val message: String, val hint: String?) : PanelState

    /** A scene row: the scene's runtime view settings, currently its Ray Tracing switch. Reads no file. */
    data class SceneDetails(val file: VirtualFile, val name: String) : PanelState

    /** An asset's Meta; [fields] are its editable properties (empty for a type without editors, which stays read only). */
    data class Details(
        val name: String,
        val meta: AssetMeta.Loaded,
        val faces: List<FaceCell>?,
        val hdr: HdrCell? = null,
        val fields: List<AssetFieldState> = emptyList(),
        /** For a terrain: what regeneration works from, or why it cannot. */
        val terrain: net.nevinsky.abyssus.terrain.TerrainSource? = null,
    ) : PanelState

    /**
     * An entity of [target]'s scene, or only its component when `target.kind` is set. [addable] names the modeled kinds the
     * entity lacks (offered when the whole entity is shown).
     */
    data class EntityDetails(
        val target: ComponentTarget,
        val name: String,
        val sections: List<ComponentSection>,
        val addable: List<String>,
    ) : PanelState
}

/** One component of an entity: its [fields] when the plugin edits it, else the file's JSON as [raw] text, read only. */
data class ComponentSection(val kind: String, val label: String, val fields: List<FieldValue>, val raw: String?)

/** One face of a skybox: [file] is what `meta.json` names (or `null`), [image] its thumbnail, null when it cannot be shown. */
data class FaceCell(val face: String, val file: String, val image: BufferedImage?)

/** An HDR sky's preview: the tone-mapped [image] and its [label] (file and size), or a null image and the [label] saying why. */
data class HdrCell(val label: String, val image: BufferedImage?)

private const val THUMBNAIL_WIDTH = 320
private const val THUMBNAIL_HEIGHT = 144

fun emptyState(node: Any?): PanelState.Empty {
    val hint = AbyssusBundle.message("propertiesHint")
    val (name, kind) = describeNonAsset(node) ?: return PanelState.Empty(AbyssusBundle.message("propertiesNothingSelected"), hint)
    return PanelState.Empty(AbyssusBundle.message("propertiesNothingToShow", name, kind), hint)
}

/** Reads the asset in [folder] for display: its Meta and, for a skybox, the face thumbnails or the HDR preview. Safe off the EDT. */
fun readAssetState(folder: VirtualFile, services: PanelServices): PanelState {
    val meta = runReadAction { loadAssetMeta(folder, services.metaFiles) }
    return when (meta) {
        is AssetMeta.Failed -> PanelState.Empty(meta.message, null)
        is AssetMeta.Loaded -> PanelState.Details(
            folder.name, meta,
            if (meta.type == MetaType.SKYBOX) faces(folder, meta) else null,
            if (meta.type == MetaType.SKYBOX_HDR) hdrCell(folder, meta, services.hdr) else null,
            readFieldStates(folder, meta.type, meta.json, services),
            if (meta.type == MetaType.TERRAIN) readTerrainNow(folder, meta, services) else null,
        )
    }
}

/** The preview of an HDR sky: its image decoded and tone mapped here, so off the EDT like the rest of the state. */
private fun hdrCell(folder: VirtualFile, meta: AssetMeta.Loaded, hdr: HdrPreviewSource): HdrCell {
    val info = hdrSkyInfo(folder, meta.json, hdr)
    val file = info.file ?: return HdrCell(AbyssusBundle.message("propertiesHdrNoFile"), null)
    val image = runCatchingKeepingCancellation {
        folder.findChild(file)?.inputStream?.buffered()?.use { hdr.preview.image(it, THUMBNAIL_WIDTH) } ?: error("missing")
    }
    return image.fold(
        { HdrCell(AbyssusBundle.message("propertiesHdrLabel", file, info.width.toString(), info.height.toString()), it) },
        { HdrCell(AbyssusBundle.message("propertiesHdrUnreadable", file, it.displayMessage()), null) },
    )
}

/**
 * Reads the entity (or component) of [target] from the scene's current text for display. Safe off the EDT. The state is
 * `Empty` with a message when the scene cannot be read or the entity or component is gone.
 */
fun readEntityState(target: ComponentTarget, services: PanelServices): PanelState {
    val root = runCatchingKeepingCancellation { SceneJson.parse(runReadAction { textOf(target.file) }) }
        .getOrElse { return PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.displayMessage()), null) }
    val entity = SceneEcsPaths().entities(root)?.get(target.entityId)?.takeIf { it.isObject }
        ?: return PanelState.Empty(AbyssusBundle.message("propertiesEntityGone", target.entityId), null)
    val components = entity.get("components")?.takeIf { it.isObject }
    val kinds = target.kind?.let { listOf(it) } ?: components?.fieldNames()?.asSequence()?.toList().orEmpty()
    val assets = SceneComponentEdits.renderAssets(target.file, services.metaFiles).map { it.name }
    val sections = kinds.map { kind ->
        val modeled = ComponentEditor.kindOf(kind)
        val fields = ComponentEditor.read(root, target.entityId, kind)
        when {
            components?.has(kind) != true ->
                return PanelState.Empty(AbyssusBundle.message("propertiesComponentGone", target.entityId, kind.removeSuffix("Component")), null)
            modeled == null || fields == null -> ComponentSection(kind, kind.removeSuffix("Component").ifEmpty { kind }, emptyList(), SceneJson.pretty(components!![kind]))
            else -> ComponentSection(kind, modeled.label, fields.map { if (it.kind == FieldKind.ASSET_NAME) it.copy(choices = (assets + it.value).filter(String::isNotEmpty).distinct()) else it }, null)
        }
    }
    val name = SceneEcsPaths().entityName(components, target.entityId)
    val addable = if (target.kind == null) ComponentEditor.missingKinds(root, target.entityId).map { it.name } else emptyList()
    return PanelState.EntityDetails(target, name, sections, addable)
}

private fun faces(folder: VirtualFile, meta: AssetMeta.Loaded): List<FaceCell> {
    val additional = meta.json.get("additional")
    return SKYBOX_FACES.map { face ->
        val file = additional?.get(face)?.takeIf { it.isTextual }?.asText()
        FaceCell(face, file ?: AbyssusBundle.message("dtoNullValue"), file?.let { thumbnail(folder, it) })
    }
}

/** The file [fileName] in [folder], or null when the folder is gone or holds no such file (a directory does not count). */
private fun imageFile(folder: VirtualFile, fileName: String): VirtualFile? =
    folder.takeIf { it.isValid }?.findChild(fileName)?.takeIf { it.isValid && !it.isDirectory }

private fun thumbnail(folder: VirtualFile, fileName: String, maxWidth: Int = THUMBNAIL_WIDTH, maxHeight: Int = THUMBNAIL_HEIGHT): BufferedImage? = runCatchingKeepingCancellation {
    val file = imageFile(folder, fileName) ?: return@runCatchingKeepingCancellation null
    val source = ImageIO.read(ByteArrayInputStream(file.contentsToByteArray())) ?: return@runCatchingKeepingCancellation null
    scaled(source, maxWidth, maxHeight)
}.getOrNull()

private fun scaled(source: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
    val ratio = minOf(maxWidth.toDouble() / source.width, maxHeight.toDouble() / source.height, 1.0)
    val w = maxOf(1, (source.width * ratio).toInt())
    val h = maxOf(1, (source.height * ratio).toInt())
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(source, 0, 0, w, h, null)
        dispose()
    }
    return out
}

/** A tone-mapped thumbnail at most [width] wide of the Radiance image [fileName] in [folder], or null when it is absent or unreadable. Off the EDT. */
fun hdrThumbnail(folder: VirtualFile, fileName: String, width: Int, preview: HdrPreview): BufferedImage? = runCatchingKeepingCancellation {
    val file = imageFile(folder, fileName) ?: return@runCatchingKeepingCancellation null
    file.inputStream.buffered().use { preview.image(it, width) }
}.getOrNull()

/** A small square-bounded thumbnail of the image [fileName] in [folder], or null when it is absent or cannot be decoded. Safe off the EDT. */
fun smallThumbnail(folder: VirtualFile, fileName: String, size: Int): BufferedImage? = thumbnail(folder, fileName, size, size)

private fun readTerrainNow(folder: VirtualFile, meta: AssetMeta.Loaded, services: PanelServices): net.nevinsky.abyssus.terrain.TerrainSource {
    val text = folder.findChild(META_FILE)?.let { runReadAction { textOf(it) } } ?: ""
    return net.nevinsky.abyssus.terrain.readTerrainSource(java.io.File(folder.path), text, meta.json, AssetReferenceChoices(services.json), services.terrainRecipes)
}

/** The terrain of [folder] as it is now, for the checks Apply makes just before it writes. UI thread. */
fun readTerrainSourceNow(folder: VirtualFile, services: PanelServices): net.nevinsky.abyssus.terrain.TerrainSource {
    val meta = loadAssetMeta(folder, services.metaFiles) as? AssetMeta.Loaded ?: return net.nevinsky.abyssus.terrain.TerrainSource.Unusable("meta.json")
    return readTerrainNow(folder, meta, services)
}
