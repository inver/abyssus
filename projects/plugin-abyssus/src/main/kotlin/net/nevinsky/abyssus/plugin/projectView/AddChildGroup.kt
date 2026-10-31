/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

/** Popup choices share update/dispatch; each group retains its admission and creation rules. */
abstract class AddChildGroup<T>(title: String) : DefaultActionGroup(title, true), DumbAware {
    protected abstract fun canAdd(target: T): Boolean
    protected abstract fun addTarget(target: T)

    protected fun addChild(target: T, label: String, literalName: Boolean = false) {
        add(childAction(target, label, literalName))
    }

    /** The choice that adds [target]; a group that lists its choices when it opens builds them with this. */
    protected fun childAction(target: T, label: String, literalName: Boolean = false): AnAction {
        val child = object : AnAction(label), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = canAdd(target)
            }

            override fun actionPerformed(e: AnActionEvent) = addTarget(target)
        }
        if (literalName) child.templatePresentation.setText(label, false)
        return child
    }
}
