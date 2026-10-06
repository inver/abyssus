/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.filetype

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.fasterxml.jackson.databind.node.ObjectNode

class NativeDocumentWriteGuardTest : BasePlatformTestCase() {
    fun testLegacyAndFutureDocumentsNeverReachMutationOrFormatting() {
        for (extension in listOf("scene", "abss", "json")) {
            for ((index, text) in listOf("{\"name\":\"Legacy\"}", "{\"format\":\"abyssus\",\"formatVersion\":2,\"name\":\"Future\"}").withIndex()) {
                val path = if (extension == "json") "p$index/assets/a/meta.json" else "p$index/document.$extension"
                val file = myFixture.addFileToProject(path, text).virtualFile
                val document = FileDocumentManager.getInstance().getDocument(file)!!
                var called = false
                assertFalse(editSceneJson(project, file, "Edit") { called = true; (it as ObjectNode).put("name", "Changed"); true })
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
    fun testCandidateMustKeepNativeIdentityAndReservedFieldsAbsent() {
        val text = """{"format":"abyssus","formatVersion":1,"name":"Native"}"""
        val file = myFixture.addFileToProject("Native.scene", text).virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertFalse(editSceneJson(project, file, "Remove marker") { (it as ObjectNode).remove("format"); true })
        assertEquals(text, document.text)
        assertFalse(editSceneJson(project, file, "Legacy payload") { (it as ObjectNode).putObject("ecs").putObject("componentIdentifiers"); true })
        assertEquals(text, document.text)
        assertEquals(text, String(file.contentsToByteArray(), file.charset))
    }
    fun testNativeEditIsOneCommandAndUndoRestoresExactText() {
        val text = """{"format":"abyssus","formatVersion":1,"name":"Native","number":1.00}"""
        val file = myFixture.addFileToProject("Edit.scene", text).virtualFile
        myFixture.openFileInEditor(file)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val before = document.text
        assertTrue(editSceneJson(project, file, "Rename") { (it as ObjectNode).put("name", "Changed"); true })
        assertTrue(document.text.contains("1.00"))
        com.intellij.openapi.command.undo.UndoManager.getInstance(project).undo(editor)
        assertEquals(before, document.text)
    }

}
