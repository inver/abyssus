package net.nevinsky.abyssus.filetype

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneTextEditorTest : BasePlatformTestCase() {
    private val minified = """{"id":0,"name":"Main","fog":{"density":0.001}}"""

    fun testSceneFilesAreJson() {
        assertEquals(JsonLanguage.INSTANCE, SceneFileType.INSTANCE.language)
        val psi = myFixture.addFileToProject("a/Main.scene", minified)
        assertEquals(JsonLanguage.INSTANCE, psi.language)
        assertEquals(SceneFileType.INSTANCE, psi.virtualFile.fileType)
    }

    fun testOtherExtensionsAreNotTreatedAsSceneJson() {
        val psi = myFixture.addFileToProject("a/Main.scene.bak", minified)
        assertNotSame(SceneFileType.INSTANCE, psi.virtualFile.fileType)
    }

    fun testTextEditorShowsFormattedJsonOnOpen() {
        val file = myFixture.addFileToProject("b/Main.scene", minified).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        val text = FileDocumentManager.getInstance().getDocument(file)!!.text
        assertTrue(text, text.lines().size > 4)
        assertTrue(text, text.contains("\n  \"fog\": {"))
    }

    fun testSwitchingToTextTabFormatsAScene() {
        val file = myFixture.addFileToProject("c/Main.scene", minified).virtualFile
        val manager = FileEditorManager.getInstance(project)
        manager.openFile(file, true)
        val doc = FileDocumentManager.getInstance().getDocument(file)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { doc.setText(minified) }
        // select the Scene View tab, then the text tab again
        manager.setSelectedEditor(file, net.nevinsky.abyssus.sceneview.SceneFileEditorProvider.EDITOR_TYPE_ID)
        val text = manager.getEditors(file).first { it is TextEditor }
        manager.setSelectedEditor(file, "text-editor")
        assertNotNull(text)
        assertTrue(doc.text, doc.text.lines().size > 4)
    }

    fun testInvalidJsonIsNotTouched() {
        val bad = "{ \"id\": "
        val file = myFixture.addFileToProject("d/Main.scene", bad).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        assertEquals(bad, FileDocumentManager.getInstance().getDocument(file)!!.text)
    }

    fun testOnlyScenesAreReformatted() {
        val file = myFixture.addFileToProject("e/Main.abss", minified).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        assertEquals(minified, FileDocumentManager.getInstance().getDocument(file)!!.text)
    }
}
