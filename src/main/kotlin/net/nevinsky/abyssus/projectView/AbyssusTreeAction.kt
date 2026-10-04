/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * A right-click action on a row of the Abyssus view. A row is a [T] target when [targetOf] says so; the action is shown
 * for such rows only, enabled when [isEnabled] agrees, and [perform]s on the row selected when it runs.
 */
abstract class AbyssusTreeAction<T : Any> : AnAction(), DumbAware {
    /** The node the action applies to; the selected row of the Abyssus view. Tests replace it. */
    internal open fun selected(e: AnActionEvent): Any? = selectedNode(e)

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    /** What [node] stands for to this action, or null when the action does not apply to it. */
    protected abstract fun targetOf(node: Any?): T?

    /** Whether the action can run on [target] now; an action that is not applicable at all returns false. */
    protected open fun isEnabled(e: AnActionEvent, target: T): Boolean = true

    protected abstract fun perform(project: Project, target: T, e: AnActionEvent)

    final override fun update(e: AnActionEvent) {
        val target = targetOf(selected(e))
        e.presentation.isVisible = target != null
        e.presentation.isEnabled = target != null && isEnabled(e, target)
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = targetOf(selected(e)) ?: return
        if (!isEnabled(e, target)) return
        perform(project, target, e)
    }
}
