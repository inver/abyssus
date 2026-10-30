/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import net.nevinsky.abyssus.lib.core.editor.ui.UiTree
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection

enum class DropPosition { BEFORE, INTO, AFTER }

/** Where a widget dragged in the tree ends: [parentId] and the child [index] there (null: the end). */
data class TreeDrop(val parentId: String, val index: Int?)

/**
 * Turns "drop [draggedId] [position] [overId]" in the widget tree into the parent and index to move it to, or null when
 * that is not allowed: the root cannot move, a widget cannot go onto itself or into its own subtree, and only containers
 * take `INTO`. The index counts the target's other children, as `reparentWidget` does. Pure.
 */
fun treeDrop(collection: UiCollection, draggedId: String, overId: String, position: DropPosition): TreeDrop? {
    val tree = UiTree(collection)
    val dragged = tree.widget(draggedId) ?: return null
    val over = tree.widget(overId) ?: return null
    if (tree.parentOf(draggedId) == null || draggedId == overId) return null
    val drop = when (position) {
        DropPosition.INTO -> if (tree.isContainer(over)) TreeDrop(over.id, null) else return null
        else -> {
            val parent = tree.parentOf(overId) ?: return null
            val others = parent.children.filter { it.id != draggedId }
            val at = others.indexOfFirst { it.id == overId }
            TreeDrop(parent.id, if (position == DropPosition.BEFORE) at else at + 1)
        }
    }
    return drop.takeUnless { tree.isInside(it.parentId, dragged.id) }
}
