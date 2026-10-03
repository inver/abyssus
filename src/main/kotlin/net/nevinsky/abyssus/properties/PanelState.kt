/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.ecs.scene.FieldValue
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.projectView.ComponentTarget
import net.nevinsky.abyssus.projectView.HDR_SKY_TYPE
import net.nevinsky.abyssus.projectView.hdrSkyInfo
import net.nevinsky.abyssus.projectView.SceneComponentEdits
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.sceneview.textOf
import net.nevinsky.abyssus.projectView.describeNonAsset
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** What the panel shows. */
sealed interface PanelState {
    /** No asset to describe: [message], and under it [hint] when there is one. */
    data class Empty(val message: String, val hint: String?) : PanelState

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

val SKYBOX_FACES = listOf("top", "bottom", "left", "right", "front", "back")

private const val SKYBOX = "SKYBOX"
private const val THUMBNAIL_WIDTH = 320
private const val THUMBNAIL_HEIGHT = 144

fun emptyState(node: Any?): PanelState.Empty {
    val hint = AbyssusBundle.message("propertiesHint")
    val (name, kind) = describeNonAsset(node) ?: return PanelState.Empty(AbyssusBundle.message("propertiesNothingSelected"), hint)
    return PanelState.Empty(AbyssusBundle.message("propertiesNothingToShow", name, kind), hint)
}

/** Reads the asset in [folder] for display: its Meta and, for a skybox, the face thumbnails or the HDR preview. Safe off the EDT. */
fun readAssetState(folder: VirtualFile): PanelState {
    val meta = runReadAction { loadAssetMeta(folder) }
    return when (meta) {
        is AssetMeta.Failed -> PanelState.Empty(meta.message, null)
        is AssetMeta.Loaded -> PanelState.Details(
            folder.name, meta,
            if (meta.type == SKYBOX) faces(folder, meta) else null,
            if (meta.type == HDR_SKY_TYPE) hdrCell(folder, meta) else null,
            readFieldStates(folder, meta.type, meta.json),
            if (meta.type == "TERRAIN") readTerrainNow(folder, meta) else null,
        )
    }
}

/** The preview of an HDR sky: its image decoded and tone mapped here, so off the EDT like the rest of the state. */
private fun hdrCell(folder: VirtualFile, meta: AssetMeta.Loaded): HdrCell {
    val loading = service<AbyssusCore>().loading
    val info = hdrSkyInfo(folder, meta.json, loading.hdrFiles, loading.decoder)
    val file = info.file ?: return HdrCell(AbyssusBundle.message("propertiesHdrNoFile"), null)
    val image = runCatchingKeepingCancellation {
        folder.findChild(file)?.inputStream?.buffered()?.use { loading.hdrPreview.image(it, THUMBNAIL_WIDTH) } ?: error("missing")
    }
    return image.fold(
        { HdrCell(AbyssusBundle.message("propertiesHdrLabel", file, info.width.toString(), info.height.toString()), it) },
        { HdrCell(AbyssusBundle.message("propertiesHdrUnreadable", file, it.message ?: it.javaClass.simpleName), null) },
    )
}

/**
 * Reads the entity (or component) of [target] from the scene's current text for display. Safe off the EDT. The state is
 * `Empty` with a message when the scene cannot be read or the entity or component is gone.
 */
fun readEntityState(target: ComponentTarget): PanelState {
    val root = runCatchingKeepingCancellation { SceneJson.parse(runReadAction { textOf(target.file) }) }
        .getOrElse { return PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.message ?: it.javaClass.simpleName), null) }
    val entity = root.get("ecs")?.get("entities")?.get(target.entityId)?.takeIf { it.isObject }
        ?: return PanelState.Empty(AbyssusBundle.message("propertiesEntityGone", target.entityId), null)
    val components = entity.get("components")?.takeIf { it.isObject }
    val kinds = target.kind?.let { listOf(it) } ?: components?.fieldNames()?.asSequence()?.toList().orEmpty()
    val assets = SceneComponentEdits.renderAssets(target.file).map { it.name }
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
    val name = components?.get("NameComponent")?.get("name")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() } ?: target.entityId
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

private fun thumbnail(folder: VirtualFile, fileName: String, maxWidth: Int = THUMBNAIL_WIDTH, maxHeight: Int = THUMBNAIL_HEIGHT): BufferedImage? = runCatchingKeepingCancellation {
    val file = folder.takeIf { it.isValid }?.findChild(fileName)?.takeIf { it.isValid && !it.isDirectory } ?: return@runCatchingKeepingCancellation null
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
    val file = folder.takeIf { it.isValid }?.findChild(fileName)?.takeIf { it.isValid && !it.isDirectory } ?: return@runCatchingKeepingCancellation null
    file.inputStream.buffered().use { preview.image(it, width) }
}.getOrNull()

/** A small square-bounded thumbnail of the image [fileName] in [folder], or null when it is absent or cannot be decoded. Safe off the EDT. */
fun smallThumbnail(folder: VirtualFile, fileName: String, size: Int): BufferedImage? = thumbnail(folder, fileName, size, size)

private fun readTerrainNow(folder: VirtualFile, meta: AssetMeta.Loaded): net.nevinsky.abyssus.terrain.TerrainSource {
    val core = service<AbyssusCore>()
    val text = folder.findChild(net.nevinsky.abyssus.dto.ProjectLayout.META_FILE)?.let { runReadAction { textOf(it) } } ?: ""
    return net.nevinsky.abyssus.terrain.readTerrainSource(java.io.File(folder.path), text, meta.json, AssetReferenceChoices(core.json), core.terrainRecipes)
}

/** The terrain of [folder] as it is now, for the checks Apply makes just before it writes. UI thread. */
fun readTerrainSourceNow(folder: VirtualFile): net.nevinsky.abyssus.terrain.TerrainSource {
    val meta = loadAssetMeta(folder) as? AssetMeta.Loaded ?: return net.nevinsky.abyssus.terrain.TerrainSource.Unusable("meta.json")
    return readTerrainNow(folder, meta)
}
