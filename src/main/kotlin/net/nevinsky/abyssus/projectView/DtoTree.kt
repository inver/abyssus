/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.projectView

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.AssetInfo
import net.nevinsky.abyssus.dto.SceneError
import net.nevinsky.abyssus.filetype.SceneJson
import net.nevinsky.abyssus.scene.SceneDto

/**
 * A named, ordered child of a DTO as shown by the Abyssus view. [enabled] is set when the row is gated by an
 * `xxxEnabled` toggle (see [foldToggles]); `null` means it has no toggle.
 */
data class DtoRow(
    val name: String,
    val value: Any?,
    val enabled: Boolean? = null,
    val toggleName: String? = null,
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
fun sceneLabel(scene: SceneDto, index: Int): String {
    val name = scene.name?.takeIf { it.isNotBlank() } ?: AbyssusBundle.message("dtoListElementLabel", "scenes", index)
    return scene.id?.let { "$name ($it)" } ?: name
}

/** What a list element is called in the tree: a scene's label, an asset's folder, a failed scene's file, else `parent[i]`. */
fun elementLabel(parentName: String, element: Any?, index: Int): String = when (element) {
    is SceneDto -> sceneLabel(element, index)
    is AssetInfo -> element.name
    is SceneError -> element.file.name
    else -> AbyssusBundle.message("dtoListElementLabel", parentName, index)
}
