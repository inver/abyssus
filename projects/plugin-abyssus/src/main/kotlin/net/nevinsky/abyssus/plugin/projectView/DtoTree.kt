/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementCopyCount
import com.fasterxml.jackson.annotation.JsonIgnore
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import net.nevinsky.abyssus.lib.core.ui.model.UiMeta
import net.nevinsky.abyssus.lib.core.ui.model.UiScreen
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectDto
import net.nevinsky.abyssus.plugin.dto.SceneEntry
import net.nevinsky.abyssus.plugin.dto.SceneError

/**
 * A named, ordered child of a DTO as shown by the Abyssus view. [enabled] is set when the row is gated by an
 * `xxxEnabled` toggle (see [foldToggles]); `null` means it has no toggle.
 */
data class DtoRow(
    val name: String,
    val value: Any?,
    val enabled: Boolean? = null,
    val toggleName: String? = null,
    /**
     * JSON keys the view skips between the parent and this row (`entities` above an entity), so paths and writes still
     * name the real location.
     */
    val via: List<String> = emptyList(),
)

/** Rows shown under another name than their property; the file keeps the property name. */
private val DISPLAY_NAMES = mapOf("skyboxName" to "skybox")

/** What the view calls the property [name]. */
fun displayName(name: String): String = DISPLAY_NAMES[name] ?: name

/** What a scalar row shows: the value itself. */
fun scalarOf(value: Any?): Any? = value

/** True for a value shown as `name: value`; everything else is a node with children. */
fun isScalar(value: Any?): Boolean = when (value) {
    null, is String, is Number, is Boolean, is Enum<*> -> true
    is EcsNode, is EntityNode, is ComponentNode, is PartsNode, is PartNode -> false
    else -> false
}


/**
 * The children of [value] in order: a bound object lists the properties Jackson would serialize (declaration order, no
 * `@JsonIgnore`d metadata), a list its indexes and a JSON node its fields or elements. A scalar has none.
 */
fun childrenOf(value: Any?): List<DtoRow> = when {
    value is SceneEntry -> sceneRows(value.scene)
    value is ProjectDto -> projectRows(value)
    value is TreeSource -> value.children()
    value is UiMeta -> emptyList()
    value is UiScreen || value is UiScreenEntry -> emptyList()
    value is SceneDto -> sceneRows(value)
    value is EcsNode -> ecsRows(value.scene)
    value is EntityNode -> entityRows(value)
    value is ComponentNode -> emptyList()
    value is PartsNode -> partRows(value)
    value is PartNode -> partRows(value)
    isScalar(value) -> emptyList()
    value is List<*> -> value.mapIndexed { i, v -> DtoRow("$i", v) }
    else -> beanProperties(value!!)
}

/**
 * A scene's own properties, with the `ecs` row standing for its entities. The `abyssus` extension block is not listed.
 */
private fun sceneRows(scene: SceneDto): List<DtoRow> =
    beanProperties(scene).filter { it.name != "water" && it.name != "abyssus" }
        .map { if (it.name == "ecs") {
            it.copy(value = EcsNode(scene))
        } else {
            it
        } }

/**
 * The properties of a bean in declaration order: the constructor parameters of a Kotlin class (skipping `@JsonIgnore`d
 * ones), else the public fields of a Java class.
 */
private fun beanProperties(bean: Any): List<DtoRow> {
    val kotlin = bean::class
    val constructor = kotlin.primaryConstructor
    if (constructor != null) {
        val properties = kotlin.memberProperties.associateBy { it.name }
        return constructor.parameters.mapNotNull { parameter ->
            val property = properties[parameter.name] ?: return@mapNotNull null
            if (property.hasAnnotation<JsonIgnore>() || property.getter.hasAnnotation<JsonIgnore>()) {
                return@mapNotNull null
            }
            property.isAccessible = true
            DtoRow(property.name, property.getter.call(bean))
        }
    }
    return bean.javaClass.fields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .map { DtoRow(it.name, it.get(bean)) }
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
        if (!toggle.name.endsWith("Enabled")) {
            continue
        }
        val base = toggle.name.removeSuffix("Enabled")
        val target = firstOrNull { it.name == base } ?: firstOrNull { it.name == base + "Name" } ?: continue
        folded[target.name] = on to toggle.name
        consumed += toggle.name
    }
    return filter { it.name !in consumed }.map { r ->
        folded[r.name]?.let { (on, key) ->
            r.copy(
                enabled = on,
                toggleName = key
            )
        } ?: r
    }
}

