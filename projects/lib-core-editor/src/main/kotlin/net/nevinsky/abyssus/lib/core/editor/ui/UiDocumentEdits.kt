/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.ui.model.UiAnchor
import net.nevinsky.abyssus.lib.core.ui.model.UiAlign
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingType
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType

enum class UiEditError {
    NOT_EDITABLE, UNKNOWN_SCREEN, UNKNOWN_WIDGET, UNKNOWN_NAME, ROOT_WIDGET, NOT_A_CONTAINER, HIERARCHY_CYCLE,
    BAD_VALUE, UNKNOWN_TYPE, DUPLICATE_ID, INVALID_RESULT, STALE,
}

/** What an edit of the `additional.ui` tree did. The tree is only touched for [Changed] and [Added]. */
sealed interface UiEditResult {
    data object Changed : UiEditResult

    /** A screen or widget was created (or duplicated) under [id]. */
    data class Added(val id: String) : UiEditResult

    data object Unchanged : UiEditResult

    /** Refused with a code; the tree is untouched. [subject] names the screen, widget or value at fault. */
    data class Rejected(val error: UiEditError, val subject: String? = null) : UiEditResult
}

/**
 * Pure edits of a UI asset's `meta.json` tree (`additional.ui`): screens, the widget hierarchy, properties, layout,
 * styles, bindings and actions. Every edit leaves the tree untouched when it rejects, keeps unknown members, key order and
 * the number text it did not write, and appends new members. Ids are stable: a widget keeps its id through rename-like
 * edits, reparenting and reordering, and a duplicate gets fresh ids for every widget it contains. No IO, no IDE.
 */
/** The members of a `list` widget that hold a widget of their own. */
private val SUBTREES = listOf("template", "empty")

class UiDocumentEdits {
    // ---- locating ----

    private class Spot(val widget: ObjectNode, val parent: ObjectNode?, val siblings: ArrayNode?, val screen: ObjectNode)

    private fun ui(meta: JsonNode): ObjectNode? = meta.path("additional").path("ui") as? ObjectNode

    private fun screens(ui: ObjectNode): ArrayNode = (ui.get("screens") as? ArrayNode) ?: ui.putArray("screens")

    private fun screenNode(ui: ObjectNode, id: String): ObjectNode? =
        screens(ui).firstOrNull { it.path("id").asText() == id } as? ObjectNode

    private fun children(widget: ObjectNode): ArrayNode? = widget.get("children") as? ArrayNode

    private fun spot(ui: ObjectNode, id: String): Spot? {
        for (screen in screens(ui)) {
            if (screen !is ObjectNode) continue
            val root = screen.get("root") as? ObjectNode ?: continue
            val stack = ArrayDeque<Triple<ObjectNode, ObjectNode?, ArrayNode?>>()
            stack.addLast(Triple(root, null, null))
            while (stack.isNotEmpty()) {
                val (widget, parent, siblings) = stack.removeLast()
                if (widget.path("id").asText() == id) return Spot(widget, parent, siblings, screen)
                children(widget)?.let { list -> list.forEach { if (it is ObjectNode) stack.addLast(Triple(it, widget, list)) } }
                // a list's template and empty state are subtrees too; like a screen's root they have no sibling list to move in
                SUBTREES.forEach { f -> (widget.get(f) as? ObjectNode)?.let { stack.addLast(Triple(it, widget, null)) } }
            }
        }
        return null
    }

    private fun allWidgetIds(ui: ObjectNode): Set<String> {
        val ids = HashSet<String>()
        val stack = ArrayDeque<JsonNode>()
        screens(ui).forEach { it.get("root")?.let(stack::addLast) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.get("id")?.takeIf { it.isTextual }?.let { ids += it.textValue() }
            node.get("children")?.forEach(stack::addLast)
            SUBTREES.forEach { f -> node.get(f)?.let(stack::addLast) }
        }
        return ids
    }

