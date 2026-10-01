package net.nevinsky.abyssus.filetype

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Formats a scene's JSON whenever it is shown in the text editor (on open, and when switching from the scene view). */
class SceneFormatListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (isScene(file) && source.getSelectedEditor(file) is TextEditor) format(source.project, file)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (isScene(file) && event.newEditor is TextEditor) format(event.manager.project, file)
    }

    private fun isScene(file: VirtualFile) = !file.isDirectory && file.extension == "scene"

    private fun format(project: Project, file: VirtualFile) {
        val document = FileDocumentManager.getInstance().getDocument(file)?.takeIf { it.isWritable } ?: return
        val pretty = SceneJson.pretty(document.text) ?: return
        if (pretty == document.text) return
        WriteCommandAction.runWriteCommandAction(project, "Format Scene JSON", null, { document.setText(pretty) })
    }
}
