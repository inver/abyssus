package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot

/**
 * Harness setup that runs between scenarios and inside a lifecycle (editor reopen, project reopen). It closes and
 * reloads; it never applies a plugin edit, so it cannot stand in for the operation under test.
 */
class IdeReset(private val robot: RemoteRobot) {
    private fun js(body: String) = "importPackage(com.intellij.openapi.project);\n" +
        "importPackage(com.intellij.openapi.vfs);\n" +
        "importPackage(com.intellij.openapi.fileEditor);\n" + body

    private fun quote(text: String) = "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"

    /** Closes every open editor tab, which also disposes their scene views and what runs in them. */
    fun closeAllEditors() {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0]; var m = FileEditorManager.getInstance(p);" +
                "var fs = m.getOpenFiles(); for (var i = 0; i < fs.length; i++) m.closeFile(fs[i])"),
            runInEdt = true,
        )
    }

    /**
     * Drops every unsaved in-memory document text in favor of the disk, so a buffer from the previous scenario can
     * neither be saved over the restored files nor leave edit history that targets the next scenario.
     */
    fun discardUnsavedDocuments() {
        robot.runJs(
            js("var m = FileDocumentManager.getInstance(); var ds = m.getUnsavedDocuments();" +
                "for (var i = 0; i < ds.length; i++) m.reloadFromDisk(ds[i])"),
            runInEdt = true,
        )
    }

    /**
     * Drops the IDE's undo and redo history of the project and the global one. Reloading a document from disk does not
     * (the history of the previous scenario stayed available), and an Undo in the next scenario must not reach it.
     */
    fun dropUndoHistory() {
        val app = "com.intellij.openapi.application.ApplicationManager.getApplication()"
        robot.runJs(
            js("importPackage(com.intellij.openapi.command.undo);" +
                "var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "$app.invokeAndWait(new java.lang.Runnable({ run: function () {" +
                "UndoManager.getInstance(p).dropHistoryInTests(); UndoManager.getGlobalInstance().dropHistoryInTests(); } }))"),
            runInEdt = false,
        )
    }

    /**
     * Leaves the Abyssus pane and comes back by pane id, so its tree is built anew. Used between scenarios, where a mouse
     * click on the view selector is not reliable right after editors were closed.
     */
    fun reshowAbyssusView() {
        val app = "com.intellij.openapi.application.ApplicationManager.getApplication()"
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0]; var v = com.intellij.ide.projectView.ProjectView.getInstance(p);" +
                "$app.invokeAndWait(new java.lang.Runnable({ run: function () { v.changeView('ProjectPane'); v.changeView('Abyssus'); } }))"),
            runInEdt = false,
        )
    }

    /** Paths of the documents with unsaved text. */
    fun unsavedDocuments(): List<String> = robot.callJs<String>(
        js("var m = FileDocumentManager.getInstance(); var ds = m.getUnsavedDocuments(); var out = [];" +
            "for (var i = 0; i < ds.length; i++) { var f = m.getFile(ds[i]); out.push(f == null ? '?' : String(f.getPath())); } out.join('\\n')"),
        runInEdt = true,
    ).lines().filter { it.isNotEmpty() }

    /** Closes the editor of [absolutePath] and opens it again as text: an editor reopen. Save first (Ctrl+S): nothing here writes. */
    fun reopenTextEditor(absolutePath: String) {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
                "var m = FileEditorManager.getInstance(p); m.closeFile(vf);" +
                "m.openTextEditor(new com.intellij.openapi.fileEditor.OpenFileDescriptor(p, vf), true)"),
            runInEdt = true,
        )
    }

    /** Puts the project's Show FPS view preference back to its default (off), so no scenario inherits it. */
    fun clearShowFps() {
        robot.runJs(js("com.intellij.ide.util.PropertiesComponent.getInstance(ProjectManager.getInstance().getOpenProjects()[0]).setValue('net.nevinsky.abyssus.sceneView.showFps', false, false)"), runInEdt = true)
    }

    /**
     * Closes the open project and opens the same folder again in the same IDE process (a project reopen, not an IDE
     * restart): documents, editors and edit history are new afterwards. Save first (Ctrl+S): nothing here writes. The IDE shows the project again when this returns.
     */
    fun reopenProject() {
        val app = "com.intellij.openapi.application.ApplicationManager.getApplication()"
        // closing needs the write-intent lock, which invokeAndWait runs with; opening blocks, so it stays off the EDT
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0]; var path = String(p.getBasePath());" +
                "$app.invokeAndWait(new java.lang.Runnable({ run: function () { ProjectManager.getInstance().closeAndDispose(p); } }));" +
                "com.intellij.ide.impl.ProjectUtil.openOrImport(java.nio.file.Paths.get(path), com.intellij.ide.impl.OpenProjectTask.build())"),
            runInEdt = false,
        )
    }

    /**
     * Whether the IDE's Undo is available for the text editor of [absolutePath] (the history that editor would undo).
     * Opens the file as text to ask and closes it again.
     */
    fun undoAvailableInTextEditor(absolutePath: String): Boolean {
        val answer = robot.callJs<String>(
            js("importPackage(com.intellij.openapi.command.undo);" +
                "var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
                "var m = FileEditorManager.getInstance(p);" +
                "m.openTextEditor(new com.intellij.openapi.fileEditor.OpenFileDescriptor(p, vf), false);" +
                "var te = m.getSelectedEditor(vf);" +
                "var r = String(UndoManager.getInstance(p).isUndoAvailable(te)); m.closeFile(vf); r"),
            runInEdt = true,
        )
        return answer == "true"
    }

    /**
     * The name and description of the Undo (or, with [redo], Redo) the IDE offers for the text editor of [absolutePath],
     * as `name | description`, or an empty text when none is available. Opens the file as text to ask and closes it again.
     */
    fun historyText(absolutePath: String, redo: Boolean = false): String = robot.callJs<String>(
        js("importPackage(com.intellij.openapi.command.undo);" +
            "var p = ProjectManager.getInstance().getOpenProjects()[0];" +
            "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
            "var m = FileEditorManager.getInstance(p);" +
            "m.openTextEditor(new com.intellij.openapi.fileEditor.OpenFileDescriptor(p, vf), false);" +
            "var te = m.getSelectedEditor(vf); var um = UndoManager.getInstance(p);" +
            "var r = ${if (redo) "um.isRedoAvailable(te) ? um.getRedoActionNameAndDescription(te) : null" else "um.isUndoAvailable(te) ? um.getUndoActionNameAndDescription(te) : null"};" +
            "m.closeFile(vf); r == null ? '' : String(r.first) + ' | ' + String(r.second)"),
        runInEdt = true,
    )
}
