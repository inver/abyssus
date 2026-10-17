/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.dto

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.plugin.filetype.AbyssusProjectSettings
import net.nevinsky.abyssus.plugin.filetype.ProjectSettingsListener
import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

class ProjectAntialiasingSettingsTest : BasePlatformTestCase() {
    private val settings get() = project.service<AbyssusProjectSettings>()
    private fun file(name: String = "P", extra: String = "") = myFixture.addFileToProject("$name/$name.abss",
        """{"format":"abyssus","formatVersion":1,"name":"$name","future":{"n":2.500}$extra}""").virtualFile
    private fun text(file: com.intellij.openapi.vfs.VirtualFile) = FileDocumentManager.getInstance().getDocument(file)!!.text

    fun testDefaultsIgnoreOldApplicationPreferenceAndReadWithoutWriting() {
        val properties = PropertiesComponent.getInstance()
        val key = "net.nevinsky.abyssus.sceneView.antialiasing"
        val old = properties.getValue(key)
        properties.setValue(key, "X8")
        try {
            val f = file()
            val before = text(f)
            assertEquals(AntialiasingLevel.X4, settings.getSettings(f).antialiasing)
            assertEquals(before, text(f))
            assertEquals(before, String(f.contentsToByteArray(), f.charset))
        } finally { if (old == null) properties.unsetValue(key) else properties.setValue(key, old) }
    }

    fun testEveryChoiceReloadsFromItsOwnNativeDocumentAndInvalidValuesReportAProblem() {
        for (level in AntialiasingLevel.entries) {
            val f = file(level.name, ",\"sceneViewAntialiasing\":\"${level.name}\"")
            assertEquals(level, settings.getSettings(f).antialiasing)
            assertTrue(settings.getSettings(f).problems.isEmpty())
        }
        for ((index, raw) in listOf("null", "4", "true", "[]", "\"future\"").withIndex()) {
            val f = file("bad$index", ",\"sceneViewAntialiasing\":$raw")
            val before = text(f)
            assertEquals(AntialiasingLevel.X4, settings.getSettings(f).antialiasing)
            assertEquals(listOf("sceneViewAntialiasing"), settings.getSettings(f).problems)
            assertEquals(before, text(f))
        }
    }

    fun testOnlyTheSettingChangesDefaultsAreOmittedAndProjectsAreIndependent() {
        val a = file()
        val b = file("B", ",\"sceneViewAntialiasing\":\"OFF\"")
        val before = text(a)
        assertFalse(settings.setAntialiasing(a, AntialiasingLevel.X4))
        assertTrue(settings.setAntialiasing(a, AntialiasingLevel.X8))
        assertEquals(before.dropLast(1) + ",\"sceneViewAntialiasing\":\"X8\"}", text(a))
        assertFalse(settings.setAntialiasing(a, AntialiasingLevel.X8))
        assertEquals(AntialiasingLevel.OFF, settings.getSettings(b).antialiasing)
        assertTrue(settings.setAntialiasing(a, AntialiasingLevel.X4))
        assertEquals(before, text(a))
    }

    fun testUndoRedoAndUnsavedTextNotifyWithTheCurrentValue() {
        val f = file()
        settings.getSettings(f)
        val seen = mutableListOf<AntialiasingLevel>()
        project.messageBus.connect(testRootDisposable).subscribe(ProjectSettingsListener.TOPIC,
            ProjectSettingsListener { changed, value ->
                if (changed == f) {
                    assertEquals(value.antialiasing, settings.getSettings(f).antialiasing)
                    seen += value.antialiasing
                }
            })
        myFixture.openFileInEditor(f)
        val editor = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val original = text(f)
        settings.setAntialiasing(f, AntialiasingLevel.OFF)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(original, text(f))
        UndoManager.getInstance(project).redo(editor)
        assertEquals(listOf(AntialiasingLevel.OFF, AntialiasingLevel.X4, AntialiasingLevel.OFF), seen)
        val saved = String(f.contentsToByteArray(), f.charset)
        WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(f)!!.setText(text(f).replace("\"OFF\"", "\"X2\""))
        }
        assertEquals(AntialiasingLevel.X2, settings.getSettings(f).antialiasing)
        assertEquals(AntialiasingLevel.X2, seen.last())
        assertEquals(saved, String(f.contentsToByteArray(), f.charset))
    }

    fun testExternalVfsContentChangeNotifiesWithoutACachedDocument() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("external")
        lateinit var f: com.intellij.openapi.vfs.VirtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            f = dir.createChildData(this, "External.abss")
            f.setBinaryContent("""{"format":"abyssus","formatVersion":1}""".toByteArray())
        }
        assertNull(FileDocumentManager.getInstance().getCachedDocument(f))
        assertEquals(AntialiasingLevel.X4, settings.getSettings(f).antialiasing)
        val seen = mutableListOf<AntialiasingLevel>()
        project.messageBus.connect(testRootDisposable).subscribe(ProjectSettingsListener.TOPIC,
            ProjectSettingsListener { changed, value -> if (changed == f) seen += value.antialiasing })
        WriteCommandAction.runWriteCommandAction(project) {
            f.setBinaryContent("""{"format":"abyssus","formatVersion":1,"sceneViewAntialiasing":"X8"}""".toByteArray())
        }
        assertNull(FileDocumentManager.getInstance().getCachedDocument(f))
        assertEquals(listOf(AntialiasingLevel.X8), seen)
        assertEquals(AntialiasingLevel.X8, settings.getSettings(f).antialiasing)
    }

    fun testUnsupportedNativeDocumentsCannotBeEdited() {
        for ((index, header) in listOf("", "\"format\":\"abyssus\",\"formatVersion\":2,").withIndex()) {
            val f = myFixture.addFileToProject("bad$index/P.abss", "{$header\"sceneViewAntialiasing\":\"OFF\"}").virtualFile
            val before = text(f)
            assertFalse(settings.getSettings(f).supported)
            assertFalse(settings.setAntialiasing(f, AntialiasingLevel.X8))
            assertEquals(before, text(f))
        }
    }
}
