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

    fun testProjectFilesAreJsonToo() {
        assertEquals(JsonLanguage.INSTANCE, AbyssusProjectFileType.INSTANCE.language)
        val psi = myFixture.addFileToProject("a/Untitled.abss", minified)
        assertEquals(JsonLanguage.INSTANCE, psi.language)
        assertEquals(AbyssusProjectFileType.INSTANCE, psi.virtualFile.fileType)
    }

    fun testTextEditorShowsFormattedJsonForProjectFiles() {
        val file = myFixture.addFileToProject("f/Untitled.abss", minified).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        val text = FileDocumentManager.getInstance().getDocument(file)!!.text
        assertTrue(text, text.lines().size > 4)
        assertTrue(text, text.contains("\n  \"fog\": {"))
    }

    fun testInvalidProjectJsonIsNotTouched() {
        val bad = "{ \"mainCamera\": "
        val file = myFixture.addFileToProject("g/Untitled.abss", bad).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        assertEquals(bad, FileDocumentManager.getInstance().getDocument(file)!!.text)
    }

    fun testOtherFilesAreNotReformatted() {
        for (name in listOf("e/Main.json", "e/Main.abss.bak", "e/Main.txt")) {
            val file = myFixture.addFileToProject(name, minified).virtualFile
            myFixture.configureFromExistingVirtualFile(file)
            assertEquals(name, minified, FileDocumentManager.getInstance().getDocument(file)!!.text)
        }
    }
}
