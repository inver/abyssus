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

    fun testUnsavedTextEditInvalidatesTheCacheAndFiresListener() {
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
        assertTrue(notifications.any { (file, value) -> file == abss && value.physicsEnabled && value.problems.isEmpty() })
        assertTrue(settings.getSettings(abss).physicsEnabled)
        assertFalse(String(abss.contentsToByteArray(), abss.charset).contains("physicsEnabled"))
    }

    fun testNonBooleanValueReportsOnceAndLeavesFileUnchanged() {
        val notices = mutableListOf<com.intellij.notification.Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(com.intellij.notification.Notifications.TOPIC,
            object : com.intellij.notification.Notifications {
                override fun notify(notification: com.intellij.notification.Notification) { notices += notification }
            })
        val abss = myFixture.addFileToProject("p/Test.abss", """{"format":"abyssus","formatVersion":1,"name":"Test","physicsEnabled":"yes"}""").virtualFile
        val originalText = String(abss.contentsToByteArray(), abss.charset)
        val result = settings.getSettings(abss)
        assertFalse(result.physicsEnabled)
        assertEquals(listOf("physicsEnabled"), result.problems)
        val document = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(1, " ") }
        WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().saveDocument(document) }
        repeat(3) { settings.getSettings(abss) }
        assertEquals(notices.joinToString { "${it.groupId}: ${it.content}" }, 1,
            notices.count { it.groupId == "Abyssus Project Settings" })
        assertEquals(originalText.take(1) + " " + originalText.drop(1), String(abss.contentsToByteArray(), abss.charset))
    }

    fun testUnsupportedProjectCannotEnablePhysics() {
        for (header in listOf("", "\"format\":\"abyssus\",\"formatVersion\":2,")) {
            val abss = myFixture.addFileToProject("p${header.length}/Test.abss", "{$header\"physicsEnabled\":true}").virtualFile
            val before = String(abss.contentsToByteArray(), abss.charset)
            assertFalse(settings.getSettings(abss).physicsEnabled)
            assertFalse(settings.setPhysicsEnabled(abss, false))
            assertEquals(before, String(abss.contentsToByteArray(), abss.charset))
        }
    }

    fun testUndoAndRedoRefreshWithoutSaving() {
        val abss = copyAbss("Untitled")
        myFixture.openFileInEditor(abss)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        settings.setPhysicsEnabled(abss, true)
        UndoManager.getInstance(project).undo(editor)
        assertFalse(settings.getSettings(abss).physicsEnabled)
        UndoManager.getInstance(project).redo(editor)
        assertTrue(settings.getSettings(abss).physicsEnabled)
    }

    fun testEqualValueCreatesNoEditAndProjectsAreIndependent() {
        val a = copyAbss("Untitled")
        val b = myFixture.addFileToProject("Other/Other.abss", """{"format":"abyssus","formatVersion":1,"physicsEnabled":true}""").virtualFile
        assertTrue(settings.getSettings(b).physicsEnabled)
        assertFalse(settings.getSettings(a).physicsEnabled)
        assertFalse(settings.setPhysicsEnabled(b, true))
        assertTrue(settings.setPhysicsEnabled(a, true))
        assertTrue(settings.setPhysicsEnabled(b, false))
        assertTrue(settings.getSettings(a).physicsEnabled)
        assertFalse(settings.getSettings(b).physicsEnabled)
    }
}