    private fun uniqueId(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    private fun isDescendant(candidate: ObjectNode, ancestor: ObjectNode): Boolean {
        if (candidate === ancestor) return true
        return children(ancestor)?.any { it is ObjectNode && isDescendant(candidate, it) } == true ||
            SUBTREES.any { f -> (ancestor.get(f) as? ObjectNode)?.let { isDescendant(candidate, it) } == true }
    }

    private fun containerType(widget: ObjectNode): Boolean =
        UiWidgetType.entries.firstOrNull { it.json == widget.path("type").asText() }?.container == true

    private fun number(value: Double): JsonNode =
        if (value == Math.rint(value) && Math.abs(value) < 1e9) IntNode.valueOf(value.toInt()) else DoubleNode.valueOf(value)

    // ---- screens ----

    /** Adds a screen with an empty panel root; [kind] is `screen` or `overlay`. */
    fun addScreen(meta: JsonNode, name: String, kind: String = "screen"): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (kind != "screen" && kind != "overlay") return UiEditResult.Rejected(UiEditError.BAD_VALUE, "kind")
        if (name.isBlank()) return UiEditResult.Rejected(UiEditError.BAD_VALUE, "name")
        val taken = screens(ui).mapNotNull { it.get("id")?.textValue() }.toSet()
        val id = uniqueId(name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "screen" }, taken)
        val widgets = allWidgetIds(ui)
        val screen = JsonNodeFactory.instance.objectNode().apply {
            put("id", id); put("name", name.trim()); if (kind == "overlay") put("kind", kind)
            putObject("root").apply { put("id", uniqueId("$id-root", widgets)); put("type", "panel") }
        }
        screens(ui).add(screen)
        if (kind == "screen" && ui.get("entryScreen") == null && screens(ui).size() == 1) ui.put("entryScreen", id)
        return UiEditResult.Added(id)
    }

    fun renameScreen(meta: JsonNode, screenId: String, name: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val screen = screenNode(ui, screenId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        if (name.isBlank()) return UiEditResult.Rejected(UiEditError.BAD_VALUE, "name")
        if (screen.path("name").asText() == name) return UiEditResult.Unchanged
        screen.put("name", name)
        return UiEditResult.Changed
    }

    /** Removes a screen; when it was the entry screen the first remaining `screen` kind (or none) takes over. */
    fun deleteScreen(meta: JsonNode, screenId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val list = screens(ui)
        val index = list.indexOfFirst { it.path("id").asText() == screenId }
        if (index < 0) return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        list.remove(index)
        if (ui.path("entryScreen").asText() == screenId) {
            val next = list.firstOrNull { it.path("kind").asText("screen") == "screen" }?.get("id")?.textValue()
            if (next != null) ui.put("entryScreen", next) else ui.remove("entryScreen")
        }
        return UiEditResult.Changed
    }

    fun setEntryScreen(meta: JsonNode, screenId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val screen = screenNode(ui, screenId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        if (screen.path("kind").asText("screen") != "screen") return UiEditResult.Rejected(UiEditError.BAD_VALUE, "kind")
        if (ui.path("entryScreen").asText() == screenId) return UiEditResult.Unchanged
        ui.put("entryScreen", screenId)
        return UiEditResult.Changed
    }

    // ---- widgets ----

    /** Adds a widget of [type] under container [parentId] at [index] (the end by default), with the type's starting text. */
    fun addWidget(meta: JsonNode, parentId: String, type: String, index: Int? = null): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val kind = UiWidgetType.entries.firstOrNull { it.json == type } ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_TYPE, type)
        val parent = spot(ui, parentId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, parentId)
        if (!containerType(parent.widget)) return UiEditResult.Rejected(UiEditError.NOT_A_CONTAINER, parentId)
        val id = uniqueId(type, allWidgetIds(ui))
        val widget = JsonNodeFactory.instance.objectNode().apply {
            put("id", id); put("type", type)
            when (kind) {
                UiWidgetType.LABEL -> putObject("properties").put("text", "Label")
                UiWidgetType.BUTTON -> putObject("properties").put("text", "Button")
                UiWidgetType.CHECKBOX -> putObject("properties").put("text", "Checkbox")
                // a list needs a row to repeat: a row container the author fills with row widgets
                UiWidgetType.LIST -> putObject("template").apply { put("id", uniqueId("$type-row", allWidgetIds(ui) + id)); put("type", "row") }
                else -> Unit
            }
        }
        val list = children(parent.widget) ?: parent.widget.putArray("children")
        list.insert((index ?: list.size()).coerceIn(0, list.size()), widget)
        return UiEditResult.Added(id)
    }

    /** Deep-copies [widgetId] right after itself; every widget inside gets a new unique id, unknown members are copied as they are. */
    fun duplicateWidget(meta: JsonNode, widgetId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val spot = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = spot.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        val taken = allWidgetIds(ui).toMutableSet()
        val copy = spot.widget.deepCopy()
        fun renew(node: ObjectNode) {
            val fresh = uniqueId(node.path("id").asText(), taken)
            taken += fresh
            node.put("id", fresh)
            children(node)?.forEach { if (it is ObjectNode) renew(it) }
            SUBTREES.forEach { f -> (node.get(f) as? ObjectNode)?.let(::renew) }
        }
        renew(copy)
        siblings.insert(siblings.indexOf(spot.widget) + 1, copy)
        return UiEditResult.Added(copy.path("id").asText())
    }

    /**
     * Moves [widgetId] under [newParentId] at [index] (end by default). The widget is converted to the destination's layout
     * policy in the same edit: free placement (`anchor`, `pivot`, `x`, `y`) is dropped under a row or column, and `grow` is
     * dropped under a panel.
     */
    fun reparentWidget(meta: JsonNode, widgetId: String, newParentId: String, index: Int? = null): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val moving = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = moving.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        val target = spot(ui, newParentId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, newParentId)
        if (isDescendant(target.widget, moving.widget)) return UiEditResult.Rejected(UiEditError.HIERARCHY_CYCLE, newParentId)
        if (!containerType(target.widget)) return UiEditResult.Rejected(UiEditError.NOT_A_CONTAINER, newParentId)
        val before = ui.toString()
        siblings.remove(siblings.indexOf(moving.widget))
        val list = children(target.widget) ?: target.widget.putArray("children")
        val at = (index ?: list.size()).coerceIn(0, list.size())
        list.insert(at, moving.widget)
        val layout = moving.widget.get("layout") as? ObjectNode
        if (layout != null) {
            val policy = target.widget.path("type").asText()
            if (policy == "row" || policy == "column") listOf("anchor", "pivot", "x", "y").forEach(layout::remove) else layout.remove("grow")
            if (layout.isEmpty) moving.widget.remove("layout")
        }
        return if (ui.toString() == before) UiEditResult.Unchanged else UiEditResult.Changed
    }

    /** Moves [widgetId] to position [newIndex] among its siblings (clamped), counted after the widget is taken out. */
    fun reorderWidget(meta: JsonNode, widgetId: String, newIndex: Int): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val spot = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = spot.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        val from = siblings.indexOf(spot.widget)
        val to = newIndex.coerceIn(0, siblings.size() - 1)
        if (from == to) return UiEditResult.Unchanged
        siblings.remove(from)
        siblings.insert(to, spot.widget)
        return UiEditResult.Changed
    }

    /** Gives the list [listId] an empty state: a label that replaces the rows when the list has no items. */
    fun addListEmpty(meta: JsonNode, listId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val list = spot(ui, listId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, listId)
        if (list.path("type").asText() != "list") return UiEditResult.Rejected(UiEditError.BAD_VALUE, listId)
        if (list.has("empty")) return UiEditResult.Unchanged
        val id = uniqueId("$listId-empty", allWidgetIds(ui))
        list.putObject("empty").apply { put("id", id); put("type", "label"); putObject("properties").put("text", "Nothing here yet") }
        return UiEditResult.Added(id)
    }

    fun removeListEmpty(meta: JsonNode, listId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val list = spot(ui, listId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, listId)
        return if (list.remove("empty") != null) UiEditResult.Changed else UiEditResult.Unchanged
    }

    /** Sets how Up and Down behave on a screen: `tab` (the default, removed from the document) or `arrows`. */
    fun setScreenNavigation(meta: JsonNode, screenId: String, navigation: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val screen = screenNode(ui, screenId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        if (navigation != "tab" && navigation != "arrows") return UiEditResult.Rejected(UiEditError.BAD_VALUE, "navigation")
        val current = screen.path("navigation").asText("tab")
        if (current == navigation) return UiEditResult.Unchanged
        if (navigation == "tab") screen.remove("navigation") else screen.put("navigation", navigation)
        return UiEditResult.Changed
    }

    /** Deletes a widget with its subtree; the root widget of a screen cannot be deleted (delete the screen). */
    fun deleteWidget(meta: JsonNode, widgetId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val spot = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = spot.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        siblings.remove(siblings.indexOf(spot.widget))
        if (siblings.isEmpty && spot.parent != null) spot.parent.remove("children")
        return UiEditResult.Changed
    }

    // ---- widget members ----

    /** Sets `properties.<name>` to [value], or removes it when [value] is null. */
    fun setProperty(meta: JsonNode, widgetId: String, name: String, value: JsonNode?): UiEditResult =
        member(meta, widgetId, "properties", name, value)

    /** Sets `bindings.<property>` to a binding name, or removes it when null. */
    fun setBinding(meta: JsonNode, widgetId: String, property: String, binding: String?): UiEditResult =
        member(meta, widgetId, "bindings", property, binding?.let { JsonNodeFactory.instance.textNode(it) })

    /** Sets `actions.<event>` to an action object, or removes it when null. */
    fun setAction(meta: JsonNode, widgetId: String, event: String, action: JsonNode?): UiEditResult {
        if (action != null && (!action.isObject || !action.path("type").isTextual)) return UiEditResult.Rejected(UiEditError.BAD_VALUE, event)
        return member(meta, widgetId, "actions", event, action)
    }

    /** Sets the named style of a widget, or removes it when null. */
    fun setStyle(meta: JsonNode, widgetId: String, style: String?): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val widget = spot(ui, widgetId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        if (style == null) return if (widget.remove("style") != null) UiEditResult.Changed else UiEditResult.Unchanged
        if (widget.path("style").asText(null) == style) return UiEditResult.Unchanged
        widget.put("style", style)
        return UiEditResult.Changed
    }

    /**
     * Sets one `layout` field: numbers `x`, `y`, `width`, `height`, `grow`, `padding`, `spacing` (sizes, grow, padding and
     * spacing not negative, all finite), or the names `anchor`, `pivot`, `align`. A null value removes it.
     */
    fun setLayout(meta: JsonNode, widgetId: String, field: String, value: Any?): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val widget = spot(ui, widgetId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val node: JsonNode? = when (field) {
            "x", "y", "width", "height", "grow", "padding", "spacing" -> value?.let {
                val d = (it as? Number)?.toDouble()?.takeIf(Double::isFinite) ?: return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
                if (field != "x" && field != "y" && d < 0) return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
                number(d)
            }
            "anchor", "pivot" -> value?.let { v -> UiAnchor.entries.firstOrNull { it.json == v }?.let { JsonNodeFactory.instance.textNode(it.json) } ?: return UiEditResult.Rejected(UiEditError.BAD_VALUE, field) }
            "align" -> value?.let { v -> UiAlign.entries.firstOrNull { it.json == v }?.let { JsonNodeFactory.instance.textNode(it.json) } ?: return UiEditResult.Rejected(UiEditError.BAD_VALUE, field) }
            else -> return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
        }
        return member(widget, "layout", field, node)
    }

    private fun member(meta: JsonNode, widgetId: String, group: String, key: String, value: JsonNode?): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val widget = spot(ui, widgetId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        return member(widget, group, key, value)
    }

    private fun member(widget: ObjectNode, group: String, key: String, value: JsonNode?): UiEditResult {
        val existing = widget.get(group) as? ObjectNode
        if (value == null) {
            if (existing?.remove(key) == null) return UiEditResult.Unchanged
            if (existing.isEmpty) widget.remove(group)
            return UiEditResult.Changed
        }
        if (existing?.get(key) == value) return UiEditResult.Unchanged
        ((existing ?: widget.putObject(group))).set<JsonNode>(key, value)
        return UiEditResult.Changed
    }

    // ---- resources ----

    /**
     * Declares a resource: [kind] is `image` or `bitmapFont`, [path] is relative to the asset folder (files are copied
     * there by the caller). The id must be new.
     */
    fun addResource(meta: JsonNode, id: String, kind: String, path: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (id.isBlank() || path.isBlank() || (kind != "image" && kind != "bitmapFont")) return UiEditResult.Rejected(UiEditError.BAD_VALUE, id)
        val resources = NamedArray(ui, "resources", "id")
        if (resources.indexOf(id) >= 0) return UiEditResult.Rejected(UiEditError.DUPLICATE_ID, id)
        resources.list().addObject().apply { put("id", id); put("kind", kind); put("path", path) }
        return UiEditResult.Added(id)
    }

    /** Removes a resource declaration; the file stays and widgets naming it fall back to a placeholder. */
    fun deleteResource(meta: JsonNode, id: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (!NamedArray(ui, "resources", "id").remove(id)) return UiEditResult.Rejected(UiEditError.UNKNOWN_NAME, id)
        return UiEditResult.Changed
    }

    // ---- styles and bindings ----

    /** Adds or replaces the style called [name]; [values] is the style object without its name. */
    fun upsertStyle(meta: JsonNode, name: String, values: JsonNode): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (name.isBlank() || !values.isObject) return UiEditResult.Rejected(UiEditError.BAD_VALUE, name)
        val entry = JsonNodeFactory.instance.objectNode().apply { put("name", name); (values as ObjectNode).properties().forEach { if (it.key != "name") set<JsonNode>(it.key, it.value) } }
        return NamedArray(ui, "styles", "name").upsert(name, entry)
    }

    /** Deletes a style and clears the widgets' `style` that named it, as one edit. */
    fun deleteStyle(meta: JsonNode, name: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (!NamedArray(ui, "styles", "name").remove(name)) return UiEditResult.Rejected(UiEditError.UNKNOWN_NAME, name)
        forEachWidget(ui) { if (it.path("style").asText(null) == name) it.remove("style") }
        return UiEditResult.Changed
    }

    /** Adds or replaces the binding declared as [name]/[type]; replacing keeps references. [extra] holds access, sample and constraints. */
    fun upsertBinding(meta: JsonNode, name: String, type: String, extra: ObjectNode? = null): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (name.isBlank()) return UiEditResult.Rejected(UiEditError.BAD_VALUE, "name")
        if (UiBindingType.entries.none { it.json == type }) return UiEditResult.Rejected(UiEditError.BAD_VALUE, "type")
        val entry = JsonNodeFactory.instance.objectNode().apply { put("name", name); put("type", type); extra?.properties()?.forEach { if (it.key != "name" && it.key != "type") set<JsonNode>(it.key, it.value) } }
        return NamedArray(ui, "bindings", "name").upsert(name, entry)
    }

    /** Deletes a binding and removes every widget binding that named it, as one edit. */
    fun deleteBinding(meta: JsonNode, name: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (!NamedArray(ui, "bindings", "name").remove(name)) return UiEditResult.Rejected(UiEditError.UNKNOWN_NAME, name)
        forEachWidget(ui) { widget ->
            val bindings = widget.get("bindings") as? ObjectNode ?: return@forEachWidget
            bindings.properties().filter { it.value.asText() == name }.map { it.key }.forEach(bindings::remove)
            if (bindings.isEmpty) widget.remove("bindings")
        }
        return UiEditResult.Changed
    }

    private fun forEachWidget(ui: ObjectNode, action: (ObjectNode) -> Unit) {
        val stack = ArrayDeque<ObjectNode>()
        screens(ui).forEach { (it.get("root") as? ObjectNode)?.let(stack::addLast) }
        while (stack.isNotEmpty()) {
            val widget = stack.removeLast()
            action(widget)
            children(widget)?.forEach { if (it is ObjectNode) stack.addLast(it) }
            SUBTREES.forEach { f -> (widget.get(f) as? ObjectNode)?.let(stack::addLast) }
        }
    }

    /** The array under [key] of [ui] whose entries are named by their [idField]; it is dropped when its last entry goes. */
    private class NamedArray(private val ui: ObjectNode, private val key: String, private val idField: String) {
        /** The array, created when missing. */
        fun list(): ArrayNode = (ui.get(key) as? ArrayNode) ?: ui.putArray(key)

        fun indexOf(name: String): Int = (ui.get(key) as? ArrayNode)?.indexOfFirst { it.path(idField).asText() == name } ?: -1

        /** Replaces the entry called [name] with [entry], or appends it; unchanged when the same entry is already there. */
        fun upsert(name: String, entry: JsonNode): UiEditResult {
            val list = list()
            val at = indexOf(name)
            if (at >= 0) { if (list[at] == entry) return UiEditResult.Unchanged; list.set(at, entry) } else list.add(entry)
            return UiEditResult.Changed
        }

        /** Removes the entry called [name]; false when there is none. */
        fun remove(name: String): Boolean {
            val at = indexOf(name)
            if (at < 0) return false
            val list = ui.get(key) as ArrayNode
            list.remove(at)
            if (list.isEmpty) ui.remove(key)
            return true
        }
    }
}
