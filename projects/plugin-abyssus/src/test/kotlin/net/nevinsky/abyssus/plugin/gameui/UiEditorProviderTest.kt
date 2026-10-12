/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.ui.UiAssetTemplate
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditError
import net.nevinsky.abyssus.lib.core.ui.model.UiIssue
import net.nevinsky.abyssus.lib.core.ui.model.UiIssueCode
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.util.UUID
import java.util.concurrent.Executor
import javax.swing.JPanel

class UiEditorProviderTest : BasePlatformTestCase() {
    private val provider = UiFileEditorProvider()
    private val uiMeta = UiAssetTemplate().create(UUID.fromString("0b1c0f5e-7a2e-4d0b-9a0a-3a1f6d6e2b11"), 1L)

    private fun project(meta: String = uiMeta, assetName: String = "menus"): VirtualFile {
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1,"name":"P","uiAssetNames":["$assetName"]}""")
        return myFixture.addFileToProject("p/assets/$assetName/meta.json", meta).virtualFile
    }

    private fun open(file: VirtualFile): UiFileEditor {
        val editor = UiFileEditor(this.project, file, Executor { it.run() }) { JPanel() }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return editor
    }

    fun testItAcceptsOnlyUiAssetsOfANativeProject() {
        val ui = project()
        assertTrue(provider.accept(project, ui))
        val model = myFixture.addFileToProject("p/assets/tree/meta.json",
            """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{}}""").virtualFile
        assertFalse("other metadata keeps its editors", provider.accept(project, model))
        assertFalse(provider.accept(project, myFixture.addFileToProject("p/scenes/Main.scene", """{"format":"abyssus","formatVersion":1}""").virtualFile))
        assertFalse(provider.accept(project, myFixture.addFileToProject("p/assets/menus/other.json", uiMeta).virtualFile))
        assertEquals("abyssus-ui-editor", provider.editorTypeId)
    }

    fun testAFileOutsideAProjectOrAtTheWrongDepthIsNotOurs() {
        val loose = myFixture.addFileToProject("loose/assets/menus/meta.json", uiMeta).virtualFile
        assertFalse("no .abss beside the assets folder", provider.accept(project, loose))
        myFixture.addFileToProject("q/Q.abss", """{"format":"abyssus","formatVersion":1}""")
        val deep = myFixture.addFileToProject("q/assets/menus/sub/meta.json", uiMeta).virtualFile
        assertFalse(provider.accept(project, deep))
        val direct = myFixture.addFileToProject("q/meta.json", uiMeta).virtualFile
        assertFalse(provider.accept(project, direct))
        assertNull(uiAssetOwner(loose))
        assertEquals("Q.abss", uiAssetOwner(myFixture.addFileToProject("q/assets/ok/meta.json", uiMeta).virtualFile)!!.name)
    }

    fun testASupportedAssetOpensEditableAndNothingIsRewritten() {
        val file = project()
        val before = String(file.contentsToByteArray())
        val editor = open(file)
        assertNull(editor.statusText)
        assertTrue(editor.controller.state is UiEditorState.Editable)
        assertEquals("main", editor.controller.screenId)
        assertEquals("root", editor.controller.selection.widgetId)
        assertEquals(before, String(file.contentsToByteArray()))
        assertEquals(before, FileDocumentManager.getInstance().getDocument(file)!!.text)
        editor.dispose()
    }

    fun testAnUnsupportedVersionShowsItsReasonAndIsNeverChanged() {
        val future = uiMeta.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        val file = project(future)
        assertTrue("an unsupported UI asset is still a UI asset", provider.accept(project, file))
        val editor = open(file)
        val state = editor.controller.state as UiEditorState.Refused
        assertEquals(UiIssueCode.UI_VERSION, state.issues.single().code)
        assertTrue(editor.statusText!!.contains(AbyssusBundle.message("uiIssue.UI_VERSION", "additional.ui.schemaVersion")))
        assertFalse(editor.controller.addWidget("button"))
        assertEquals(future, FileDocumentManager.getInstance().getDocument(file)!!.text)
        editor.dispose()
    }

    fun testTheTabFollowsTheDocumentThroughInvalidTextAndBack() {
        val file = project()
        val editor = open(file)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        ApplicationManager.getApplication().runWriteAction { document.setText("{ not json") }
        editor.flushPendingReload()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(editor.controller.state is UiEditorState.Unreadable)
        assertTrue(editor.statusText!!.isNotBlank())
        ApplicationManager.getApplication().runWriteAction { document.setText(uiMeta) }
        editor.flushPendingReload()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(editor.controller.state is UiEditorState.Editable)
        assertNull(editor.statusText)
        editor.dispose()
    }

    fun testEveryIssueAndEditErrorHasAUserVisibleMessage() {
        for (code in UiIssueCode.entries) {
            val text = uiIssueMessage(UiIssue(code, "somewhere"))
            assertFalse(code.name, text.startsWith("!") || text.contains("uiIssue."))
            assertTrue(code.name, text.isNotBlank())
        }
        for (error in UiEditError.entries) assertFalse(error.name, AbyssusBundle.message("uiEditError.${error.name}").startsWith("!"))
    }

    fun testRegisteredNamesAreReadFromTheProjectFile() {
        val file = project()
        assertEquals(listOf("menus"), registeredUiAssets(uiAssetOwner(file)!!))
        val bare = myFixture.addFileToProject("r/R.abss", """{"format":"abyssus","formatVersion":1}""").virtualFile
        assertTrue(registeredUiAssets(bare).isEmpty())
        val odd = myFixture.addFileToProject("s/S.abss", """{"format":"abyssus","formatVersion":1,"uiAssetNames":["a",7,null,"b"]}""").virtualFile
        assertEquals(listOf("a", "b"), registeredUiAssets(odd))
    }
}
