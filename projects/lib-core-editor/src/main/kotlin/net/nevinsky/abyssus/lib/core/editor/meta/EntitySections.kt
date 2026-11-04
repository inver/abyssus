/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.meta

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.components.FieldKind
import net.nevinsky.abyssus.lib.core.editor.components.FieldValue
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/** One component of an entity: its [fields] when the plugin edits it, else the file's JSON as [raw] text, read only. */
data class ComponentSection(val kind: String, val label: String, val fields: List<FieldValue>, val raw: String?)

/** What the panel shows of one entity, read from its scene: its sections, or why there are none. */
sealed interface EntitySections {
    /** [addable] names the modeled kinds the entity lacks (offered when the whole entity is shown). */
    data class Read(val name: String, val sections: List<ComponentSection>, val addable: List<String>) : EntitySections

    data class Gone(val message: String) : EntitySections
}

/**
 * The scene file's asset names offered by asset fields: [all] render assets, and [byType] those of each asset type.
 * [meshes] lists the mesh ids of a model asset (it may parse the model, so the caller is off the UI thread); null when
 * they are not known, which leaves a Render component's mesh field out.
 */
class EntityAssetChoices(
    val all: List<String>,
    val byType: Map<String, Collection<String>>,
    val meshes: (String) -> List<String>? = { null },
)

/**
 * Reads entity [entityId] of the scene [scene] for display: every component, or only [kind] when it is set, through
 * [editor]. A component the editor does not model is shown read only as its JSON text, written by [json].
 */
fun readEntitySections(
    scene: SceneDto,
    entityId: Long,
    kind: String?,
    editor: ComponentEditor,
    assets: EntityAssetChoices,
    messages: EditorMessages,
    json: JsonProcessor,
): EntitySections {
    val entity =
        scene.entity(entityId) ?: return EntitySections.Gone(messages.message("propertiesEntityGone", entityId))
    val shown = entity.components.filter { it !is HeaderComponent && it !is RawComponentsWrapper }
    val rawEntries = entity.components.filterIsInstance<RawComponentsWrapper>().flatMap { it.entries }
    val names = shown.map { it.javaClass.simpleName } + rawEntries.map { it.name.substringAfterLast('.') }
    val kinds = kind?.let { listOf(it) } ?: names
    val sections = kinds.map { name ->
        val modeled = editor.kindOf(name)
        val fields = editor.read(scene, entityId, name)
        when {
            name !in names ->
                return EntitySections.Gone(
                    messages.message("propertiesComponentGone", entityId, name.removeSuffix("Component"))
                )

            modeled == null || fields == null ->
                ComponentSection(
                    name,
                    name.removeSuffix("Component").ifEmpty { name },
                    emptyList(),
                    rawEntries.firstOrNull { it.name.substringAfterLast('.') == name }?.json
                        ?: shown
                            .firstOrNull { it.javaClass.simpleName == name }
                            ?.let { json.componentTree(it).toString() },
                )

            else ->
                ComponentSection(
                    name,
                    modeled.label,
                    withMeshChoices(fields, assets).map { field ->
                        when {
                            field.kind != FieldKind.ASSET_NAME -> field
                            field.assetType != null ->
                                field.copy(
                                    choices =
                                        (listOf("") + assets.byType[field.assetType].orEmpty() + field.value).distinct()
                                )
                            else ->
                                field.copy(choices = (assets.all + field.value).filter(String::isNotEmpty).distinct())
                        }
                    },
                    null,
                )
        }
    }
    val addable =
        if (kind == null) {
            editor.missingKinds(scene, entityId).map { it.name }
        } else {
            emptyList()
        }
    return EntitySections.Read(
        entity.component(NameComponent::class.java)?.name?.takeIf { it.isNotBlank() } ?: entityId.toString(),
        sections,
        addable,
    )
}

/**
 * Fills the mesh field of a Render component with the meshes of the model it names. The field is left out unless the
 * component draws a `MODEL` whose meshes are known: it has nothing to offer for a terrain or an unreadable model.
 */
private fun withMeshChoices(fields: List<FieldValue>, assets: EntityAssetChoices): List<FieldValue> {
    if (fields.none { it.kind == FieldKind.MESH_ID }) {
        return fields
    }
    val type = fields.firstOrNull { it.field == "assetType" }?.value
    val asset = fields.firstOrNull { it.field == "assetName" }?.value.orEmpty()
    val meshes =
        if (type == MetaType.MODEL.name && asset.isNotEmpty()) {
            assets.meshes(asset)
        } else {
            null
        }
    return fields.mapNotNull { field ->
        if (field.kind != FieldKind.MESH_ID) {
            field
        } else {
            meshes?.let { field.copy(choices = it) }
        }
    }
}
