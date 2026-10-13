/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** Right-click "Rename Scene..." on a scene listed under a project; edits the scene file's `name`. */
open class RenameSceneAction : AbyssusTreeAction<DtoEntry>() {
    override fun targetOf(node: Any?): DtoEntry? = (node as? DtoEntryNode)?.value?.takeIf { sceneFileOf(it) != null }

    override fun perform(project: Project, target: DtoEntry, e: AnActionEvent) {
        val entry = target
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
