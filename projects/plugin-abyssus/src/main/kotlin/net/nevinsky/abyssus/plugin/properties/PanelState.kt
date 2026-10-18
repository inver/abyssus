/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.lib.core.editor.panel.FolderKind as PanelFolderKind
import net.nevinsky.abyssus.lib.core.editor.panel.PanelBuilders
import net.nevinsky.abyssus.lib.core.editor.panel.PanelModel
import net.nevinsky.abyssus.plugin.projectView.isGameComponent
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.meta.ComponentSection
import net.nevinsky.abyssus.lib.core.editor.meta.EntityAssetChoices
import net.nevinsky.abyssus.lib.core.editor.meta.EntitySections
import net.nevinsky.abyssus.lib.core.editor.meta.RenderOptics
import net.nevinsky.abyssus.lib.core.editor.meta.readEntitySections
import net.nevinsky.abyssus.lib.core.editor.meta.readRenderOptics
import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.meta.*
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.ProjectSettings
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.foliage.FoliageSource
import net.nevinsky.abyssus.plugin.foliage.readFoliageSource
import net.nevinsky.abyssus.plugin.projectView.*
import net.nevinsky.abyssus.lib.core.editor.water.WaterCodec
import net.nevinsky.abyssus.lib.core.editor.water.WaterSurface
import net.nevinsky.abyssus.lib.core.editor.water.WaterText
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import net.nevinsky.abyssus.plugin.terrain.TerrainSource
import net.nevinsky.abyssus.plugin.terrain.readTerrainSource
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import net.nevinsky.abyssus.plugin.ui.thumbnail
import java.awt.image.BufferedImage
import java.io.File
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits

/** What the panel shows. */
sealed interface PanelState {
    /** No asset to describe: [message], and under it [hint] when there is one. */
    data class Empty(val message: String, val hint: String?) : PanelState

    data class Project(
        val file: VirtualFile,
        val name: String,
        val settings: ProjectSettings,
        /** The project file as parsed, for the sections; empty when it cannot be read. */
        val root: JsonNode = SceneJson().parse("{}"),
    ) : PanelState

    data class UISceneState(
        val file: VirtualFile,
        val name: String,
        val rayRoot: JsonNode = SceneJson().parse("{}"),
        val raySettings: SceneRaySettingsState = SceneRaySettingsCodec().read(rayRoot),
    ) : PanelState

    /**
     * A water surface of [target]'s scene. [texts] is what each value reads in the file (a valid surface shows its
     * numbers, an invalid one the values it can be fixed from); [surface] says whether the surface is drawn.
     */
    data class WaterDetails(
        val target: WaterTarget,
        val surface: WaterSurface,
        val texts: Map<WaterField, String>,
        val enabled: Boolean?,
        val preset: WaterPreset?,
    ) : PanelState

    /**
     * A folder, the `ecs` row, a scene setting or a UI screen: a layout with no editor of its own beyond the setting's
     * `Enabled` box and skybox. [root] is the scene as read, for the skybox row.
     */
    data class StructuralDetails(
        val selection: Structural,
        val model: PanelModel,
        val root: JsonNode? = null,
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
        /** For a foliage asset: what its settings, masks and bake read as, or why it cannot be edited. */
        val foliage: FoliageSource? = null,
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
        /** What the panel draws: the header and the sections of the entity or component. */
        val model: PanelModel? = null,
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
            if (meta.type == MetaType.FOLIAGE) readFoliageNow(folder, meta, services) else null,
        )
    }
}

