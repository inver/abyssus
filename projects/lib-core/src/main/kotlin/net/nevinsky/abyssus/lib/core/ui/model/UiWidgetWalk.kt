/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.model

/**
 * Visits [root] and everything under it, depth first, parent before children. [item] is the record a list row sees:
 * the fields of the enclosing list's `items` binding for the widgets of its template, null elsewhere (the empty state of
 * a list is not a row and sees no item). [path] names the widget the way the decoder reports it.
 */
fun UiCollection.walkScoped(
    root: UiWidget,
    path: String,
    item: Map<String, UiBindingType>? = null,
    visit: (widget: UiWidget, path: String, item: Map<String, UiBindingType>?) -> Unit,
) {
    visit(root, path, item)
    root.children.forEachIndexed { index, child -> walkScoped(child, "$path.children[$index]", item, visit) }
    if (root.type == UiWidgetType.LIST) {
        val record = root.bindings["items"]?.let(::binding)
            ?.takeIf { it.type == UiBindingType.LIST }
            ?.fields
            ?.associate { it.name to it.type }
            .orEmpty()
        root.template?.let { walkScoped(it, "$path.template", record, visit) }
        root.empty?.let { walkScoped(it, "$path.empty", null, visit) }
    }
}
