/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import net.nevinsky.abyssus.lib.core.editor.document.setBooleanMember
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.lib.core.editor.components.SetEnabledAction
import net.nevinsky.abyssus.plugin.AbyssusBundle

/**
 * Enables or disables an entity or a component of [scene] as one undoable command: `enabled: false` is written on that
 * entry only, and enabling removes the key. The tree eye and the Properties `Enabled` check box both call it.
 * Returns whether anything was written.
 */
fun setEnabled(project: Project, scene: VirtualFile, target: EnabledTarget, enabled: Boolean): Boolean {
    val action = SetEnabledAction()
    return editSceneJson(project, scene, AbyssusBundle.message("commandSetEnabled")) { root ->
        action.apply(root, target, enabled)
    }
}

/**
 * Sets the scene setting flag [key] (`fogEnabled`, `ambientLightEnabled`, `skyboxEnabled`) of [scene] as one undoable
 * command, adding the key when the file has none. Only that key changes. Returns whether anything was written.
 */
fun setSceneToggle(project: Project, scene: VirtualFile, key: String, on: Boolean): Boolean =
    editSceneJson(project, scene, AbyssusBundle.message("commandToggleEnabled")) { root ->
        setBooleanMember(root, key, on)
    }
