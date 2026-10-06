/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.ecs.scene.FieldKind
import net.nevinsky.abyssus.ecs.scene.FieldValue
import net.nevinsky.abyssus.editor.EditorMessages
import net.nevinsky.abyssus.editor.document.SceneDocument
import net.nevinsky.abyssus.editor.document.SceneJson
import net.nevinsky.abyssus.editor.document.documentDisplayMessage
import net.nevinsky.abyssus.editor.ray.RayDataError
import net.nevinsky.abyssus.editor.ray.RayMaterialIdentity
import net.nevinsky.abyssus.editor.ray.RayMaterialOverrides
import net.nevinsky.abyssus.editor.ray.RayOpticalField

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

/** What the panel shows of one entity, read from its scene's tree: its sections, or why there are none. */
sealed interface EntitySections {
    /** [addable] names the modeled kinds the entity lacks (offered when the whole entity is shown). */
    data class Read(val name: String, val sections: List<ComponentSection>, val addable: List<String>, val render: JsonNode?) :
        EntitySections

    data class Gone(val message: String) : EntitySections
}

/** The scene file's asset names offered by asset fields: [all] render assets, and [byType] those of each asset type. */
class EntityAssetChoices(val all: List<String>, val byType: Map<String, Collection<String>>)

private const val RENDER_COMPONENT = "RenderComponent"

/**
 * Reads entity [entityId] of the admitted scene [root] for display: every component, or only [kind] when it is set,
 * through [editor]. [render] is the entity's Render component when it is shown, for its optical overrides.
 */
fun readEntitySections(
    root: JsonNode,
    entityId: String,
    kind: String?,
    editor: ComponentEditor,
    assets: EntityAssetChoices,
    messages: EditorMessages,
): EntitySections {
    val entity = SceneDocument(root).entity(entityId)
        ?: return EntitySections.Gone(messages.message("propertiesEntityGone", entityId))
    val components = entity.components
    val kinds = kind?.let { listOf(it) } ?: components?.fieldNames()?.asSequence()?.toList().orEmpty()
    val sections = kinds.map { name ->
        val modeled = editor.kindOf(name)
        val fields = editor.read(root, entityId, name)
        when {
            components?.has(name) != true ->
                return EntitySections.Gone(messages.message("propertiesComponentGone", entityId, name.removeSuffix("Component")))

            modeled == null || fields == null ->
                ComponentSection(name, name.removeSuffix("Component").ifEmpty { name }, emptyList(), SceneJson().pretty(components[name]))

            else -> ComponentSection(name, modeled.label, fields.map { field ->
                when {
                    field.kind != FieldKind.ASSET_NAME -> field
                    field.assetType != null ->
                        field.copy(choices = (listOf("") + assets.byType[field.assetType].orEmpty() + field.value).distinct())
                    else -> field.copy(choices = (assets.all + field.value).filter(String::isNotEmpty).distinct())
                }
            }, null)
        }
    }
    val addable = if (kind == null) editor.missingKinds(root, entityId).map { it.name } else emptyList()
    val render = if (kinds.contains(RENDER_COMPONENT)) components?.get(RENDER_COMPONENT) else null
    return EntitySections.Read(entity.name, sections, addable, render)
}

/**
 * The optical overrides of a Render component [render] that draws a model asset, against that model's material table
 * from [materials] (null when the model cannot be found; it may throw). Null for a Render component of anything else.
 */
fun readRenderOptics(render: JsonNode, materials: (String) -> List<RayMaterialIdentity>?, messages: EditorMessages): RenderOptics? {
    val asset = render.path("renderable").path("asset")
    if (asset.path("type").asText() != "MODEL") return null
    val assetName = asset.path("assetName").asText().ifEmpty { return null }
    val codec = RayMaterialOverrides()
    val stored = codec.read(render)
    val identities = runCatchingKeepingCancellation { materials(assetName) }.getOrElse {
        return RenderOptics(
            emptyList(), emptyList(), stored.values.keys.toList(),
            messages.message("propertiesOpticsUnreadable", it.documentDisplayMessage(messages))
        )
    } ?: return RenderOptics(
        emptyList(), emptyList(), stored.values.keys.toList(), messages.message("propertiesOpticsNoModel", assetName)
    )
    val map = render.get("rayTracingMaterials")
    val rows = identities.map { it.id }.distinct().map { id ->
        val block = id?.let { map?.get(it) }
        OpticalMaterialRow(
            id, if (id == null) RayDataError.MATERIAL_ID else codec.eligible(id, identities),
            RayOpticalField.entries.associateWith { block?.get(it.key) },
            RayOpticalField.entries.mapNotNull { field -> stored.errors["$id.${field.key}"]?.let { field to it } }.toMap()
        )
    }
    val known = identities.mapNotNullTo(HashSet()) { it.id }
    val problem = stored.errors["rayTracingMaterials"]?.let { messages.message("propertiesRayError${it.name}") }
    return RenderOptics(rows, identities, stored.values.keys.filter { it !in known }, problem)
}
