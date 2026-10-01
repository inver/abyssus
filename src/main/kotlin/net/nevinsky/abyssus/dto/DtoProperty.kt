package net.nevinsky.abyssus.dto

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
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
    ) : DtoValue

    /** A list of DTOs or a generic JSON array, in declaration order. */
    data class Items(val items: List<DtoValue>) : DtoValue
}

interface DtoSource {
    fun properties(): List<DtoProperty>

    fun toValue(label: String? = null): DtoValue.Obj = DtoValue.Obj(properties(), label)
}

fun JsonElement.toDtoValue(): DtoValue = when (this) {
    is JsonNull -> DtoValue.Scalar(null)
    is JsonPrimitive -> DtoValue.Scalar(if (isString) asString else if (isBoolean) asBoolean else asNumber)
    is JsonObject -> DtoValue.Obj(entrySet().map { (k, v) -> DtoProperty(k, v.toDtoValue()) })
    is JsonArray -> DtoValue.Items(map { it.toDtoValue() })
    else -> DtoValue.Scalar(toString())
}
