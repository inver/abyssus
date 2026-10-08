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
        assertTrue(newText.contains("\"physicsEnabled\":true"))
        assertTrue(newText.endsWith("\"physicsEnabled\":true}"))
        assertTrue(originalText.length < newText.length)
    }

    fun testUndoRestoresOriginalText() {
        val abss = copyAbss("Untitled")
        val originalText = String(abss.contentsToByteArray(), abss.charset)
        settings.setPhysicsEnabled(abss, true)
        WriteCommandAction.runWriteCommandAction(project) { UndoManager.getInstance(project).undo(null) }
        assertEquals(originalText, String(abss.contentsToByteArray(), abss.charset))
    }

    fun testTextEditFiresListener() {
        val abss = copyAbss("Untitled")
        var fired = false
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(ProjectSettingsListener.TOPIC, ProjectSettingsListener { _, _ ->
            fired = true
        })
        val document = FileDocumentManager.getInstance().getDocument(abss)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("}", ",\"physicsEnabled\":true}"))
        }
        assertTrue(fired)
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