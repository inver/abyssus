/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.filetype.ProjectSettingsListener

class AbyssusProjectSettingsTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private val settings: AbyssusProjectSettings by lazy { project.service() }

    private fun copyAbss(dir: String, name: String = "Untitled"): VirtualFile {
        myFixture.copyFileToProject("$dir/$dir.abss", "$dir/$name.abss")
        return myFixture.findFileInTempDir("$dir/$name.abss")
    }

    fun testTurningPhysicsOnAddsKeyLastAndRestIsIdentical() {
        val abss = copyAbss("Untitled")
        val originalText = String(abss.contentsToByteArray(), abss.charset)
        assertTrue(settings.setPhysicsEnabled(abss, true))
        val newText = String(abss.contentsToByteArray(), abss.charset)
        assertEquals(originalText.dropLast(1) + ",\"physicsEnabled\":true}", newText)
        assertTrue(settings.getSettings(abss).physicsEnabled)
    }

    fun testUndoRestoresOriginalText() {
        val abss = copyAbss("Untitled")
        myFixture.openFileInEditor(abss)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val document = FileDocumentManager.getInstance().getDocument(abss)!!
        val originalText = document.text
        settings.setPhysicsEnabled(abss, true)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(originalText, document.text)
        WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().saveDocument(document) }
        assertEquals(originalText, String(abss.contentsToByteArray(), abss.charset))
    }

    fun testSavingATextEditInvalidatesTheCacheAndFiresListener() {
        val abss = copyAbss("Untitled")
        assertFalse(settings.getSettings(abss).physicsEnabled)
        val notifications = mutableListOf<Pair<VirtualFile, ProjectSettings>>()
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(ProjectSettingsListener.TOPIC, ProjectSettingsListener { file, value ->
            notifications += file to value
        })
        val document = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.dropLast(1) + ",\"physicsEnabled\":true}")
        }
        assertTrue(notifications.isEmpty())
        assertFalse(settings.getSettings(abss).physicsEnabled)
        WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().saveDocument(document) }
        assertTrue(notifications.any { (file, value) -> file == abss && value.physicsEnabled && value.problems.isEmpty() })
        assertTrue(settings.getSettings(abss).physicsEnabled)
    }

    fun testNonBooleanValueReportsOnceAndLeavesFileUnchanged() {
        val abss = myFixture.addFileToProject("p/Test.abss", """{"format":"abyssus","formatVersion":1,"name":"Test","physicsEnabled":"yes"}""").virtualFile
        val originalText = String(abss.contentsToByteArray(), abss.charset)
        val result = settings.getSettings(abss)
        assertFalse(result.physicsEnabled)
        assertEquals(listOf("physicsEnabled"), result.problems)
        assertEquals(originalText, String(abss.contentsToByteArray(), abss.charset))
    }
}
