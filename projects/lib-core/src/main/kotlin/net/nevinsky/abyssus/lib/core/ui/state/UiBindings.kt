/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.state

import net.nevinsky.abyssus.lib.core.ui.model.*
import java.util.Locale
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor

/** A typed binding value as a host publishes it and a control proposes it. */
sealed interface UiValue {
    val type: UiBindingType

    data class Num(val value: Double) : UiValue {
        override val type get() = UiBindingType.NUMBER
    }

    data class Bool(val value: Boolean) : UiValue {
        override val type get() = UiBindingType.BOOLEAN
    }

    data class Text(val value: String) : UiValue {
        override val type get() = UiBindingType.STRING
    }

    /** The items of a `list` binding: one record per item, keyed by the declared field names. */
    data class Rows(val items: List<Map<String, UiValue>>) : UiValue {
        override val type get() = UiBindingType.LIST
    }
}

/** The authored id behind a runtime id of an expanded list row (`title#3` is `title`). */
fun uiBaseId(id: String): String = id.substringBefore('#')

/** The synthetic binding name the expansion gives field [field] of row [row] of list binding [list]. */
fun uiRowBinding(list: String, row: Int, field: String): String = "$list#$row.$field"

/** The values a host publishes for one update; immutable once built. */
class UiSnapshot(values: Map<String, UiValue> = emptyMap()) {
    private val values = values.toMap()
    override fun equals(other: Any?) = other is UiSnapshot && other.values == values
    override fun hashCode() = values.hashCode()
    operator fun get(name: String): UiValue? = values[name]
    fun toMap(): Map<String, UiValue> = values
}

enum class UiBindingIssue {
    MISSING_VALUE, INCOMPATIBLE_VALUE, INVALID_MAXIMUM, READ_ONLY_TARGET, INCOMPATIBLE_TARGET, UNKNOWN_BINDING,
    UNSUPPORTED_PROPERTY, WRITE_REJECTED, ITEM_FIELD, ROW_LIMIT,
}

/** A diagnostic with data only; [binding] and [widgetId] are set when they apply. */
data class UiBindingDiagnostic(
    val code: UiBindingIssue,
    val binding: String? = null,
    val widgetId: String? = null,
    val property: String? = null
)

/** What a widget property accepts: the [types] a binding may have, and whether the control writes it back. */
data class UiBindingSlot(val types: Set<UiBindingType>, val writable: Boolean)

private val ANY_TYPE = setOf(UiBindingType.NUMBER, UiBindingType.BOOLEAN, UiBindingType.STRING)
private val ONLY_BOOLEAN = setOf(UiBindingType.BOOLEAN)
private val ONLY_NUMBER = setOf(UiBindingType.NUMBER)

/** The slot of [property] on a [type] widget, or null when the property is not bindable there. */
fun bindingSlot(type: UiWidgetType, property: String): UiBindingSlot? = when (property) {
    "visible", "enabled" -> UiBindingSlot(ONLY_BOOLEAN, false)
    "style" -> UiBindingSlot(setOf(UiBindingType.STRING), false)
    "items" -> if (type == UiWidgetType.LIST) UiBindingSlot(setOf(UiBindingType.LIST), false) else null
    "text" -> when (type) {
        UiWidgetType.LABEL, UiWidgetType.BUTTON, UiWidgetType.CHECKBOX -> UiBindingSlot(ANY_TYPE, false)
        UiWidgetType.TEXT_FIELD -> UiBindingSlot(setOf(UiBindingType.STRING), true)
        else -> null
    }

    "value" -> when (type) {
        UiWidgetType.PROGRESS_BAR -> UiBindingSlot(ONLY_NUMBER, false)
        UiWidgetType.SLIDER -> UiBindingSlot(ONLY_NUMBER, true)
        else -> null
    }

    "maximum" -> if (type == UiWidgetType.PROGRESS_BAR) UiBindingSlot(ONLY_NUMBER, false) else null
    "checked" -> if (type == UiWidgetType.CHECKBOX) UiBindingSlot(ONLY_BOOLEAN, true) else null
    else -> null
}

/**
 * The static result of checking every widget binding of a collection: diagnostics, the widgets whose writable target is
 * unusable (disabled) and the properties whose binding is ignored (the literal property value applies).
 */
class UiBindingPlan internal constructor(
    val diagnostics: List<UiBindingDiagnostic>,
    private val disabled: Set<String>,
    private val ignored: Set<Pair<String, String>>,
) {
    fun isDisabled(widgetId: String) = uiBaseId(widgetId) in disabled
    fun isIgnored(widgetId: String, property: String) = (uiBaseId(widgetId) to property) in ignored

    /** The writable binding of the widget (slider value, checkbox checked, text field text), or null. */
    fun writeTarget(collection: UiCollection, widget: UiWidget): UiBinding? {
        if (widget.id in disabled) return null
        val property = writableProperty(widget.type) ?: return null
        return widget.bindings[property]?.let(collection::binding)
    }
}

