/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditResult
import net.nevinsky.abyssus.lib.core.editor.ui.UiTree
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.state.bindingSlot

/** Why a row's current value is unusable; the panel shows [UiFieldWarning] next to it. */
enum class UiFieldWarning { UNKNOWN_BINDING, INCOMPATIBLE_TARGET, READ_ONLY_TARGET, MISSING_SCREEN, MISSING_RESOURCE, MISSING_STYLE }

/** One editable row of the properties panel; [key] names it for the edit that applies it. */
sealed interface UiField {
    val key: String

    /** A number that may be blank (unset); [minimum] is the smallest allowed value, if any. */
    data class Number(override val key: String, val value: Double?, val minimum: Double?) : UiField

    /** A choice among [options]; [value] may be null (unset) and is shown even when it is not among the options. */
    data class Choice(override val key: String, val value: String?, val options: List<String>, val warning: UiFieldWarning? = null) : UiField

    data class Text(override val key: String, val value: String) : UiField

    /** A widget property bound to a declared binding; [warning] explains an unusable choice (read-only target, unknown binding...). */
    data class Binding(override val key: String, val binding: String?, val options: List<String>, val warning: UiFieldWarning?) : UiField

    /** The `click` action of a button; [warning] is set when it names a screen that does not exist. */
    data class Action(override val key: String, val action: UiAction?, val screens: List<String>, val warning: UiFieldWarning?) : UiField
}

/** The editable rows of one widget, grouped as the panel shows them. */
data class UiFieldGroups(
    val layout: List<UiField>,
    val content: List<UiField>,
    val style: List<UiField>,
    val bindings: List<UiField>,
    val actions: List<UiField>,
) {
    val all: List<UiField> get() = layout + content + style + bindings + actions
}

private val BINDABLE = listOf("items", "text", "value", "maximum", "checked", "visible", "enabled", "style")

/**
 * Which rows the properties panel shows for a widget, from the widget's kind and its parent's layout policy: a panel's
 * children are placed by anchor and offsets, a row's or column's by order and `grow`, containers have padding and
 * spacing, a bindable property lists only compatible bindings. Pure; the panel only renders it.
 */
class UiPropertiesModel {
    fun fieldsFor(collection: UiCollection, widgetId: String): UiFieldGroups? {
        val tree = UiTree(collection)
        val widget = tree.widget(widgetId) ?: return null
        val parent = tree.parentOf(widgetId)
        val l = widget.layout
        val layout = buildList {
            val free = parent == null || parent.type == UiWidgetType.PANEL
            if (free) {
                add(UiField.Choice("layout.anchor", l.anchor?.json, UiAnchor.entries.map { it.json }))
                add(UiField.Choice("layout.pivot", l.pivot?.json, UiAnchor.entries.map { it.json }))
                add(UiField.Number("layout.x", l.x.toDouble(), null))
                add(UiField.Number("layout.y", l.y.toDouble(), null))
            } else {
                add(UiField.Number("layout.grow", l.grow.toDouble(), 0.0))
            }
            add(UiField.Number("layout.width", l.width?.toDouble(), 0.0))
            add(UiField.Number("layout.height", l.height?.toDouble(), 0.0))
            if (tree.isContainer(widget) || widget.type == UiWidgetType.LIST) {
                add(UiField.Number("layout.padding", l.padding.toDouble(), 0.0))
                if (widget.type != UiWidgetType.PANEL) {
                    add(UiField.Number("layout.spacing", l.spacing.toDouble(), 0.0))
                    add(UiField.Choice("layout.align", l.align.json, UiAlign.entries.map { it.json }))
                }
            }
        }
        val content = buildList {
            when (widget.type) {
                UiWidgetType.LABEL -> {
                    add(UiField.Text("property.text", widget.properties["text"]?.asText().orEmpty()))
                    add(UiField.Text("property.prefix", widget.properties["prefix"]?.asText().orEmpty()))
                    add(UiField.Text("property.suffix", widget.properties["suffix"]?.asText().orEmpty()))
                    add(UiField.Number("property.precision", widget.properties["precision"]?.takeIf { it.isNumber }?.doubleValue(), 0.0))
                }
                UiWidgetType.LIST -> add(UiField.Number("property.maxRows", widget.properties["maxRows"]?.takeIf { it.isIntegralNumber }?.doubleValue(), 0.0))
                UiWidgetType.BUTTON, UiWidgetType.CHECKBOX, UiWidgetType.TEXT_FIELD ->
                    add(UiField.Text("property.text", widget.properties["text"]?.asText().orEmpty()))
                UiWidgetType.IMAGE -> {
                    val images = collection.resources.filter { it.kind == UiResourceKind.IMAGE }.map { it.id }
                    val chosen = widget.properties["image"]?.takeIf { it.isTextual }?.textValue()
                    add(UiField.Choice("property.image", chosen, images, if (chosen != null && chosen !in images) UiFieldWarning.MISSING_RESOURCE else null))
                }
                UiWidgetType.PROGRESS_BAR -> {
                    add(UiField.Number("property.value", widget.properties["value"]?.takeIf { it.isNumber }?.doubleValue(), null))
                    add(UiField.Number("property.maximum", widget.properties["maximum"]?.takeIf { it.isNumber }?.doubleValue(), null))
                }
                else -> Unit
            }
        }
        val style = if (widget.type == null) emptyList() else
            listOf(UiField.Choice("style", widget.style, collection.styles.map { it.name },
                if (widget.style != null && collection.styles.none { it.name == widget.style }) UiFieldWarning.MISSING_STYLE else null))
        val type = widget.type
        // inside a list's row the fields of its record can be bound as `item.<field>`
        val record = rowRecord(tree, collection, widgetId)
        val bindings = if (type == null) emptyList() else BINDABLE.mapNotNull { property ->
            val slot = bindingSlot(type, property) ?: return@mapNotNull null
            val current = widget.bindings[property]
            val declared = current?.let { if (it.startsWith(UI_ITEM_PREFIX)) record[it.removePrefix(UI_ITEM_PREFIX)]?.let { t -> UiBinding(it, t, UiBindingAccess.READ_ONLY, null) } else collection.binding(it) }
            val options = collection.bindings.filter { it.type in slot.types }.map { it.name } +
                record.filter { it.value in slot.types }.keys.map { UI_ITEM_PREFIX + it }
            val warning = when {
                current == null -> null
                declared == null -> UiFieldWarning.UNKNOWN_BINDING
                declared.type !in slot.types -> UiFieldWarning.INCOMPATIBLE_TARGET
                slot.writable && declared.access != UiBindingAccess.WRITE -> UiFieldWarning.READ_ONLY_TARGET
                else -> null
            }
            UiField.Binding("binding.$property", current, options, warning)
        }
        // buttons, and panels / rows / columns (a whole list row can be one click target) take a click action
        val actions = if (type !in setOf(UiWidgetType.BUTTON, UiWidgetType.PANEL, UiWidgetType.ROW, UiWidgetType.COLUMN)) emptyList() else {
            val action = widget.actions["click"]
            val local = action?.takeIf { it.assetName == null && it.screenId != null && (it.type == UiActionType.OPEN_SCREEN || it.type == UiActionType.SHOW_OVERLAY) }
            val warning = if (local != null && collection.screen(local.screenId!!) == null) UiFieldWarning.MISSING_SCREEN else null
            listOf(UiField.Action("action.click", action, collection.screens.map { it.id }, warning))
        }
        return UiFieldGroups(layout, content, style, bindings, actions)
    }

