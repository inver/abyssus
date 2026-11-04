/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.util.NumberTextStyle
import net.nevinsky.abyssus.lib.core.editor.util.numberText
import net.nevinsky.abyssus.lib.core.editor.util.uiNumber
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiBinding
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingAccess
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingType
import net.nevinsky.abyssus.lib.core.ui.style.parseUiColor

/** What is wrong with one form field, as the key of its message (`uiFormError.<key>`). */
data class FormError(val field: String, val message: String)

/** The result of reading a form: the JSON to store, or the first error per field. */
sealed interface FormResult<out T> {
    data class Valid<T>(val value: T) : FormResult<T>

    data class Invalid(val errors: List<FormError>) : FormResult<Nothing>
}

/** The strings a binding dialog holds; blank optional fields are left out of the document. */
data class BindingForm(
    val name: String = "",
    val type: String = "number",
    val access: String = "readOnly",
    val sample: String = "",
    val min: String = "",
    val max: String = "",
    val step: String = "",
    val maxLength: String = "",
    /** For a list: the record as `name:type, name:type`; [sample] is then a JSON array of rows. */
    val fields: String = "",
)

/** The form that shows the binding [b]. */
fun bindingFormOf(b: UiBinding) =
    BindingForm(
        b.name,
        b.type.json,
        b.access.json,
        b.sampleText.orEmpty(),
        trimmed(b.min),
        trimmed(b.max),
        trimmed(b.step),
        b.maxLength?.toString().orEmpty(),
        b.fields.joinToString(", ") { "${it.name}:${it.type.json}" },
    )

private fun trimmed(v: Double?) = v?.let { numberText(it, NumberTextStyle.UI) }.orEmpty()

/** Validates a [BindingForm] and builds the member object (`access`, `sample`, `min`, ...) for `upsertBinding`. */
fun bindingMembers(form: BindingForm, json: JsonProcessor): FormResult<UiBindingDoc> {
    val errors = mutableListOf<FormError>()
    val type = UiBindingType.entries.firstOrNull { it.json == form.type }
    if (form.name.isBlank()) {
        errors += FormError("name", "name")
    }
    if (type == null) {
        errors += FormError("type", "name")
    }
    fun number(field: String, text: String, positive: Boolean = false): Double? {
        if (text.isBlank()) {
            return null
        }
        val v = text.trim().toDoubleOrNull()?.takeIf(Double::isFinite)
        if (v == null) {
            errors += FormError(field, "number")
        } else if (positive && v <= 0) {
            errors += FormError(field, "positive")
        }
        return v
    }
    val min = number("min", form.min)
    val max = number("max", form.max)
    val step = number("step", form.step, positive = true)
    if (min != null && max != null && min > max) {
        errors += FormError("min", "range")
    }
    val maxLength = form.maxLength.takeIf { it.isNotBlank() }?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
    if (form.maxLength.isNotBlank() && maxLength == null) {
        errors += FormError("maxLength", "length")
    }
    if (type == UiBindingType.LIST) {
        return listMembers(form, errors, json)
    }
    val sample: Any? =
        form.sample
            .takeIf { it.isNotBlank() }
            ?.let {
                when (type) {
                    UiBindingType.NUMBER ->
                        it.trim().toDoubleOrNull()?.takeIf(Double::isFinite)?.let { d -> uiNumber(d) }
                    UiBindingType.BOOLEAN ->
                        when (it.trim().lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        }
                    UiBindingType.STRING -> it
                    UiBindingType.LIST,
                    null -> null
                }.also { value ->
                    if (value == null) {
                        errors += FormError("sample", "sample")
                    }
                }
            }
    if (errors.isNotEmpty()) {
        return FormResult.Invalid(errors)
    }
    return FormResult.Valid(
        UiBindingDoc().apply {
            if (form.access == UiBindingAccess.WRITE.json) {
                access = form.access
            }
            this.sample = sample
            this.min = min?.let(::uiNumber)
            this.max = max?.let(::uiNumber)
            this.step = step?.let(::uiNumber)
            this.maxLength = maxLength
        }
    )
}

/**
 * The record of a list binding from `name:type, name:type`: scalar types only, names unique and non-blank; null on a
 * mistake.
 */