/** The preview of an HDR sky: its image decoded and tone mapped here, so off the EDT like the rest of the state. */
private fun hdrCell(folder: VirtualFile, meta: AssetMeta.Loaded, hdr: HdrPreviewService): HdrCell {
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
    return when (val read = readEntitySections(
        root, target.entityId, target.kind, services.schemas.editorFor(target.file), assets,
        EditorBundle
    )) {
        is EntitySections.Gone -> PanelState.Empty(read.message, null)
        is EntitySections.Read -> {
            val builders = PanelBuilders(EditorBundle, ::isGameComponent)
            val projectName = ProjectLayout.abssFor(target.file)?.name.orEmpty()
            val model = if (target.kind == null) builders.entity(root, target.entityId, read, projectName)
            else builders.component(root, target.entityId, read, projectName)
            PanelState.EntityDetails(target, read.name, read.sections, read.addable, read.render?.let { render ->
                readRenderOptics(render, { assetName ->
                    ProjectLayout.projectDirFor(target.file)?.let { services.rayMaterials(it, assetName) }
                }, EditorBundle)
            }, model)
        }
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

/**
 * Reads the foliage asset in [folder] for display: its settings and problems, the terrain it stands on, the layer
 * masks and the state of the bake. Safe off the EDT, like the rest of the state it is read for.
 */
private fun readFoliageNow(folder: VirtualFile, meta: AssetMeta.Loaded, services: PanelServices): FoliageSource {
    val text = folder.findChild(META_FILE)?.let { runReadAction { textOf(it) } } ?: ""
    return readFoliageSource(
        folder.name,
        File(folder.path).parentFile.parentFile,
        services.json,
        text,
        meta.json.get("additional"),
    )
}

/** The foliage asset in [folder] as it is now, for the checks Apply makes just before it writes. UI thread. */
fun readFoliageSourceNow(folder: VirtualFile, services: PanelServices): FoliageSource {
    val meta = loadAssetMeta(folder, services.metaFiles)
    if (meta !is AssetMeta.Loaded) {
        return FoliageSource.Unusable((meta as? AssetMeta.Failed)?.message ?: AbyssusBundle.message("assetFieldUnreadable"))
    }
    return readFoliageNow(folder, meta, services)
}

/** Current native scene preferences, read off the EDT independently of renderer availability. */
fun readSceneState(file: VirtualFile, name: String): PanelState = runCatchingKeepingCancellation {
    val root = SceneJson().parse(runReadAction { textOf(file) })
    AbyssusDocumentFormat().requireSupported(root, DocumentKind.SCENE)
    PanelState.UISceneState(file, name, root)
}.getOrElse { PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.documentDisplayMessage()), null) }

/** Project settings use the same admitted, stamp-aware document cache as scene availability. */
fun readProjectState(file: VirtualFile, name: String, settings: AbyssusProjectSettings): PanelState.Project {
    val root = runCatchingKeepingCancellation { SceneJson().parse(runReadAction { textOf(file) }) }
        .getOrElse { SceneJson().parse("{}") }
    return PanelState.Project(file, name, settings.getSettings(file), root)
}

/**
 * Reads the water surface of [target] from its scene's current text for display. Safe off the EDT. The state is
 * `Empty` with a message when the scene cannot be read or the surface is gone.
 */
fun readWaterState(target: WaterTarget): PanelState {
    val root = runCatchingKeepingCancellation {
        SceneJson().parse(runReadAction { textOf(target.file) }).also {
            AbyssusDocumentFormat().requireSupported(it, DocumentKind.SCENE)
        }
    }.getOrElse { return PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.documentDisplayMessage()), null) }
    val extension = root.get("abyssus")
    val node = extension?.get("waterSurfaces")?.get(target.id)
    val surface = node?.let { WaterCodec().surface(target.id, it) }
        ?: return PanelState.Empty(AbyssusBundle.message("waterErrorNotFound"), null)
    val enabled = node.get("enabled")?.takeIf { it.isBoolean }?.booleanValue() ?: true
    val preset = WaterPresets().parse(node.get("preset")?.takeIf { it.isTextual }?.textValue())
    return PanelState.WaterDetails(target, surface, WaterText().texts(node), enabled, preset)
}


/** Reads what the layout of [selection] shows. Safe off the EDT. `Empty` with a message when the scene cannot be read. */
internal fun readStructuralState(selection: Structural): PanelState {
    val builders = PanelBuilders(EditorBundle, ::isGameComponent)
    fun project(abss: VirtualFile?) = abss?.name.orEmpty()
    fun scene(file: VirtualFile): JsonNode? = runCatchingKeepingCancellation {
        SceneJson().parse(runReadAction { textOf(file) }).also { AbyssusDocumentFormat().requireSupported(it, DocumentKind.SCENE) }
    }.getOrNull()
    return when (selection) {
        is Structural.Folder -> {
            val trail = listOf(project(selection.abss)) + if (selection.kind == PanelFolderKind.GROUP) listOf(AbyssusBundle.message("panelAssetsFolder")) else emptyList()
            PanelState.StructuralDetails(selection, builders.folder(selection.kind, selection.name, selection.items, trail.filter { it.isNotEmpty() }))
        }

        is Structural.Setting -> {
            val root = scene(selection.file)
                ?: return PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", selection.file.name), null)
            PanelState.StructuralDetails(
                selection, builders.setting(root, selection.setting, project(ProjectLayout.abssFor(selection.file))), root,
            )
        }

        is Structural.Ecs -> {
            val root = scene(selection.file)
                ?: return PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", selection.file.name), null)
            PanelState.StructuralDetails(selection, builders.ecs(root, project(ProjectLayout.abssFor(selection.file))), root)
        }

        is Structural.Screen -> PanelState.StructuralDetails(
            selection, builders.uiScreen(selection.name, selection.asset, project(selection.abss)),
        )
    }
}