    /** The fields of the record of the list whose row [widgetId] is in; empty outside a row. */
    private fun rowRecord(tree: UiTree, collection: UiCollection, widgetId: String): Map<String, UiBindingType> {
        var child = tree.widget(widgetId) ?: return emptyMap()
        var parent = tree.parentOf(child.id)
        while (parent != null) {
            if (parent.type == UiWidgetType.LIST && parent.template?.id == child.id) {
                return parent.bindings["items"]?.let(collection::binding)?.takeIf { it.type == UiBindingType.LIST }?.fields?.associate { it.name to it.type }.orEmpty()
            }
            child = parent
            parent = tree.parentOf(child.id)
        }
        return emptyMap()
    }

    // ---- turning a field change into the document edit ----

    /** The tree edit for setting [key] of [widgetId] to text [input] (blank unsets), or null when [input] is not a valid value. */
    fun edit(key: String, widgetId: String, input: String): ((net.nevinsky.abyssus.lib.core.editor.ui.UiDocumentEdits, JsonNode) -> UiEditResult)? {
        val blank = input.isBlank()
        return when {
            key.startsWith("layout.") -> {
                val field = key.removePrefix("layout.")
                val value: Any? = when (field) {
                    "anchor", "pivot", "align" -> input.takeUnless { blank }
                    else -> if (blank) null else input.trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
                }
                // an empty `align` is the default `start`: store nothing rather than the default
                val stored = if (field == "align" && value == "start") null else value
                return { e, m -> e.setLayout(m, widgetId, field, stored) }
            }
            key.startsWith("property.") -> {
                val name = key.removePrefix("property.")
                val node: JsonNode? = when {
                    blank -> null
                    name == "precision" || name == "value" || name == "maximum" || name == "maxRows" -> input.trim().toDoubleOrNull()?.takeIf(Double::isFinite)?.let { number(it) } ?: return null
                    else -> JsonNodeFactory.instance.textNode(input)
                }
                return { e, m -> e.setProperty(m, widgetId, name, node) }
            }
            key == "style" -> { e, m -> e.setStyle(m, widgetId, input.takeUnless { blank }) }
            key.startsWith("binding.") -> { e, m -> e.setBinding(m, widgetId, key.removePrefix("binding."), input.takeUnless { blank }) }
            else -> null
        }
    }

    private fun number(v: Double): JsonNode =
        if (v == Math.rint(v) && Math.abs(v) < 1e9) JsonNodeFactory.instance.numberNode(v.toInt()) else JsonNodeFactory.instance.numberNode(v)

    /** The edit that sets (or with a null [type] removes) the `click` action. [target] is a screen id, or an action name for `emitAction`. */
    fun actionEdit(widgetId: String, type: UiActionType?, target: String?, assetName: String? = null): (net.nevinsky.abyssus.lib.core.editor.ui.UiDocumentEdits, JsonNode) -> UiEditResult {
        val node = type?.let {
            JsonNodeFactory.instance.objectNode().apply {
                put("type", it.json)
                if (assetName != null) put("assetName", assetName)
                when (it) {
                    UiActionType.OPEN_SCREEN, UiActionType.SHOW_OVERLAY -> target?.let { t -> put("screenId", t) }
                    UiActionType.CLOSE_OVERLAY -> target?.let { t -> put("screenId", t) }
                    UiActionType.EMIT_ACTION -> target?.let { t -> put("name", t) }
                    UiActionType.BACK -> Unit
                }
            }
        }
        return { e, m -> e.setAction(m, widgetId, "click", node) }
    }
}
