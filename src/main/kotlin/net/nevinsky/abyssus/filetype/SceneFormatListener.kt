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

package net.nevinsky.abyssus.filetype

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.dto.ProjectLayout

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
        val pretty = SceneJson.pretty(document.text) ?: return
        if (pretty == document.text) return
        WriteCommandAction.runWriteCommandAction(project, "Format Scene JSON", null, { document.setText(pretty) })
    }
}
