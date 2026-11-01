/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NativeDocumentWriteGuardTest : BasePlatformTestCase() {
    fun testLegacyAndFutureDocumentsNeverReachMutationOrFormatting() {
        for (extension in listOf("scene", "abss", "json")) {
            for ((index, text) in listOf("{\"name\":\"Legacy\"}", "{\"formatVersion\":2,\"name\":\"Future\"}").withIndex()) {
                val path = if (extension == "json") "p$index/assets/a/meta.json" else "p$index/document.$extension"
                val file = myFixture.addFileToProject(path, text).virtualFile
                val document = FileDocumentManager.getInstance().getDocument(file)!!
                var called = false
                assertFalse(editSceneDocument(project, file, "Edit") { called = true; true })
                assertFalse(called)
                if (extension != "json") {
                    val manager = FileEditorManager.getInstance(project)
                    manager.openFile(file, true)
                    SceneFormatListener().fileOpened(manager, file)
                    manager.closeFile(file)
                    manager.openFile(file, true)
                }
                assertEquals(text, document.text)
                assertEquals(text, String(file.contentsToByteArray(), file.charset))
            }
        }
    }
    fun testNativeEditIsOneCommandAndUndoRestoresExactText() {
        val text = """{"formatVersion":1,"name":"Native"}"""
        val file = myFixture.addFileToProject("Edit.scene", text).virtualFile
        myFixture.openFileInEditor(file)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val before = document.text
        assertTrue(editSceneDocument(project, file, "Rename") { scene -> scene.add(net.nevinsky.abyssus.lib.core.dto.EntityType.OBJECT); true })
        assertTrue(document.text.contains("OBJECT"))
        com.intellij.openapi.command.undo.UndoManager.getInstance(project).undo(editor)
        assertEquals(before, document.text)
    }

}
