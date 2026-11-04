/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.editor.util.Util
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAlign
import net.nevinsky.abyssus.lib.core.ui.model.UiAnchor
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingType
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType

enum class UiEditError {
    NOT_EDITABLE,
    UNKNOWN_SCREEN,
    UNKNOWN_WIDGET,
    UNKNOWN_NAME,
    ROOT_WIDGET,
    NOT_A_CONTAINER,
    HIERARCHY_CYCLE,
    BAD_VALUE,
    UNKNOWN_TYPE,
    DUPLICATE_ID,
    INVALID_RESULT,
    STALE,
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
 * Pure edits of a UI asset's `meta.json` document (`additional.ui`): screens, the widget hierarchy, properties, layout,
 * styles, bindings and actions. Every edit leaves the document untouched when it rejects, keeps the members the model
 * does not name, and appends new members. Ids are stable: a widget keeps its id through rename-like edits, reparenting
 * and reordering, and a duplicate gets fresh ids for every widget it contains. No IO, no IDE.
 */
class UiDocumentEdits(private val json: JsonProcessor) {
    // ---- locating ----

    private class Spot(
        val widget: UiWidgetDoc,
        val parent: UiWidgetDoc?,
        val siblings: MutableList<UiWidgetDoc>?,
        val screen: UiScreenDoc,
    )

    private fun ui(meta: UiMetaDoc): UiBodyDoc? = meta.additional?.ui

    private fun screenDoc(ui: UiBodyDoc, id: String): UiScreenDoc? = ui.screens.orEmpty().firstOrNull { it.id == id }

    private fun spot(ui: UiBodyDoc, id: String): Spot? {
        for (screen in ui.screens.orEmpty()) {
            val root = screen.root ?: continue
            val stack = ArrayDeque<Triple<UiWidgetDoc, UiWidgetDoc?, MutableList<UiWidgetDoc>?>>()
            stack.addLast(Triple(root, null, null))
            while (stack.isNotEmpty()) {
                val (widget, parent, siblings) = stack.removeLast()
                if (widget.id == id) {
                    return Spot(widget, parent, siblings, screen)
                }
                widget.children?.let { list -> list.forEach { stack.addLast(Triple(it, widget, list)) } }
                // a list's template and empty state are subtrees too; like a screen's root they have no sibling list to
                // move in
                listOfNotNull(widget.template, widget.empty).forEach { stack.addLast(Triple(it, widget, null)) }
            }
        }
        return null
    }

    private fun forEachWidget(ui: UiBodyDoc, action: (UiWidgetDoc) -> Unit) {
        val stack = ArrayDeque<UiWidgetDoc>()
        ui.screens.orEmpty().forEach { it.root?.let(stack::addLast) }
        while (stack.isNotEmpty()) {
            val widget = stack.removeLast()
            action(widget)
            widget.inside().forEach(stack::addLast)
        }
    }

    private fun allWidgetIds(ui: UiBodyDoc): Set<String> {
        val ids = HashSet<String>()
        forEachWidget(ui) { w -> w.id?.let { ids += it } }
        return ids
    }

    private fun uniqueId(base: String, taken: Set<String>): String = Util.uniqueName(base) { it in taken }

    private fun isDescendant(candidate: UiWidgetDoc, ancestor: UiWidgetDoc): Boolean {
        if (candidate === ancestor) {
            return true
        }
        return ancestor.inside().any { isDescendant(candidate, it) }
    }

    private fun containerType(widget: UiWidgetDoc): Boolean =
        UiWidgetType.entries.firstOrNull { it.json == widget.type }?.container == true

    /** A whole number below 1e9 as an `Int`, any other as a `Double`, so a stored value never gains a fraction. */
    private fun number(value: Double): Number =
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            value.toInt()
        } else {
            value
        }

    private fun widgetOf(id: String, type: String, text: String? = null): UiWidgetDoc =
        UiWidgetDoc().apply {
            this.id = id
            this.type = type
            if (text != null) {
                properties = linkedMapOf("text" to text)
            }
        }

    // ---- screens ----