/** Checks every widget binding of [collection], list rows included; pure. */
fun planBindings(collection: UiCollection): UiBindingPlan {
    val diagnostics = mutableListOf<UiBindingDiagnostic>()
    val disabled = HashSet<String>()
    val ignored = HashSet<Pair<String, String>>()
    fun walk(widget: UiWidget, item: Map<String, UiBindingType>?) {
        val type = widget.type
        if (type != null) {
            for ((property, name) in widget.bindings) {
                val slot = bindingSlot(type, property)
                // a row widget names a field of its list's record; anything else is a declared binding
                val binding = if (name.startsWith(UI_ITEM_PREFIX)) item?.get(name.removePrefix(UI_ITEM_PREFIX))
                    ?.let { UiBinding(name, it, UiBindingAccess.READ_ONLY, null) }
                else collection.binding(name)

                fun problem(code: UiBindingIssue) {
                    diagnostics += UiBindingDiagnostic(code, name, widget.id, property)
                    ignored += widget.id to property
                    if (slot?.writable == true) disabled += widget.id
                }
                when {
                    slot == null -> problem(UiBindingIssue.UNSUPPORTED_PROPERTY)
                    binding == null -> problem(UiBindingIssue.UNKNOWN_BINDING)
                    binding.type !in slot.types -> problem(UiBindingIssue.INCOMPATIBLE_TARGET)
                    slot.writable && binding.access != UiBindingAccess.WRITE -> problem(UiBindingIssue.READ_ONLY_TARGET)
                }
            }
        }
        widget.children.forEach { walk(it, item) }
        if (type == UiWidgetType.LIST) {
            val record = widget.bindings["items"]?.let(collection::binding)
                ?.takeIf { it.type == UiBindingType.LIST }?.fields?.associate { it.name to it.type }.orEmpty()
            widget.template?.let { walk(it, record) }
            widget.empty?.let { walk(it, null) }
        }
    }
    collection.screens.forEach { walk(it.root, null) }
    return UiBindingPlan(diagnostics, disabled, ignored)
}

internal fun writableProperty(type: UiWidgetType?): String? = when (type) {
    UiWidgetType.SLIDER -> "value"
    UiWidgetType.CHECKBOX -> "checked"
    UiWidgetType.TEXT_FIELD -> "text"
    else -> null
}

/**
 * The values widgets show for one published [UiSnapshot]: a missing or wrongly typed value falls back to the declared
 * sample (or the type's zero) with a diagnostic. Pure; building a view never writes.
 */
class UiBindingView(
    private val collection: UiCollection,
    private val plan: UiBindingPlan,
    private val values: Map<String, UiValue>,
    val diagnostics: List<UiBindingDiagnostic>
) {
    fun value(binding: String): UiValue? = values[binding]

    /** The items of list binding [binding]; empty when it is not a list. */
    fun rows(binding: String): List<Map<String, UiValue>> = (values[binding] as? UiValue.Rows)?.items.orEmpty()

    /** The style name of [widget]: the bound string when it has a usable `style` binding, else its authored `style`. */
    fun styleName(widget: UiWidget): String? =
        (bound(widget, "style") as? UiValue.Text)?.value?.takeIf { it.isNotEmpty() } ?: widget.style

    private fun bound(widget: UiWidget, property: String): UiValue? =
        if (plan.isIgnored(widget.id, property)) null else widget.bindings[property]?.let(values::get)

    /** Displayed text: the bound value (numbers with `precision`), else the literal `text`, wrapped in `prefix` / `suffix`. */
    fun text(widget: UiWidget): String {
        val value = bound(widget, "text")
        val body = if (value != null) formatValue(
            value,
            widget.properties["precision"]?.takeIf { it.isIntegralNumber }?.intValue()
        )
        else widget.properties["text"]?.takeIf { it.isTextual }?.textValue().orEmpty()
        return (widget.properties["prefix"]?.asText().orEmpty()) + body + (widget.properties["suffix"]?.asText()
            .orEmpty())
    }

    fun number(widget: UiWidget, property: String, default: Double): Double =
        (bound(widget, property) as? UiValue.Num)?.value ?: widget.properties[property]?.takeIf { it.isNumber }
            ?.doubleValue() ?: default

    fun flag(widget: UiWidget, property: String, default: Boolean): Boolean =
        (bound(widget, property) as? UiValue.Bool)?.value ?: widget.properties[property]?.takeIf { it.isBoolean }
            ?.booleanValue() ?: default

    fun visible(widget: UiWidget) = flag(widget, "visible", true)

    fun enabled(widget: UiWidget) = flag(widget, "enabled", true) && !plan.isDisabled(widget.id)

    /** Progress in 0..1: clamped; a maximum that is not positive shows empty and is diagnosed by [UiBindingView.diagnostics]. */
    fun progress(widget: UiWidget): Float {
        val max = number(widget, "maximum", 1.0)
        if (!(max > 0.0) || !max.isFinite()) return 0f
        val value = number(widget, "value", 0.0)
        return if (value.isFinite()) (value / max).coerceIn(0.0, 1.0).toFloat() else 0f
    }

    /** The slider range from its binding's `min` / `max` (0..1 when undeclared). */
    fun range(widget: UiWidget): ClosedFloatingPointRange<Double> {
        val binding = widget.bindings["value"]?.let(collection::binding)
        return (binding?.min ?: 0.0)..(binding?.max ?: 1.0)
    }
}

