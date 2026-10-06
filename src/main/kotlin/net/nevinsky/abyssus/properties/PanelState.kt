/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import net.nevinsky.abyssus.editor.meta.AssetReferenceChoices
import net.nevinsky.abyssus.ui.thumbnail

import net.nevinsky.abyssus.editor.ray.SceneRaySettingsState
import net.nevinsky.abyssus.editor.ray.SceneRaySettingsCodec
import net.nevinsky.abyssus.format.DocumentKind
import net.nevinsky.abyssus.format.AbyssusDocumentFormat
import java.io.File
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.SKYBOX_FACES
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrPreview
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.textOf
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.ecs.scene.FieldValue
import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.projectView.*
import net.nevinsky.abyssus.SceneEcsPaths
import net.nevinsky.abyssus.editor.ray.RayDataError
import net.nevinsky.abyssus.editor.ray.RayMaterialIdentity
import net.nevinsky.abyssus.editor.ray.RayMaterialOverrides
import net.nevinsky.abyssus.editor.ray.RayOpticalField
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import net.nevinsky.abyssus.ui.documentDisplayMessage as displayMessage

/** What the panel shows. */
sealed interface PanelState {
    /** No asset to describe: [message], and under it [hint] when there is one. */
    data class Empty(val message: String, val hint: String?) : PanelState

    data class UISceneState(
        val file: VirtualFile,
        val name: String,
        val rayRoot: JsonNode = SceneJson.parse("{}"),
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
        /** The model's Ray Tracing optical overrides, shown with its Render component; null for anything but a model. */
        val optics: RenderOptics? = null,
    ) : PanelState
}

/**
 * A model entity's scene-instance optical overrides (`RenderComponent.rayTracingMaterials`). [materials] follows the
 * model's material table; [unresolved] lists stored identifiers the model no longer has, kept and never retargeted;
 * [problem] says why the table could not be read, in which case nothing is editable.
 */
data class RenderOptics(
    val materials: List<OpticalMaterialRow>,
    val identities: List<RayMaterialIdentity>,
    val unresolved: List<String>,
    val problem: String? = null,
)

/**
 * One material of the model: [error] is why it has no optical editors (a missing or repeated identifier, not PBR); [stored]
 * holds the document's nodes for its fields, which an edit must still find, and [errors] the malformed ones.
 */
data class OpticalMaterialRow(
    val id: String?,
    val error: RayDataError?,
    val stored: Map<RayOpticalField, JsonNode?>,
    val errors: Map<RayOpticalField, RayDataError>,
)

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
        { HdrCell(AbyssusBundle.message("propertiesHdrUnreadable", file, it.displayMessage()), null) },
    )
}

/**
 * Reads the entity (or component) of [target] from the scene's current text for display. Safe off the EDT. The state is
 * `Empty` with a message when the scene cannot be read or the entity or component is gone.
 */
fun readEntityState(target: ComponentTarget, services: PanelServices): PanelState {
    val root = runCatchingKeepingCancellation {
        SceneJson.parse(runReadAction { textOf(target.file) }).also {
            net.nevinsky.abyssus.format.AbyssusDocumentFormat()
                .requireSupported(it, net.nevinsky.abyssus.format.DocumentKind.SCENE)
        }
    }
        .getOrElse {
            return PanelState.Empty(
                AbyssusBundle.message("propertiesSceneUnreadable", it.displayMessage()),
                null
            )
        }
    val entity = SceneEcsPaths().entities(root)?.get(target.entityId)?.takeIf { it.isObject }
        ?: return PanelState.Empty(AbyssusBundle.message("propertiesEntityGone", target.entityId), null)
    val components = entity.get("components")?.takeIf { it.isObject }
    val kinds = target.kind?.let { listOf(it) } ?: components?.fieldNames()?.asSequence()?.toList().orEmpty()
    val assets = SceneComponentEdits.renderAssets(target.file, services.metaFiles).map { it.name }
    val byType = SceneComponentEdits.assetsByType(target.file, services.metaFiles).orEmpty()
    val editor = services.schemas.editorFor(target.file)
    val sections = kinds.map { kind ->
        val modeled = editor.kindOf(kind)
        val fields = editor.read(root, target.entityId, kind)
        when {
            components?.has(kind) != true ->
                return PanelState.Empty(
                    AbyssusBundle.message(
                        "propertiesComponentGone",
                        target.entityId,
                        kind.removeSuffix("Component")
                    ), null
                )

            modeled == null || fields == null -> ComponentSection(
                kind,
                kind.removeSuffix("Component").ifEmpty { kind },
                emptyList(),
                SceneJson.pretty(components!![kind])
            )

            else -> ComponentSection(kind, modeled.label, fields.map { field ->
                when {
                    field.kind != FieldKind.ASSET_NAME -> field
                    field.assetType != null -> field.copy(choices = (listOf("") + byType[field.assetType].orEmpty() + field.value).distinct())
                    else -> field.copy(choices = (assets + field.value).filter(String::isNotEmpty).distinct())
                }
            }, null)
        }
    }
    val name = SceneEcsPaths().entityName(components, target.entityId)
    val addable = if (target.kind == null) editor.missingKinds(root, target.entityId).map { it.name } else emptyList()
    val optics = if (kinds.contains(RENDER_COMPONENT)) components?.get(RENDER_COMPONENT)
        ?.let { readOptics(target, it, services) } else null
    return PanelState.EntityDetails(target, name, sections, addable, optics)
}