/** `Main Scene (6275127)`: the scene name followed by its id; the index stands in for a missing name. */
fun sceneLabel(scene: SceneDto, index: Int): String {
    val name = scene.name?.takeIf { it.isNotBlank() } ?: AbyssusBundle.message("dtoListElementLabel", "scenes", index)
    return "$name (${scene.id})"
}

/**
 * What a list element is called in the tree: a scene's label, an asset's folder, a failed scene's file, else
 * `parent[i]`.
 */
fun elementLabel(parentName: String, element: Any?, index: Int): String = when (element) {
    is SceneEntry -> sceneLabel(element.scene, index)
    is SceneDto -> sceneLabel(element, index)
    is Asset<*> -> element.name
    is SceneError -> element.file.name
    else -> AbyssusBundle.message("dtoListElementLabel", parentName, index)
}

/** What a row shows: its [label] and, in gray after it, [secondary] (a count). */
data class RowText(val label: String, val secondary: String? = null)

private const val ECS = "ecs"
private const val COMPONENT_SUFFIX = "Component"

fun isEcsEntry(entry: DtoEntry) = entry.value is EcsNode
fun isEntityEntry(entry: DtoEntry) = entry.value is EntityNode
fun isComponentEntry(entry: DtoEntry) = entry.value is ComponentNode
private fun entityName(node: EntityNode): String = node.scene.nameOf(node.id) ?: node.id.toString()

/** `Models`, `Skyboxes`, ...; a type without a label shows its capitalised name. */
fun groupLabel(group: String): String = when (group) {
    "MODEL" -> AbyssusBundle.message("treeGroup.MODEL")
    "TERRAIN" -> AbyssusBundle.message("treeGroup.TERRAIN")
    "FOLIAGE" -> AbyssusBundle.message("treeGroup.FOLIAGE")
    "TEXTURE" -> AbyssusBundle.message("treeGroup.TEXTURE")
    "SKYBOX" -> AbyssusBundle.message("treeGroup.SKYBOX")
    "CLOUDS" -> AbyssusBundle.message("treeGroup.CLOUDS")
    else -> group.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** The label and secondary text of [entry]; [label] is what its parent already named it (a list element). */
fun rowText(entry: DtoEntry, label: String? = null): RowText {
    val v = entry.value
    return when {
        v is List<*> && entry.name == "scenes" -> RowText(AbyssusBundle.message("treeScenes"), v.size.toString())
        v is TreeSource -> v.text()
        v is UiScreenEntry -> RowText(v.screen.name.ifBlank { v.screen.id }, v.asset.name)
        v is UiScreen -> RowText(v.name.ifBlank { v.id }, v.kind.json)
        v is EcsNode -> RowText(ECS, AbyssusBundle.message("treeEntities", v.count))
        v is EntityNode -> entityText(v)
        v is ComponentNode -> RowText(entry.name.removeSuffix(COMPONENT_SUFFIX).ifEmpty { entry.name })
        v is PartsNode -> RowText(AbyssusBundle.message("treeParts"), v.info?.count?.toString())
        v is PartNode -> RowText(entry.name, partMarks(v.part))
        else -> RowText(label ?: displayName(entry.name))
    }
}

/**
 * An entity row: its name and `N components`, with `M copies` after it for a path arrangement and `M children` for an
 * entity with child entities.
 */
private fun entityText(node: EntityNode): RowText {
    val entity = node.entity ?: return RowText(node.id.toString())
    val components = componentCountOf(entity)
    val copies = arrangementCopyCount(entity)
    val children = treeChildren(node.scene, node.id).size
    return RowText(
        entityName(node),
        if (children > 0) {
            AbyssusBundle.message("treeComponentsChildren", components, children)
        } else if (copies > 0) {
            AbyssusBundle.message(
                "treeComponentsCopies",
                components,
                copies
            )
        } else {
            AbyssusBundle.message("treeComponents", components)
        },
    )
}
