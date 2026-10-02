/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
