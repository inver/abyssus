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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.AbyssusBundle

/** Right-click "Rename Scene..." on a scene listed under a project; edits the scene file's `name`. */
class RenameSceneAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    private fun selectedScene(e: AnActionEvent): DtoEntry? {
        val project = e.project ?: return null
        val pane = ProjectView.getInstance(project).currentProjectViewPane?.takeIf { it.id == AbyssusProjectViewPane.ID } ?: return null
        val node = TreeUtil.getUserObject(pane.selectedPath?.lastPathComponent) as? DtoEntryNode ?: return null
        return node.value.takeIf { sceneFileOf(it) != null }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selectedScene(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val entry = selectedScene(e) ?: return
        val file = sceneFileOf(entry) ?: return
        val name = Messages.showInputDialog(
            project,
            AbyssusBundle.message("renameScenePrompt"),
            AbyssusBundle.message("renameSceneTitle"),
            null,
            sceneName(entry),
            null,
        )?.trim()
        if (!name.isNullOrEmpty() && name != sceneName(entry)) renameScene(project, file, name)
    }
}
