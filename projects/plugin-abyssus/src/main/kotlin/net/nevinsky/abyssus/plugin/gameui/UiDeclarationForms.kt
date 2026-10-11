/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.ui.model.UiBinding
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingAccess
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingType
import net.nevinsky.abyssus.lib.core.ui.style.parseUiColor

/** A number as a document would hold it: whole values without a fraction. */
private fun jsonNumber(v: Double): com.fasterxml.jackson.databind.JsonNode =
    if (v == Math.rint(v) && Math.abs(v) < 1e9) JsonNodeFactory.instance.numberNode(v.toInt()) else JsonNodeFactory.instance.numberNode(v)

/** What is wrong with one form field, as the key of its message (`uiFormError.<key>`). */
data class FormError(val field: String, val message: String)

/** The result of reading a form: the JSON to store, or the first error per field. */
sealed interface FormResult<out T> {
    data class Valid<T>(val value: T) : FormResult<T>
    data class Invalid(val errors: List<FormError>) : FormResult<Nothing>
}

/** The strings a binding dialog holds; blank optional fields are left out of the document. */
data class BindingForm(
    val name: String = "", val type: String = "number", val access: String = "readOnly", val sample: String = "",
    val min: String = "", val max: String = "", val step: String = "", val maxLength: String = "",
) {
    companion object {
        fun of(b: UiBinding) = BindingForm(b.name, b.type.json, b.access.json, b.sample?.asText().orEmpty(),
            b.min?.let(::trim).orEmpty(), b.max?.let(::trim).orEmpty(), b.step?.let(::trim).orEmpty(), b.maxLength?.toString().orEmpty())

        private fun trim(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()
    }
}

/** Validates a [BindingForm] and builds the member object (`access`, `sample`, `min`, ...) for `upsertBinding`. */
fun bindingMembers(form: BindingForm): FormResult<ObjectNode> {
    val errors = mutableListOf<FormError>()
    val type = UiBindingType.entries.firstOrNull { it.json == form.type }
    if (form.name.isBlank()) errors += FormError("name", "name")
    if (type == null) errors += FormError("type", "name")
    fun number(field: String, text: String, positive: Boolean = false): Double? {
        if (text.isBlank()) return null
        val v = text.trim().toDoubleOrNull()?.takeIf(Double::isFinite)
        if (v == null) errors += FormError(field, "number") else if (positive && v <= 0) errors += FormError(field, "positive")
        return v
    }
    val min = number("min", form.min); val max = number("max", form.max); val step = number("step", form.step, positive = true)
    if (min != null && max != null && min > max) errors += FormError("min", "range")
    val maxLength = form.maxLength.takeIf { it.isNotBlank() }?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
    if (form.maxLength.isNotBlank() && maxLength == null) errors += FormError("maxLength", "length")
    val f = JsonNodeFactory.instance
    val sample = form.sample.takeIf { it.isNotBlank() }?.let {
        when (type) {
            UiBindingType.NUMBER -> it.trim().toDoubleOrNull()?.takeIf(Double::isFinite)?.let { d -> jsonNumber(d) }
            UiBindingType.BOOLEAN -> when (it.trim().lowercase()) { "true" -> f.booleanNode(true); "false" -> f.booleanNode(false); else -> null }
            UiBindingType.STRING -> f.textNode(it)
            null -> null
        }.also { node -> if (node == null) errors += FormError("sample", "sample") }
    }
    if (errors.isNotEmpty()) return FormResult.Invalid(errors)
    return FormResult.Valid(f.objectNode().apply {
        if (form.access == UiBindingAccess.WRITE.json) put("access", form.access)
        sample?.let { set<com.fasterxml.jackson.databind.JsonNode>("sample", it) }
        min?.let { set<com.fasterxml.jackson.databind.JsonNode>("min", jsonNumber(it)) }; max?.let { set<com.fasterxml.jackson.databind.JsonNode>("max", jsonNumber(it)) }
        step?.let { set<com.fasterxml.jackson.databind.JsonNode>("step", jsonNumber(it)) }
        maxLength?.let { put("maxLength", it) }
    })
}

/** The strings a style dialog holds. Colours are `#rrggbb[aa]`; the three state backgrounds become state overrides. */
data class StyleForm(
    val name: String = "", val fontSize: String = "", val color: String = "", val background: String = "", val padding: String = "",
    val font: String = "", val hover: String = "", val pressed: String = "", val disabled: String = "",
)

/** Validates a [StyleForm] into the style's values (without its name) for `upsertStyle`. */
fun styleValues(form: StyleForm): FormResult<ObjectNode> {
    val errors = mutableListOf<FormError>()
    if (form.name.isBlank()) errors += FormError("name", "name")
    fun color(field: String, text: String): String? {
        if (text.isBlank()) return null
        if (parseUiColor(text.trim()) == null) errors += FormError(field, "color")
        return text.trim()
    }
    fun positive(field: String, text: String): Double? {
        if (text.isBlank()) return null
        val v = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        if (v == null) errors += FormError(field, "number")
        return v
    }
    val size = positive("fontSize", form.fontSize); val padding = positive("padding", form.padding)
    val c = color("color", form.color); val bg = color("background", form.background)
    val hover = color("hover", form.hover); val pressed = color("pressed", form.pressed); val disabled = color("disabled", form.disabled)
    if (errors.isNotEmpty()) return FormResult.Invalid(errors)
    val f = JsonNodeFactory.instance
    return FormResult.Valid(f.objectNode().apply {
        size?.let { set<com.fasterxml.jackson.databind.JsonNode>("fontSize", jsonNumber(it)) }
        c?.let { put("color", it) }; bg?.let { put("background", it) }; padding?.let { set<com.fasterxml.jackson.databind.JsonNode>("padding", jsonNumber(it)) }
        form.font.takeIf { it.isNotBlank() }?.let { put("font", it) }
        val states = f.objectNode()
        hover?.let { states.putObject("hover").put("background", it) }
        pressed?.let { states.putObject("pressed").put("background", it) }
        disabled?.let { states.putObject("disabled").put("background", it) }
        if (states.size() > 0) set<com.fasterxml.jackson.databind.JsonNode>("states", states)
    })
}
