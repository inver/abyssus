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

package net.nevinsky.abyssus.dto

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile

/**
 * A named, ordered entry of a DTO as shown by the Abyssus view. [enabled] is set when the entry is
 * gated by an `xxxEnabled` toggle (see [foldToggles]); `null` means it has no toggle.
 */
data class DtoProperty(
    val name: String,
    val value: DtoValue,
    val enabled: Boolean? = null,
    val toggleName: String? = null,
)

/**
 * Folds each boolean `<x>Enabled` property into the sibling it gates (`<x>` or `<x>Name`), which
 * gets [DtoProperty.enabled] set instead of the toggle being listed as a separate entry. A toggle
 * without a matching sibling stays as a normal property.
 */
fun List<DtoProperty>.foldToggles(): List<DtoProperty> {
    val folded = mutableMapOf<String, Pair<Boolean, String>>()
    val consumed = mutableSetOf<String>()
    for (toggle in this) {
        val on = (toggle.value as? DtoValue.Scalar)?.value as? Boolean ?: continue
        if (!toggle.name.endsWith("Enabled")) continue
        val base = toggle.name.removeSuffix("Enabled")
        val target = firstOrNull { it.name == base } ?: firstOrNull { it.name == base + "Name" } ?: continue
        folded[target.name] = on to toggle.name
        consumed += toggle.name
    }
    return filter { it.name !in consumed }.map { p -> folded[p.name]?.let { (on, key) -> p.copy(enabled = on, toggleName = key) } ?: p }
}

sealed interface DtoValue {
    /** A leaf value; `null` is a legitimate value. */
    data class Scalar(val value: Any?) : DtoValue

    /** A nested DTO or generic JSON object. [label] names it when it is a list element. */
    data class Obj(
        val properties: List<DtoProperty>,
        val label: String? = null,
        /** The file this object was read from, when it is the root of one. */
        val source: VirtualFile? = null,
        /** Set on a project asset that no scene reaches. */
        val unused: Boolean = false,
        /** True for an entry of a project's `assets` list. */
        val asset: Boolean = false,
    ) : DtoValue

    /** A list of DTOs or a generic JSON array, in declaration order. */
    data class Items(val items: List<DtoValue>) : DtoValue
}

interface DtoSource {
    fun properties(): List<DtoProperty>

    fun toValue(label: String? = null): DtoValue.Obj = DtoValue.Obj(properties(), label)
}

fun JsonNode.toDtoValue(): DtoValue = when {
    isNull || isMissingNode -> DtoValue.Scalar(null)
    isObject -> DtoValue.Obj(properties().map { (k, v) -> DtoProperty(k, v.toDtoValue()) })
    isArray -> DtoValue.Items(map { it.toDtoValue() })
    isTextual -> DtoValue.Scalar(asText())
    isBoolean -> DtoValue.Scalar(asBoolean())
    isNumber -> DtoValue.Scalar(numberValue())
    else -> DtoValue.Scalar(toString())
}
