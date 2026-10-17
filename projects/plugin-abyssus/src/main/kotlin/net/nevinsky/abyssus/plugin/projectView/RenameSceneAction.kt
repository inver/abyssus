/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** Right-click "Rename Scene..." on any scene row; edits the scene file's `name`. */
open class RenameSceneAction : AbyssusTreeAction<VirtualFile>() {
    override fun targetOf(node: Any?): VirtualFile? = viewableSceneFile(node)

    override fun perform(project: Project, target: VirtualFile, e: AnActionEvent) {
        val file = target
        val current = sceneNameOf(project, file)
        val name = Messages.showInputDialog(
            project,
            AbyssusBundle.message("renameScenePrompt"),
            AbyssusBundle.message("renameSceneTitle"),
            null,
            current,
            null,
        )?.trim()
        if (!name.isNullOrEmpty() && name != current) renameScene(project, file, name)
    }
}
