/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.Asset
import net.nevinsky.abyssus.core.project.SceneError
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.core.project.SceneEntry
import net.nevinsky.abyssus.SceneEcsPaths

/**
 * A named, ordered child of a DTO as shown by the Abyssus view. [enabled] is set when the row is gated by an
 * `xxxEnabled` toggle (see [foldToggles]); `null` means it has no toggle.
 */
data class DtoRow(
    val name: String,
    val value: Any?,
    val enabled: Boolean? = null,
    val toggleName: String? = null,
    /** JSON keys the view skips between the parent and this row (`entities` above an entity), so paths and writes still name the real location. */
    val via: List<String> = emptyList(),
)

/** Rows shown under another name than their property; the file keeps the property name. */
private val DISPLAY_NAMES = mapOf("skyboxName" to "skybox")

/** What the view calls the property [name]. */
fun displayName(name: String): String = DISPLAY_NAMES[name] ?: name

/** True for a value shown as `name: value`; everything else is a node with children. */
fun isScalar(value: Any?): Boolean = when (value) {
    null, is String, is Number, is Boolean, is Enum<*> -> true
    is JsonNode -> !value.isContainerNode
    else -> false
}

/** The text-or-number behind a scalar [value]; `null` for null and for a JSON null. */
fun scalarOf(value: Any?): Any? = when {
    value is JsonNode -> when {
        value.isNull || value.isMissingNode -> null
        value.isTextual -> value.asText()
        value.isBoolean -> value.asBoolean()
        value.isNumber -> value.numberValue()
        else -> value.toString()
    }
    else -> value
}

/**
 * The children of [value] in order: a bound object lists the properties Jackson would serialize (declaration order, no
 * `@JsonIgnore`d metadata), a list its indexes and a JSON node its fields or elements. A scalar has none.
 */
fun childrenOf(value: Any?): List<DtoRow> = when {
    value is SceneEntry -> childrenOf(value.scene)
    isScalar(value) -> emptyList()
    value is JsonNode ->
        if (value.isObject) value.properties().map { (k, v) -> DtoRow(k, v) } else value.mapIndexed { i, v -> DtoRow("$i", v) }
    value is List<*> -> value.mapIndexed { i, v -> DtoRow("$i", v) }
    else -> beanProperties(value!!)
}

private fun beanProperties(bean: Any): List<DtoRow> =
    SceneJson.mapper.serializationConfig.introspect(SceneJson.mapper.constructType(bean.javaClass)).findProperties().mapNotNull { p ->
        val accessor = p.accessor ?: return@mapNotNull null
        accessor.fixAccess(true)
        DtoRow(p.name, accessor.getValue(bean))
    }

/**
 * Folds each boolean `<x>Enabled` row into the sibling it gates (`<x>` or `<x>Name`), which gets [DtoRow.enabled] set
 * instead of the toggle being listed as a separate row. A toggle without a matching sibling stays as a normal row.
 */
fun List<DtoRow>.foldToggles(): List<DtoRow> {
    val folded = mutableMapOf<String, Pair<Boolean, String>>()
    val consumed = mutableSetOf<String>()
    for (toggle in this) {
        val on = toggle.value as? Boolean ?: continue
        if (!toggle.name.endsWith("Enabled")) continue
        val base = toggle.name.removeSuffix("Enabled")
        val target = firstOrNull { it.name == base } ?: firstOrNull { it.name == base + "Name" } ?: continue
        folded[target.name] = on to toggle.name
        consumed += toggle.name
    }
    return filter { it.name !in consumed }.map { r -> folded[r.name]?.let { (on, key) -> r.copy(enabled = on, toggleName = key) } ?: r }
}

/** `Main Scene (6275127)`: the scene name followed by its id; the index stands in for a missing name. */
fun sceneLabel(scene: Scene, index: Int): String {
    val name = scene.name?.takeIf { it.isNotBlank() } ?: AbyssusBundle.message("dtoListElementLabel", "scenes", index)
    return scene.id?.let { "$name ($it)" } ?: name
}

/** What a list element is called in the tree: a scene's label, an asset's folder, a failed scene's file, else `parent[i]`. */
fun elementLabel(parentName: String, element: Any?, index: Int): String = when (element) {
    is SceneEntry -> sceneLabel(element.scene, index)
    is Scene -> sceneLabel(element, index)
    is Asset<*> -> element.name
    is SceneError -> element.file.name
    else -> AbyssusBundle.message("dtoListElementLabel", parentName, index)
}

/** What a row shows: its [label] and, in gray after it, [secondary] (a count). */
data class RowText(val label: String, val secondary: String? = null)

private const val ECS = "ecs"
private const val ENTITIES = "entities"
private const val COMPONENTS = "components"
private const val COMPONENT_SUFFIX = "Component"

/** The scene's `ecs` object: a top-level entry named `ecs` holding a JSON object. */
fun isEcsEntry(entry: DtoEntry) = entry.name == ECS && entry.parentKeys.isEmpty() && (entry.value as? JsonNode)?.isObject == true

/** An entity row, listed directly under `ecs`; its JSON key path is `ecs/entities`. */
fun isEntityEntry(entry: DtoEntry) = entry.parentKeys == listOf(ECS, ENTITIES) && (entry.value as? JsonNode)?.isObject == true

/** A component row of an entity: its JSON key path ends in `components`. */
fun isComponentEntry(entry: DtoEntry) = entry.parentKeys.size == 4 && entry.parentKeys[0] == ECS && entry.parentKeys[1] == ENTITIES && entry.parentKeys[3] == COMPONENTS

private fun entityName(entry: DtoEntry): String =
    SceneEcsPaths().entityName((entry.value as JsonNode).get(COMPONENTS), entry.name)

/** The label and secondary text of [entry]; [label] is what its parent already named it (a list element). */
fun rowText(entry: DtoEntry, label: String? = null): RowText {
    val v = entry.value
    return when {
        v is List<*> && entry.name == "scenes" -> RowText(AbyssusBundle.message("treeScenes"), v.size.toString())
        v is List<*> && entry.name == "assets" -> RowText(AbyssusBundle.message("treeAssets"), v.size.toString())
        isEcsEntry(entry) -> RowText(ECS, AbyssusBundle.message("treeEntities", (v as JsonNode).get(ENTITIES)?.size() ?: 0))
        isEntityEntry(entry) -> RowText(entityName(entry), AbyssusBundle.message("treeComponents", (v as JsonNode).get(COMPONENTS)?.size() ?: 0))
        isComponentEntry(entry) -> RowText(entry.name.removeSuffix(COMPONENT_SUFFIX).ifEmpty { entry.name })
        else -> RowText(label ?: displayName(entry.name))
    }
}

/** The children of an `ecs` object: its entities directly (the `entities` level is skipped), then its other keys. */
fun ecsRows(ecs: JsonNode): List<DtoRow> =
    (ecs.get(ENTITIES)?.takeIf { it.isObject }?.properties()?.map { (id, e) -> DtoRow(id, e, via = listOf(ENTITIES)) } ?: emptyList()) +
        ecs.properties().filter { it.key != ENTITIES || !it.value.isObject }.map { (k, v) -> DtoRow(k, v) }

/** The children of an entity: its components (the `components` level is skipped); its other fields are not rows. */
fun entityRows(entity: JsonNode): List<DtoRow> =
    entity.get(COMPONENTS)?.takeIf { it.isObject }?.properties()?.map { (name, c) -> DtoRow(name, c, via = listOf(COMPONENTS)) } ?: emptyList()
