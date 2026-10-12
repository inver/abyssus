/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.state

import net.nevinsky.abyssus.lib.core.ui.model.UI_ITEM_PREFIX
import net.nevinsky.abyssus.lib.core.ui.model.UI_MAX_WIDGETS
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType

/** A screen's widget tree with every list replaced by its rows, ready for layout, painting and input. */
class UiListExpansion(val root: UiWidget, val diagnostics: List<UiBindingDiagnostic>)

/**
 * Replaces each `list` widget of [root] by a copy whose children are its rows: the template repeated per item of the
 * list's `items` binding (at most `properties.maxRows`), or the `empty` widget when there are none. Row widgets get the
 * runtime id `<id>#<row>` and their `item.<field>` bindings are rewritten to the per-row values the [view] holds, so
 * nothing downstream needs to know about items. Rows stop when the expanded tree would exceed [budget] widgets. Pure.
 */
fun expandLists(root: UiWidget, view: UiBindingView, budget: Int = UI_MAX_WIDGETS): UiListExpansion {
    val diagnostics = mutableListOf<UiBindingDiagnostic>()
    var count = 0

    fun size(w: UiWidget): Int = 1 + w.children.sumOf(::size)

    /** One copy of the template for row [row]: new ids, rewritten item bindings, nested lists left for [expand]. */
    fun rowCopy(w: UiWidget, list: String, row: Int): UiWidget = w.copy(
        id = "${w.id}#$row",
        bindings = w.bindings.mapValues { (_, name) -> if (name.startsWith(UI_ITEM_PREFIX)) uiRowBinding(list, row, name.removePrefix(UI_ITEM_PREFIX)) else name },
        // a named game action from a row says which row: `<name>#<row>`
        actions = w.actions.mapValues { (_, a) -> if (a.type == UiActionType.EMIT_ACTION && a.name != null) a.copy(name = "${a.name}#$row") else a },
        children = w.children.map { rowCopy(it, list, row) },
        template = w.template?.let { rowCopy(it, list, row) },
        empty = w.empty?.let { rowCopy(it, list, row) },
    )

    fun expand(w: UiWidget): UiWidget {
        count++
        if (w.type != UiWidgetType.LIST) return w.copy(children = w.children.map(::expand))
        val name = w.bindings["items"]
        val items = name?.let(view::rows).orEmpty()
        val template = w.template
        val maxRows = w.properties["maxRows"]?.takeIf { it.isIntegralNumber && it.intValue() >= 0 }?.intValue() ?: Int.MAX_VALUE
        val rows = mutableListOf<UiWidget>()
        if (items.isEmpty() || template == null) {
            w.empty?.let { rows += expand(it) }
        } else {
            val per = size(template)
            for (i in 0 until minOf(items.size, maxRows)) {
                if (count + per > budget) { diagnostics += UiBindingDiagnostic(UiBindingIssue.ROW_LIMIT, name, w.id); break }
                rows += expand(rowCopy(template, name!!, i))
            }
        }
        return w.copy(children = rows, template = null, empty = null)
    }

    return UiListExpansion(expand(root), diagnostics)
}
