package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot

/** Read-only questions to the IDE process, run on the EDT. Nothing here edits a document or a file. */
class IdeQueries(private val robot: RemoteRobot) {

    private fun js(body: String) = "importPackage(com.intellij.openapi.project);\n" +
        "importPackage(com.intellij.openapi.wm);\n" +
        "importPackage(com.intellij.openapi.vfs);\n" +
        "importPackage(com.intellij.openapi.fileEditor);\n" + body

    private fun quote(text: String) = "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"

    /** Base paths of the open projects. */
    fun openProjectPaths(): List<String> = robot.callJs<String>(
        js("var out = []; var ps = ProjectManager.getInstance().getOpenProjects();" +
            "for (var i = 0; i < ps.length; i++) out.push(String(ps[i].getBasePath())); out.join('\\n')"),
        runInEdt = true,
    ).lines().filter { it.isNotEmpty() }

    fun toolWindowIds(): List<String> = robot.callJs<String>(
        js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
            "var ids = ToolWindowManager.getInstance(p).getToolWindowIds(); ids.join('\\n')"),
        runInEdt = true,
    ).lines().filter { it.isNotEmpty() }

    fun isToolWindowVisible(id: String): Boolean = robot.callJs(
        js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
            "var w = ToolWindowManager.getInstance(p).getToolWindow(${quote(id)}); w != null && w.isVisible()"),
        runInEdt = true,
    )

    /** Names of files open in editors, so a scenario can assert that no scene editor opened. */
    fun openEditorFiles(): List<String> = robot.callJs<String>(
        js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
            "var fs = FileEditorManager.getInstance(p).getOpenFiles(); var out = [];" +
            "for (var i = 0; i < fs.length; i++) out.push(String(fs[i].getName())); out.join('\\n')"),
        runInEdt = true,
    ).lines().filter { it.isNotEmpty() }

    /** The IDE's in-memory text for [absolutePath], or null when no document is loaded for it. */
    fun documentText(absolutePath: String): String? = robot.callJs<String>(
        js("importPackage(com.intellij.openapi.fileEditor);" +
            "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
            "com.intellij.openapi.application.ReadAction.compute(new com.intellij.openapi.util.ThrowableComputable({ compute: function () {" +
            "var d = vf == null ? null : FileDocumentManager.getInstance().getDocument(vf);" +
            "return d == null ? '\u0000none' : String(d.getText()); } }))"),
        runInEdt = true,
    ).takeUnless { it == "\u0000none" }

    /** Refreshes [absolutePath] recursively, the way the IDE does when the window regains focus. */
    fun refresh(absolutePath: String) {
        robot.runJs(
            js("var vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(${quote(absolutePath)});" +
                "if (vf != null) vf.refresh(false, true)"),
            runInEdt = false,
        )
    }

    /** What the IDE's Undo would do now, or null when it is unavailable. Read-only. */
    fun undoAction(): String? = robot.callJs<String>(
        js("importPackage(com.intellij.openapi.command.undo);" +
            "var p = ProjectManager.getInstance().getOpenProjects()[0]; var m = UndoManager.getInstance(p);" +
            "m.isUndoAvailable(null) ? String(m.getUndoActionNameAndDescription(null).first) : '\u0000none'"),
        runInEdt = true,
    ).takeUnless { it == "\u0000none" }

    /**
     * Opens [absolutePath] in the plain text editor and focuses it, so the IDE's Undo has the file's document in scope
     * (Undo of the plugin's edits is tied to the document, not to the project tree). Navigation only; edits nothing.
     */
    fun openTextEditor(absolutePath: String) {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
                "FileEditorManager.getInstance(p).openTextEditor(new com.intellij.openapi.fileEditor.OpenFileDescriptor(p, vf), true)"),
            runInEdt = true,
        )
    }

    /**
     * Opens [absolutePath] and selects its Scene View editor tab, as the plugin's own Open actions do. Navigation only; edits nothing.
     */
    fun openSceneView(absolutePath: String) {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
                "var m = FileEditorManager.getInstance(p); m.openFile(vf, true); m.setSelectedEditor(vf, 'abyssus-scene-view')"),
            runInEdt = true,
        )
    }

    /** Opens [absolutePath] (a UI asset's `meta.json`) and selects its Game UI editor tab. Navigation only; edits nothing. */
    fun openUiEditor(absolutePath: String) {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
                "var m = FileEditorManager.getInstance(p); m.openFile(vf, true); m.setSelectedEditor(vf, 'abyssus-ui-editor')"),
            runInEdt = true,
        )
    }

    /** True when the keyboard focus is in a text editor, so a shortcut reaches that editor's document. */
    fun editorHasFocus(): Boolean = robot.callJs(
        "var o = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();" +
            "o != null && String(o.getClass().getSimpleName()) == 'EditorComponentImpl'",
        runInEdt = true,
    )

    fun closeFile(absolutePath: String) {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var vf = LocalFileSystem.getInstance().findFileByPath(${quote(absolutePath)});" +
                "if (vf != null) FileEditorManager.getInstance(p).closeFile(vf)"),
            runInEdt = true,
        )
    }

    /**
     * The facts the screenshot environment manifest is checked against, read from the running IDE process: runtime,
     * IDE build, theme, scaling, locale, timezone and screen size. Read-only.
     */
    fun observeEnvironment(): Map<String, String> {
        val script = js(
            "importPackage(java.lang);" +
                "var tk = java.awt.Toolkit.getDefaultToolkit().getScreenSize();" +
                "var parts = [" +
                "'os=' + System.getProperty('os.name')," +
                "'arch=' + System.getProperty('os.arch')," +
                "'jbr=' + System.getProperty('java.runtime.version')," +
                "'ideBuild=' + com.intellij.openapi.application.ApplicationInfo.getInstance().getBuild().asString()," +
                "'theme=' + com.intellij.ide.ui.LafManager.getInstance().getCurrentUIThemeLookAndFeel().getName()," +
                "'uiScale=' + com.intellij.ui.scale.JBUIScale.sysScale()," +
                "'labelFont=' + com.intellij.util.ui.UIUtil.getLabelFont().getFamily() + ' ' + com.intellij.util.ui.UIUtil.getLabelFont().getSize()," +
                "'language=' + System.getProperty('user.language')," +
                "'timezone=' + java.util.TimeZone.getDefault().getID()," +
                "'screen=' + tk.width + 'x' + tk.height" +
                "]; parts.join('\\n')"
        )
        val ide = robot.callJs<String>(script, runInEdt = true)
        return net.nevinsky.abyssus.uitesting.screenshot.ScreenshotEnvironment.parseLines(ide)
    }

    /**
     * Asks the Project view to refresh, as the platform does after PSI changes. The Abyssus tree does not follow Undo or
     * outside edits by itself (only plugin edits), so the per-test reset does this to start from what is on disk.
     */
    fun refreshProjectView() {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "com.intellij.ide.projectView.ProjectView.getInstance(p).refresh()"),
            runInEdt = true,
        )
    }

    /** Sets a tool window's width by stretching it; layout state only. */
    fun setToolWindowWidth(id: String, width: Int) {
        robot.runJs(
            js("var p = ProjectManager.getInstance().getOpenProjects()[0];" +
                "var tw = ToolWindowManager.getInstance(p).getToolWindow(${quote(id)});" +
                "tw.stretchWidth($width - tw.getComponent().getWidth())"),
            runInEdt = true,
        )
    }
}
