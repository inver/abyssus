/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil

/** Selects through the asynchronous tree after showing its pane. Asset creation also requests a rebuild. EDT. */
fun selectInAbyssusView(project: Project, visitor: TreeVisitor, refresh: Boolean = false) {
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.show()
    val view = ProjectView.getInstance(project)
    view.changeView(AbyssusProjectViewPane.ID)
    val pane = view.getProjectViewPaneById(AbyssusProjectViewPane.ID) ?: return
    if (refresh) pane.updateFromRoot(true)
    val tree = pane.tree ?: return
    TreeUtil.promiseSelect(tree, visitor)
}
