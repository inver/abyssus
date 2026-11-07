/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyEvent
import javax.swing.JTree
import javax.swing.KeyStroke

/**
 * The platform Delete action on an entity or modeled component row of the Abyssus view: removes it like Remove Entity /
 * Remove Component, as one undoable edit. Other rows keep the platform's provider.
 */
internal class RowDeleteProvider(private val project: Project, private val target: ComponentTarget) : DeleteProvider {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun canDeleteElement(dataContext: DataContext) = target.file.isValid

    override fun deleteElement(dataContext: DataContext) = removeRow(project, target)
}

/** The row [tree] has selected when Delete or Backspace may remove it, else null. */
internal fun removableSelectionOf(tree: JTree): ComponentTarget? =
    removableRowOf(TreeUtil.getUserObject(tree.leadSelectionPath?.lastPathComponent))

/**
 * Backspace on [tree] removes the selected entity or component too; the default keymaps bind it to Delete only on
 * macOS. It stays with speed search while that is typing.
 */
internal fun installBackspaceRemoval(tree: JTree, project: Project) {
    object : DumbAwareAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled =
                SpeedSearchSupply.getSupply(tree)?.isPopupActive != true && removableSelectionOf(tree) != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            removableSelectionOf(tree)?.let { removeRow(project, it) }
        }
    }.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0)), tree)
}
