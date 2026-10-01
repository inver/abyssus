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