fun parseRecord(spec: String): List<Pair<String, UiBindingType>>? {
    val parts = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) {
        return null
    }
    val result = mutableListOf<Pair<String, UiBindingType>>()
    for (part in parts) {
        val name = part.substringBefore(':').trim()
        val type = UiBindingType.entries.firstOrNull { it.json == part.substringAfter(':', "").trim() }
        if (name.isEmpty() || type == null || type == UiBindingType.LIST || result.any { it.first == name }) {
            return null
        }
        result += name to type
    }
    return result
}

/**
 * The members of a list binding: its `fields` and a `sample` array whose rows match them. A list is always read-only.
 */
private fun listMembers(
    form: BindingForm,
    errors: MutableList<FormError>,
    json: JsonProcessor,
): FormResult<UiBindingDoc> {
    val record = parseRecord(form.fields)
    if (record == null) {
        errors += FormError("fields", "fields")
    }
    val sample: List<*>? =
        form.sample
            .takeIf { it.isNotBlank() }
            ?.let { text ->
                val rows = runCatchingKeepingCancellation { json.parse(text, List::class.java) }.getOrNull()
                val ok =
                    rows != null &&
                        record != null &&
                        rows.all { row ->
                            row is Map<*, *> &&
                                row.all { (k, v) ->
                                    when (record.firstOrNull { it.first == k }?.second) {
                                        UiBindingType.NUMBER -> v is Number
                                        UiBindingType.BOOLEAN -> v is Boolean
                                        UiBindingType.STRING -> v is String
                                        else -> false
                                    }
                                }
                        }
                if (!ok) {
                    errors += FormError("sample", "sample")
                }
                rows
            }
    if (errors.isNotEmpty()) {
        return FormResult.Invalid(errors)
    }
    return FormResult.Valid(
        UiBindingDoc().apply {
            fields =
                record!!
                    .map { (n, t) ->
                        UiRecordFieldDoc().apply {
                            name = n
                            type = t.json
                        }
                    }
                    .toMutableList()
            this.sample = sample
        }
    )
}

/** The strings a style dialog holds. Colours are `#rrggbb[aa]`; the three state backgrounds become state overrides. */
data class StyleForm(
    val name: String = "",
    val fontSize: String = "",
    val color: String = "",
    val background: String = "",
    val padding: String = "",
    val font: String = "",
    val hover: String = "",
    val pressed: String = "",
    val disabled: String = "",
)

/** Validates a [StyleForm] into the style's values (without its name) for `upsertStyle`. */
fun styleValues(form: StyleForm): FormResult<Map<String, Any?>> {
    val errors = mutableListOf<FormError>()
    if (form.name.isBlank()) {
        errors += FormError("name", "name")
    }
    fun color(field: String, text: String): String? {
        if (text.isBlank()) {
            return null
        }
        if (parseUiColor(text.trim()) == null) {
            errors += FormError(field, "color")
        }
        return text.trim()
    }
    fun positive(field: String, text: String): Double? {
        if (text.isBlank()) {
            return null
        }
        val v = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        if (v == null) {
            errors += FormError(field, "number")
        }
        return v
    }
    val size = positive("fontSize", form.fontSize)
    val padding = positive("padding", form.padding)
    val c = color("color", form.color)
    val bg = color("background", form.background)
    val hover = color("hover", form.hover)
    val pressed = color("pressed", form.pressed)
    val disabled = color("disabled", form.disabled)
    if (errors.isNotEmpty()) {
        return FormResult.Invalid(errors)
    }
    val values = LinkedHashMap<String, Any?>()
    size?.let { values["fontSize"] = uiNumber(it) }
    c?.let { values["color"] = it }
    bg?.let { values["background"] = it }
    padding?.let { values["padding"] = uiNumber(it) }
    form.font.takeIf { it.isNotBlank() }?.let { values["font"] = it }
    val states = LinkedHashMap<String, Any?>()
    hover?.let { states["hover"] = mapOf("background" to it) }
    pressed?.let { states["pressed"] = mapOf("background" to it) }
    disabled?.let { states["disabled"] = mapOf("background" to it) }
    if (states.isNotEmpty()) {
        values["states"] = states
    }
    return FormResult.Valid(values)
}
