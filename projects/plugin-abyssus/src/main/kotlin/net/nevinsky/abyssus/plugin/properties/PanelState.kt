/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.document.*
import net.nevinsky.abyssus.lib.gdx.editor.meta.*
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.projectView.*
import net.nevinsky.abyssus.plugin.terrain.TerrainSource
import net.nevinsky.abyssus.plugin.terrain.readTerrainSource
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import net.nevinsky.abyssus.plugin.ui.thumbnail
import java.awt.image.BufferedImage
import java.io.File

/** What the panel shows. */
sealed interface PanelState {
    /** No asset to describe: [message], and under it [hint] when there is one. */
    data class Empty(val message: String, val hint: String?) : PanelState

    data class UISceneState(
        val file: VirtualFile,
        val name: String,
        val rayRoot: JsonNode = SceneJson().parse("{}"),
        val raySettings: SceneRaySettingsState = SceneRaySettingsCodec().read(rayRoot),
    ) : PanelState

    /** An asset's Meta; [fields] are its editable properties (empty for a type without editors, which stays read only). */
    data class Details(
        val name: String,
        val meta: AssetMeta.Loaded,
        val faces: List<FaceCell>?,
        val hdr: HdrCell? = null,
        val fields: List<AssetFieldState> = emptyList(),
        /** For a terrain: what regeneration works from, or why it cannot. */
        val terrain: TerrainSource? = null,
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
        /** The model's Ray Tracing optical overrides, shown with its Render component; null for anything but a model. */
        val optics: RenderOptics? = null,
    ) : PanelState
}

/** One face of a skybox: [file] is what `meta.json` names (or `null`), [image] its thumbnail, null when it cannot be shown. */
data class FaceCell(val face: String, val file: String, val image: BufferedImage?)

/** An HDR sky's preview: the tone-mapped [image] and its [label] (file and size), or a null image and the [label] saying why. */
data class HdrCell(val label: String, val image: BufferedImage?)

private const val THUMBNAIL_WIDTH = 320
private const val THUMBNAIL_HEIGHT = 144

fun emptyState(node: Any?): PanelState.Empty {
    val hint = AbyssusBundle.message("propertiesHint")
    val (name, kind) = describeNonAsset(node)
        ?: return PanelState.Empty(AbyssusBundle.message("propertiesNothingSelected"), hint)
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
        hdr.preview.image(File(folder.path, file).also { check(it.isFile) { "missing" } }, THUMBNAIL_WIDTH)
    }
    return image.fold(
        {
            HdrCell(
                AbyssusBundle.message("propertiesHdrLabel", file, info.width.toString(), info.height.toString()),
                it
            )
        },
        { HdrCell(AbyssusBundle.message("propertiesHdrUnreadable", file, it.documentDisplayMessage()), null) },
    )
}

/**
 * Reads the entity (or component) of [target] from the scene's current text for display. Safe off the EDT. The state is
 * `Empty` with a message when the scene cannot be read or the entity or component is gone.
 */
fun readEntityState(target: ComponentTarget, services: PanelServices): PanelState {
    val root = runCatchingKeepingCancellation {
        SceneJson().parse(runReadAction { textOf(target.file) }).also {
            AbyssusDocumentFormat().requireSupported(it, DocumentKind.SCENE)
        }
    }.getOrElse { return PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.documentDisplayMessage()), null) }
    val assets = EntityAssetChoices(
        SceneComponentEdits.renderAssets(target.file, services.metaFiles).map { it.name },
        SceneComponentEdits.assetsByType(target.file, services.metaFiles).orEmpty(),
    )
    return when (val read = readEntitySections(root, target.entityId, target.kind, services.schemas.editorFor(target.file), assets,
        EditorBundle
    )) {
        is EntitySections.Gone -> PanelState.Empty(read.message, null)
        is EntitySections.Read -> PanelState.EntityDetails(target, read.name, read.sections, read.addable, read.render?.let { render ->
            readRenderOptics(render, { assetName ->
                ProjectLayout.projectDirFor(target.file)?.let { services.rayMaterials(it, assetName) }
            }, EditorBundle)
        })
    }
}

private fun faces(folder: VirtualFile, meta: AssetMeta.Loaded): List<FaceCell> {
    val additional = meta.json.get("additional")
    return SKYBOX_FACES.map { face ->
        val file = additional?.get(face)?.takeIf { it.isTextual }?.asText()
        FaceCell(face, file ?: EditorBundle.message("dtoNullValue"), file?.let { thumbnail(folder, it) })
    }
}

private fun readTerrainNow(
    folder: VirtualFile,
    meta: AssetMeta.Loaded,
    services: PanelServices
): TerrainSource {
    val text = folder.findChild(META_FILE)?.let { runReadAction { textOf(it) } } ?: ""
    return readTerrainSource(
        File(folder.path),
        text,
        meta.json,
        AssetReferenceChoices(services.json),
        services.terrainRecipes
    )
}

/** The terrain of [folder] as it is now, for the checks Apply makes just before it writes. UI thread. */
fun readTerrainSourceNow(folder: VirtualFile, services: PanelServices): TerrainSource {
    val meta = loadAssetMeta(folder, services.metaFiles) as? AssetMeta.Loaded
        ?: return TerrainSource.Unusable("meta.json")
    return readTerrainNow(folder, meta, services)
}

/** Current native scene preferences, read off the EDT independently of renderer availability. */
fun readSceneState(file: VirtualFile, name: String): PanelState = runCatchingKeepingCancellation {
    val root = SceneJson().parse(runReadAction { textOf(file) })
    AbyssusDocumentFormat().requireSupported(root, DocumentKind.SCENE)
    PanelState.UISceneState(file, name, root)
}.getOrElse { PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.documentDisplayMessage()), null) }
