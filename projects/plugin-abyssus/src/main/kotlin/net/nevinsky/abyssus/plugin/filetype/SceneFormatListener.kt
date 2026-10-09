/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson

import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.AbyssusBundle

/** Formats a scene's or project's JSON whenever it is shown in the text editor (on open, and when switching from the scene view). */
class SceneFormatListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (isAsset(file) && source.getSelectedEditor(file) is TextEditor) format(source.project, file)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (isAsset(file) && event.newEditor is TextEditor) format(event.manager.project, file)
    }

    private fun isAsset(file: VirtualFile) = ProjectLayout.isAssetFile(file)

    private fun format(project: Project, file: VirtualFile) {
        val document = FileDocumentManager.getInstance().getDocument(file)?.takeIf { it.isWritable } ?: return
        val kind = documentKind(file) ?: return
        val root = net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation { SceneJson().parse(document.text) }.getOrNull() ?: return
        if (net.nevinsky.abyssus.lib.gdx.editor.document.AbyssusDocumentFormat().validate(root, kind) != null) return
        val pretty = SceneJson().pretty(root)
        if (pretty == document.text) return
        WriteCommandAction.runWriteCommandAction(project, AbyssusBundle.message("commandFormatSceneJson"), null, { document.setText(pretty) })
    }
}
