/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.SceneFileEditorProvider

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Opens [file] and selects the Abyssus scene view tab (the text editor stays available as the other tab). */
fun openSceneView(project: Project, file: VirtualFile) {
    val manager = FileEditorManager.getInstance(project)
    manager.openFile(file, true)
    manager.setSelectedEditor(file, SceneFileEditorProvider.EDITOR_TYPE_ID)
}
