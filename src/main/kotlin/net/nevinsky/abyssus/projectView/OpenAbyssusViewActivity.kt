/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Switches the Project tool window to the Abyssus view on startup, but only when the IDE is started
 * with `-Dabyssus.openView=true` (set by the `runIde` task); regular users keep their own choice.
 */
class OpenAbyssusViewActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!System.getProperty(OPEN_VIEW_PROPERTY).toBoolean()) return
        withContext(Dispatchers.EDT) {
            ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.show()
            ProjectView.getInstance(project).changeView(AbyssusProjectViewPane.ID)
        }
    }

    companion object {
        const val OPEN_VIEW_PROPERTY = "abyssus.openView"
    }
}
