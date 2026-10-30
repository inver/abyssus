/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.refresh

/**
 * What a tree showed before a rebuild: the selected and the expanded rows, each named by its identity path (the file plus
 * the entity id or property path, `/`-separated), never by the text of its label, which a rename or a toggle changes.
 */
class NavigationState(selected: Collection<String>, expanded: Collection<String>) {
    val selected: List<String> = selected.distinct()
    val expanded: List<String> = expanded.distinct()
}

/** What to do to the rebuilt tree: expand [expand] parents first, then select [select]. Both hold only rows that still exist. */
data class NavigationRestore(val expand: List<String>, val select: List<String>) {
    val selectionLost: Boolean get() = select.isEmpty()
}

/**
 * Maps a [NavigationState] onto the rows [present] after a rebuild. A row that is gone is dropped from both lists, so a
 * removed selection stays cleared instead of falling onto a neighbour; a row appears once however often it was captured;
 * expansion is ordered shallow to deep so each parent opens before its child.
 */
fun restoreNavigation(state: NavigationState, present: Set<String>): NavigationRestore = NavigationRestore(
    expand = state.expanded.filter { it in present }.sortedBy { it.count { c -> c == '/' } },
    select = state.selected.filter { it in present },
)