/** Builds [UiBindingView]s of one collection; the plan is computed once. */
class UiBindingResolver(private val collection: UiCollection) {
    val plan: UiBindingPlan = planBindings(collection)

    fun view(snapshot: UiSnapshot): UiBindingView {
        val values = HashMap<String, UiValue>()
        val diagnostics = plan.diagnostics.toMutableList()
        for (binding in collection.bindings) {
            val published = snapshot[binding.name]
            when {
                published == null -> diagnostics += UiBindingDiagnostic(UiBindingIssue.MISSING_VALUE, binding.name)
                published.type != binding.type || (published is UiValue.Num && !published.value.isFinite()) ->
                    diagnostics += UiBindingDiagnostic(UiBindingIssue.INCOMPATIBLE_VALUE, binding.name)

                else -> {
                    values[binding.name] = published; continue
                }
            }
            values[binding.name] = fallback(binding)
        }
        for (binding in collection.bindings) if (binding.type == UiBindingType.LIST) {
            val rows =
                sanitize(binding, values[binding.name] as? UiValue.Rows ?: UiValue.Rows(emptyList()), diagnostics)
            values[binding.name] = rows
            // each field of each row is also a value of its own, which the expanded row widgets are bound to
            rows.items.take(MAX_LIST_ROWS)
                .forEachIndexed { i, row -> for ((field, v) in row) values[uiRowBinding(binding.name, i, field)] = v }
        }
        val view = UiBindingView(collection, plan, values, diagnostics)
        val stack = ArrayDeque<UiWidget>()
        collection.screens.forEach { stack.addLast(it.root) }
        while (stack.isNotEmpty()) {
            val widget = stack.removeLast()
            widget.children.forEach(stack::addLast)
            widget.template?.let(stack::addLast); widget.empty?.let(stack::addLast)
            if (widget.type == UiWidgetType.PROGRESS_BAR && ("maximum" in widget.bindings || "maximum" in widget.properties)) {
                val max = view.number(widget, "maximum", 1.0)
                if (!(max > 0.0) || !max.isFinite()) diagnostics += UiBindingDiagnostic(
                    UiBindingIssue.INVALID_MAXIMUM,
                    widget.bindings["maximum"],
                    widget.id,
                    "maximum"
                )
            }
        }
        return view
    }

    private fun fallback(binding: UiBinding): UiValue =
        binding.sample?.let { scalar(binding.type, it, binding.fields) } ?: zero(binding.type)

    private fun zero(type: UiBindingType): UiValue = when (type) {
        UiBindingType.NUMBER -> UiValue.Num(0.0)
        UiBindingType.BOOLEAN -> UiValue.Bool(false)
        UiBindingType.STRING -> UiValue.Text("")
        UiBindingType.LIST -> UiValue.Rows(emptyList())
    }

    private fun scalar(
        type: UiBindingType,
        node: com.fasterxml.jackson.databind.JsonNode,
        fields: List<UiRecordField>
    ): UiValue = when (type) {
        UiBindingType.NUMBER -> UiValue.Num(node.doubleValue())
        UiBindingType.BOOLEAN -> UiValue.Bool(node.booleanValue())
        UiBindingType.STRING -> UiValue.Text(node.textValue())
        UiBindingType.LIST -> UiValue.Rows(node.map { row ->
            fields.mapNotNull { f -> row.get(f.name)?.let { f.name to scalar(f.type, it, emptyList()) } }.toMap()
        })
    }

    /** The rows with every declared field present and typed: a bad or missing field is its type's zero, reported once per row and field. */
    private fun sanitize(
        binding: UiBinding,
        rows: UiValue.Rows,
        diagnostics: MutableList<UiBindingDiagnostic>
    ): UiValue.Rows =
        UiValue.Rows(rows.items.mapIndexed { i, row ->
            binding.fields.associate { f ->
                val v = row[f.name]
                if (v != null && v.type == f.type && !(v is UiValue.Num && !v.value.isFinite())) f.name to v
                else {
                    diagnostics += UiBindingDiagnostic(UiBindingIssue.ITEM_FIELD, binding.name, null, "$i.${f.name}")
                    f.name to zero(f.type)
                }
            }
        })
}

/** The most rows of a list that get their own values; the widget limit applies to the expanded widgets as well. */
const val MAX_LIST_ROWS = UI_MAX_WIDGETS

/** Text for a value: numbers with [precision] decimals, or without a trailing `.0` when integral. */
fun formatValue(value: UiValue, precision: Int? = null): String = when (value) {
    is UiValue.Bool -> value.value.toString()
    is UiValue.Text -> value.value
    is UiValue.Rows -> ""
    is UiValue.Num ->
        if (precision != null && precision >= 0) String.format(Locale.ROOT, "%.${precision}f", value.value)
        else if (value.value == floor(value.value) && abs(value.value) < 1e15) value.value.toLong().toString()
        else value.value.toString()
}