    /** Adds a screen with an empty panel root; [kind] is `screen` or `overlay`. */
    fun addScreen(meta: UiMetaDoc, name: String, kind: String = "screen"): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (kind != "screen" && kind != "overlay") {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "kind")
        }
        if (name.isBlank()) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "name")
        }
        val taken = ui.screens.orEmpty().mapNotNull { it.id }.toSet()
        val id =
            uniqueId(name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "screen" }, taken)
        val widgets = allWidgetIds(ui)
        val screen =
            UiScreenDoc().apply {
                this.id = id
                this.name = name.trim()
                if (kind == "overlay") {
                    this.kind = kind
                }
                root = widgetOf(uniqueId("$id-root", widgets), "panel")
            }
        val list = ui.screens ?: mutableListOf<UiScreenDoc>().also { ui.screens = it }
        list.add(screen)
        if (kind == "screen" && ui.entryScreen == null && list.size == 1) {
            ui.entryScreen = id
        }
        return UiEditResult.Added(id)
    }

    fun renameScreen(meta: UiMetaDoc, screenId: String, name: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val screen = screenDoc(ui, screenId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        if (name.isBlank()) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "name")
        }
        if (screen.name == name) {
            return UiEditResult.Unchanged
        }
        screen.name = name
        return UiEditResult.Changed
    }

    /** Removes a screen; when it was the entry screen the first remaining `screen` kind (or none) takes over. */
    fun deleteScreen(meta: UiMetaDoc, screenId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val list = ui.screens.orEmpty()
        val index = list.indexOfFirst { it.id == screenId }
        if (index < 0) {
            return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        }
        ui.screens!!.removeAt(index)
        if (ui.entryScreen == screenId) {
            ui.entryScreen = ui.screens!!.firstOrNull { (it.kind ?: "screen") == "screen" }?.id
        }
        return UiEditResult.Changed
    }

    fun setEntryScreen(meta: UiMetaDoc, screenId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val screen = screenDoc(ui, screenId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        if ((screen.kind ?: "screen") != "screen") {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "kind")
        }
        if (ui.entryScreen == screenId) {
            return UiEditResult.Unchanged
        }
        ui.entryScreen = screenId
        return UiEditResult.Changed
    }

    // ---- widgets ----

    /**
     * Adds a widget of [type] under container [parentId] at [index] (the end by default), with the type's starting
     * text.
     */
    fun addWidget(meta: UiMetaDoc, parentId: String, type: String, index: Int? = null): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val kind =
            UiWidgetType.entries.firstOrNull { it.json == type }
                ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_TYPE, type)
        val parent = spot(ui, parentId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, parentId)
        if (!containerType(parent.widget)) {
            return UiEditResult.Rejected(UiEditError.NOT_A_CONTAINER, parentId)
        }
        val id = uniqueId(type, allWidgetIds(ui))
        val widget =
            when (kind) {
                UiWidgetType.LABEL -> widgetOf(id, type, "Label")
                UiWidgetType.BUTTON -> widgetOf(id, type, "Button")
                UiWidgetType.CHECKBOX -> widgetOf(id, type, "Checkbox")
                // a list needs a row to repeat: a row container the author fills with row widgets
                UiWidgetType.LIST ->
                    widgetOf(id, type).apply {
                        template = widgetOf(uniqueId("$type-row", allWidgetIds(ui) + id), "row")
                    }
                else -> widgetOf(id, type)
            }
        val list = parent.widget.children ?: mutableListOf<UiWidgetDoc>().also { parent.widget.children = it }
        list.add((index ?: list.size).coerceIn(0, list.size), widget)
        return UiEditResult.Added(id)
    }

    /**
     * Deep-copies [widgetId] right after itself; every widget inside gets a new unique id, unknown members are copied
     * as they are.
     */
    fun duplicateWidget(meta: UiMetaDoc, widgetId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val spot = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = spot.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        val taken = allWidgetIds(ui).toMutableSet()
        val copy = json.parse(json.toString(spot.widget), UiWidgetDoc::class.java)
        fun renew(node: UiWidgetDoc) {
            val fresh = uniqueId(node.id.orEmpty(), taken)
            taken += fresh
            node.id = fresh
            node.inside().forEach(::renew)
        }
        renew(copy)
        siblings.add(siblings.indexOfFirst { it === spot.widget } + 1, copy)
        return UiEditResult.Added(copy.id.orEmpty())
    }

    /**
     * Moves [widgetId] under [newParentId] at [index] (end by default). The widget is converted to the destination's
     * layout policy in the same edit: free placement (`anchor`, `pivot`, `x`, `y`) is dropped under a row or column,
     * and `grow` is dropped under a panel.
     */
    fun reparentWidget(meta: UiMetaDoc, widgetId: String, newParentId: String, index: Int? = null): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val moving = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = moving.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        val target = spot(ui, newParentId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, newParentId)
        if (isDescendant(target.widget, moving.widget)) {
            return UiEditResult.Rejected(UiEditError.HIERARCHY_CYCLE, newParentId)
        }
        if (!containerType(target.widget)) {
            return UiEditResult.Rejected(UiEditError.NOT_A_CONTAINER, newParentId)
        }
        val before = json.toString(ui)
        siblings.removeAt(siblings.indexOfFirst { it === moving.widget })
        val list = target.widget.children ?: mutableListOf<UiWidgetDoc>().also { target.widget.children = it }
        list.add((index ?: list.size).coerceIn(0, list.size), moving.widget)
        moving.widget.layout?.let { layout ->
            val policy = target.widget.type
            if (policy == "row" || policy == "column") {
                layout.run {
                    anchor = null
                    pivot = null
                    x = null
                    y = null
                }
            } else {
                layout.grow = null
            }
            if (layout.isBlank()) {
                moving.widget.layout = null
            }
        }
        return if (json.toString(ui) == before) {
            UiEditResult.Unchanged
        } else {
            UiEditResult.Changed
        }
    }

    /** Moves [widgetId] to position [newIndex] among its siblings (clamped), counted after the widget is taken out. */
    fun reorderWidget(meta: UiMetaDoc, widgetId: String, newIndex: Int): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val spot = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = spot.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        val from = siblings.indexOfFirst { it === spot.widget }
        val to = newIndex.coerceIn(0, siblings.size - 1)
        if (from == to) {
            return UiEditResult.Unchanged
        }
        siblings.removeAt(from)
        siblings.add(to, spot.widget)
        return UiEditResult.Changed
    }

    /** Gives the list [listId] an empty state: a label that replaces the rows when the list has no items. */
    fun addListEmpty(meta: UiMetaDoc, listId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val list = spot(ui, listId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, listId)
        if (list.type != "list") {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, listId)
        }
        if (list.empty != null) {
            return UiEditResult.Unchanged
        }
        val id = uniqueId("$listId-empty", allWidgetIds(ui))
        list.empty = widgetOf(id, "label", "Nothing here yet")
        return UiEditResult.Added(id)
    }

    fun removeListEmpty(meta: UiMetaDoc, listId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val list = spot(ui, listId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, listId)
        if (list.empty == null) {
            return UiEditResult.Unchanged
        }
        list.empty = null
        return UiEditResult.Changed
    }

    /** Sets how Up and Down behave on a screen: `tab` (the default, removed from the document) or `arrows`. */
    fun setScreenNavigation(meta: UiMetaDoc, screenId: String, navigation: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val screen = screenDoc(ui, screenId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_SCREEN, screenId)
        if (navigation != "tab" && navigation != "arrows") {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "navigation")
        }
        if ((screen.navigation ?: "tab") == navigation) {
            return UiEditResult.Unchanged
        }
        screen.navigation =
            if (navigation == "tab") {
                null
            } else {
                navigation
            }
        return UiEditResult.Changed
    }

    /** Deletes a widget with its subtree; the root widget of a screen cannot be deleted (delete the screen). */
    fun deleteWidget(meta: UiMetaDoc, widgetId: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val spot = spot(ui, widgetId) ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        val siblings = spot.siblings ?: return UiEditResult.Rejected(UiEditError.ROOT_WIDGET, widgetId)
        siblings.removeAt(siblings.indexOfFirst { it === spot.widget })
        if (siblings.isEmpty() && spot.parent != null) {
            spot.parent.children = null
        }
        return UiEditResult.Changed
    }

    // ---- widget members ----

    /**
     * Sets `properties.<name>` to [value] (a string, number, boolean, list or map), or removes it when [value] is null.
     */
    fun setProperty(meta: UiMetaDoc, widgetId: String, name: String, value: Any?): UiEditResult =
        withWidget(meta, widgetId) { w -> updateMap(w.properties, { w.properties = it }, name, value) }

    /** Sets `bindings.<property>` to a binding name, or removes it when null. */
    fun setBinding(meta: UiMetaDoc, widgetId: String, property: String, binding: String?): UiEditResult =
        withWidget(meta, widgetId) { w -> updateMap(w.bindings, { w.bindings = it }, property, binding) }

    /** Sets `actions.<event>` to an action, or removes it when null. */
    fun setAction(meta: UiMetaDoc, widgetId: String, event: String, action: UiActionDoc?): UiEditResult {
        if (action != null && action.type == null) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, event)
        }
        return withWidget(meta, widgetId) { w ->
            val current = w.actions?.get(event)
            if (action != null && current != null && json.toString(current) == json.toString(action)) {
                UiEditResult.Unchanged
            } else {
                updateMap(w.actions, { w.actions = it }, event, action, sameValue = false)
            }
        }
    }

    /** Sets the named style of a widget, or removes it when null. */
    fun setStyle(meta: UiMetaDoc, widgetId: String, style: String?): UiEditResult =
        withWidget(meta, widgetId) { w ->
            if (w.style == style) {
                UiEditResult.Unchanged
            } else {
                w.style = style
                UiEditResult.Changed
            }
        }

    /**
     * Sets one `layout` field: numbers `x`, `y`, `width`, `height`, `grow`, `padding`, `spacing` (sizes, grow, padding
     * and spacing not negative, all finite), or the names `anchor`, `pivot`, `align`. A null value removes it.
     */
    fun setLayout(meta: UiMetaDoc, widgetId: String, field: String, value: Any?): UiEditResult {
        val stored: Any? =
            when (field) {
                "x",
                "y",
                "width",
                "height",
                "grow",
                "padding",
                "spacing" ->
                    value?.let {
                        val d =
                            (it as? Number)?.toDouble()?.takeIf(Double::isFinite)
                                ?: return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
                        if (field != "x" && field != "y" && d < 0) {
                            return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
                        }
                        number(d)
                    }
                "anchor",
                "pivot" ->
                    value?.let { v ->
                        UiAnchor.entries.firstOrNull { it.json == v }?.json
                            ?: return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
                    }
                "align" ->
                    value?.let { v ->
                        UiAlign.entries.firstOrNull { it.json == v }?.json
                            ?: return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
                    }
                else -> return UiEditResult.Rejected(UiEditError.BAD_VALUE, field)
            }
        return withWidget(meta, widgetId) { w ->
            val layout = w.layout
            if (readLayout(layout, field) == stored) {
                UiEditResult.Unchanged
            } else {
                val target = layout ?: UiLayoutDoc().also { w.layout = it }
                writeLayout(target, field, stored)
                if (target.isBlank()) {
                    w.layout = null
                }
                UiEditResult.Changed
            }
        }
    }

    private fun readLayout(layout: UiLayoutDoc?, field: String): Any? = layout?.let {
        when (field) {
            "x" -> it.x
            "y" -> it.y
            "width" -> it.width
            "height" -> it.height
            "grow" -> it.grow
            "padding" -> it.padding
            "spacing" -> it.spacing
            "anchor" -> it.anchor
            "pivot" -> it.pivot
            else -> it.align
        }
    }

    private fun writeLayout(layout: UiLayoutDoc, field: String, value: Any?) {
        when (field) {
            "x" -> layout.x = value
            "y" -> layout.y = value
            "width" -> layout.width = value
            "height" -> layout.height = value
            "grow" -> layout.grow = value
            "padding" -> layout.padding = value
            "spacing" -> layout.spacing = value
            "anchor" -> layout.anchor = value as String?
            "pivot" -> layout.pivot = value as String?
            else -> layout.align = value as String?
        }
    }

    private fun withWidget(meta: UiMetaDoc, widgetId: String, edit: (UiWidgetDoc) -> UiEditResult): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val widget = spot(ui, widgetId)?.widget ?: return UiEditResult.Rejected(UiEditError.UNKNOWN_WIDGET, widgetId)
        return edit(widget)
    }

    /** Sets or removes [key] in an optional member map; an emptied map is dropped. */
    private fun <V> updateMap(
        current: MutableMap<String, V>?,
        assign: (MutableMap<String, V>?) -> Unit,
        key: String,
        value: V?,
        sameValue: Boolean = true,
    ): UiEditResult {
        if (value == null) {
            if (current == null || !current.containsKey(key)) {
                return UiEditResult.Unchanged
            }
            current.remove(key)
            if (current.isEmpty()) {
                assign(null)
            }
            return UiEditResult.Changed
        }
        if (sameValue && current != null && current.containsKey(key) && current[key] == value) {
            return UiEditResult.Unchanged
        }
        (current ?: LinkedHashMap<String, V>().also(assign))[key] = value
        return UiEditResult.Changed
    }

    // ---- resources ----

    /**
     * Declares a resource: [kind] is `image` or `bitmapFont`, [path] is relative to the asset folder (files are copied
     * there by the caller). The id must be new.
     */
    fun addResource(meta: UiMetaDoc, id: String, kind: String, path: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (id.isBlank() || path.isBlank() || (kind != "image" && kind != "bitmapFont")) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, id)
        }
        if (ui.resources.orEmpty().any { it.id == id }) {
            return UiEditResult.Rejected(UiEditError.DUPLICATE_ID, id)
        }
        val list = ui.resources ?: mutableListOf<UiResourceDoc>().also { ui.resources = it }
        list.add(
            UiResourceDoc().apply {
                this.id = id
                this.kind = kind
                this.path = path
            }
        )
        return UiEditResult.Added(id)
    }

    /** Removes a resource declaration; the file stays and widgets naming it fall back to a placeholder. */
    fun deleteResource(meta: UiMetaDoc, id: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        val at = ui.resources.orEmpty().indexOfFirst { it.id == id }
        if (at < 0) {
            return UiEditResult.Rejected(UiEditError.UNKNOWN_NAME, id)
        }
        ui.resources!!.removeAt(at)
        if (ui.resources!!.isEmpty()) {
            ui.resources = null
        }
        return UiEditResult.Changed
    }

    // ---- styles and bindings ----

    /** Adds or replaces the style called [name]; [values] is the style object without its name. */
    fun upsertStyle(meta: UiMetaDoc, name: String, values: Map<String, Any?>): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (name.isBlank()) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, name)
        }
        val entry =
            UiStyleDoc().apply {
                this.name = name
                values.forEach { (k, v) ->
                    if (k != "name") {
                        putExtra(k, v)
                    }
                }
            }
        return upsert(ui.styles, { ui.styles = it }, { it.name == name }, entry)
    }

    /** Deletes a style and clears the widgets' `style` that named it, as one edit. */
    fun deleteStyle(meta: UiMetaDoc, name: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (!remove(ui.styles, { ui.styles = it }, { it.name == name })) {
            return UiEditResult.Rejected(UiEditError.UNKNOWN_NAME, name)
        }
        forEachWidget(ui) {
            if (it.style == name) {
                it.style = null
            }
        }
        return UiEditResult.Changed
    }

    /**
     * Adds or replaces the binding called [name] of [type]; replacing keeps references. [extra] holds access, sample
     * and constraints.
     */
    fun upsertBinding(meta: UiMetaDoc, name: String, type: String, extra: UiBindingDoc? = null): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (name.isBlank()) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "name")
        }
        if (UiBindingType.entries.none { it.json == type }) {
            return UiEditResult.Rejected(UiEditError.BAD_VALUE, "type")
        }
        val entry =
            UiBindingDoc().apply {
                this.name = name
                this.type = type
                extra?.let {
                    access = it.access
                    sample = it.sample
                    min = it.min
                    max = it.max
                    step = it.step
                    maxLength = it.maxLength
                    fields = it.fields
                }
            }
        return upsert(ui.bindings, { ui.bindings = it }, { it.name == name }, entry)
    }

    /** Deletes a binding and removes every widget binding that named it, as one edit. */
    fun deleteBinding(meta: UiMetaDoc, name: String): UiEditResult {
        val ui = ui(meta) ?: return UiEditResult.Rejected(UiEditError.NOT_EDITABLE)
        if (!remove(ui.bindings, { ui.bindings = it }, { it.name == name })) {
            return UiEditResult.Rejected(UiEditError.UNKNOWN_NAME, name)
        }
        forEachWidget(ui) { widget ->
            val bindings = widget.bindings ?: return@forEachWidget
            bindings.entries.removeAll { it.value == name }
            if (bindings.isEmpty()) {
                widget.bindings = null
            }
        }
        return UiEditResult.Changed
    }

    /** Replaces the entry [match] finds with [entry], or appends it; unchanged when the same entry is already there. */
    private fun <T : Any> upsert(
        current: MutableList<T>?,
        assign: (MutableList<T>) -> Unit,
        match: (T) -> Boolean,
        entry: T,
    ): UiEditResult {
        val list = current ?: mutableListOf<T>().also(assign)
        val at = list.indexOfFirst(match)
        if (at >= 0) {
            if (json.toString(list[at]) == json.toString(entry)) {
                return UiEditResult.Unchanged
            }
            list[at] = entry
        } else {
            list.add(entry)
        }
        return UiEditResult.Changed
    }

    /** Removes the entry [match] finds; the list is dropped when its last entry goes. False when there is none. */
    private fun <T : Any> remove(
        current: MutableList<T>?,
        assign: (MutableList<T>?) -> Unit,
        match: (T) -> Boolean,
    ): Boolean {
        val at = current?.indexOfFirst(match) ?: -1
        if (at < 0) {
            return false
        }
        current!!.removeAt(at)
        if (current.isEmpty()) {
            assign(null)
        }
        return true
    }
}