private const val RENDER_COMPONENT = "RenderComponent"

/** The optical overrides of a Render component that draws a model asset, against that model's material table. */
private fun readOptics(target: ComponentTarget, render: JsonNode, services: PanelServices): RenderOptics? {
    val asset = render.path("renderable").path("asset")
    if (asset.path("type").asText() != "MODEL") return null
    val assetName = asset.path("assetName").asText().ifEmpty { return null }
    val codec = RayMaterialOverrides()
    val stored = codec.read(render)
    val identities = runCatchingKeepingCancellation {
        ProjectLayout.projectDirFor(target.file)?.let { services.rayMaterials(it, assetName) }
    }.getOrElse {
        return RenderOptics(
            emptyList(),
            emptyList(),
            stored.values.keys.toList(),
            AbyssusBundle.message("propertiesOpticsUnreadable", it.displayMessage())
        )
    }
        ?: return RenderOptics(
            emptyList(),
            emptyList(),
            stored.values.keys.toList(),
            AbyssusBundle.message("propertiesOpticsNoModel", assetName)
        )
    val map = render.get("rayTracingMaterials")
    val rows = identities.map { it.id }.distinct().map { id ->
        val block = id?.let { map?.get(it) }
        OpticalMaterialRow(
            id, if (id == null) RayDataError.MATERIAL_ID else codec.eligible(id, identities),
            RayOpticalField.entries.associateWith { block?.get(it.key) },
            RayOpticalField.entries.mapNotNull { field -> stored.errors["$id.${field.key}"]?.let { field to it } }
                .toMap()
        )
    }
    val known = identities.mapNotNullTo(HashSet()) { it.id }
    val problem = stored.errors["rayTracingMaterials"]?.let { AbyssusBundle.message("propertiesRayError${it.name}") }
    return RenderOptics(rows, identities, stored.values.keys.filter { it !in known }, problem)
}

private fun faces(folder: VirtualFile, meta: AssetMeta.Loaded): List<FaceCell> {
    val additional = meta.json.get("additional")
    return SKYBOX_FACES.map { face ->
        val file = additional?.get(face)?.takeIf { it.isTextual }?.asText()
        FaceCell(face, file ?: AbyssusBundle.message("dtoNullValue"), file?.let { thumbnail(folder, it) })
    }
}

private fun readTerrainNow(
    folder: VirtualFile,
    meta: AssetMeta.Loaded,
    services: PanelServices
): net.nevinsky.abyssus.terrain.TerrainSource {
    val text = folder.findChild(META_FILE)?.let { runReadAction { textOf(it) } } ?: ""
    return net.nevinsky.abyssus.terrain.readTerrainSource(
        java.io.File(folder.path),
        text,
        meta.json,
        AssetReferenceChoices(services.json),
        services.terrainRecipes
    )
}

/** The terrain of [folder] as it is now, for the checks Apply makes just before it writes. UI thread. */
fun readTerrainSourceNow(folder: VirtualFile, services: PanelServices): net.nevinsky.abyssus.terrain.TerrainSource {
    val meta = loadAssetMeta(folder, services.metaFiles) as? AssetMeta.Loaded
        ?: return net.nevinsky.abyssus.terrain.TerrainSource.Unusable("meta.json")
    return readTerrainNow(folder, meta, services)
}

/** Current native scene preferences, read off the EDT independently of renderer availability. */
fun readSceneState(file: VirtualFile, name: String): PanelState = runCatchingKeepingCancellation {
    val root = SceneJson.parse(runReadAction { textOf(file) })
    AbyssusDocumentFormat().requireSupported(root, DocumentKind.SCENE)
    PanelState.UISceneState(file, name, root)
}.getOrElse { PanelState.Empty(AbyssusBundle.message("propertiesSceneUnreadable", it.displayMessage()), null) }
