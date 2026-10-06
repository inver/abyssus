/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.meta.EditError
import net.nevinsky.abyssus.lib.core.editor.meta.FieldValue
import java.io.File
import net.nevinsky.abyssus.plugin.testCore

private fun update(project: com.intellij.openapi.project.Project, dir: com.intellij.openapi.vfs.VirtualFile, key: String, expected: FieldValue, value: FieldValue) =
    AssetMetaEdits.update(project, dir, key, expected, value, testCore.assetEditor)

class AssetMetaEditsTest : BasePlatformTestCase() {
    private val terrainMeta = File("src/test/testData/project/Untitled/assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/meta.json").readText().trimEnd() + "\n"
    private val physicalMeta = File("src/test/testData/project/Untitled/assets/skybox_physical/meta.json").readText()

    private fun textOf(f: VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    private fun open(folder: String, text: String): Triple<VirtualFile, VirtualFile, TextEditor> {
        val meta = myFixture.addFileToProject("p/assets/$folder/meta.json", text).virtualFile
        myFixture.openFileInEditor(meta)
        return Triple(meta.parent, meta, TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
    }

    fun testOneFieldEditChangesOnlyThatValue() {
        val (dir, meta, _) = open("terrain", terrainMeta)
        assertEquals(AssetEditResult.Changed, update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(800)))
        assertEquals(terrainMeta.replace("\"size\": 1600", "\"size\": 800"), textOf(meta))
    }

    fun testUndoAndRedoRestoreTheExactText() {
        val (dir, meta, editor) = open("terrain", terrainMeta)
        val undo = UndoManager.getInstance(project)
        assertEquals(AssetEditResult.Changed, update(project, dir, "uv", FieldValue.Real(60f), FieldValue.Real(30f)))
        val edited = textOf(meta)
        assertTrue(edited, edited.contains("\"uv\": 30.0"))
        undo.undo(editor)
        assertEquals(terrainMeta, textOf(meta))
        assertTrue(textOf(meta).contains("\"uv\": 60.0"))
        undo.redo(editor)
        assertEquals(edited, textOf(meta))
    }

    fun testUntouchedNumbersKeepTheirText() {
        val (dir, meta, _) = open("sky", physicalMeta)
        assertEquals(AssetEditResult.Changed, update(project, dir, "sunIntensity", FieldValue.Real(20f), FieldValue.Real(25f)))
        val after = textOf(meta)
        assertTrue(after, after.contains("[5.8e-6, 13.5e-6, 33.1e-6]") || after.contains("5.8e-6"))
        assertTrue(after.contains("\"planetRadius\": 6360000.0"))
        assertTrue(after.contains("\"sunIntensity\": 25.0"))
        assertTrue(after.contains("\"lastModified\": 1663444124794"))
    }

    fun testLastModifiedAndIdentityAreNotTouched() {
        val (dir, meta, _) = open("terrain", terrainMeta)
        update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(1))
        val after = textOf(meta)
        assertTrue(after.contains("\"lastModified\": 1699293063182"))
        assertTrue(after.contains("\"uuid\": \"2cf70bf7-f7ee-4c41-934c-e40df1d35c8b\""))
        assertTrue(after.contains("\"type\": \"TERRAIN\""))
    }

    fun testAnOmittedDefaultIsNotMaterializedByAnEqualEdit() {
        val text = """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"type":"SKYBOX_PROCEDURAL","additional":{"vertex":"v","fragment":"f"}}"""
        val (dir, meta, _) = open("sky2", text)
        assertEquals(AssetEditResult.Unchanged, update(project, dir, "sunIntensity", FieldValue.Real(20f), FieldValue.Real(20f)))
        assertEquals(text, textOf(meta))
        assertEquals(AssetEditResult.Changed, update(project, dir, "sunIntensity", FieldValue.Real(20f), FieldValue.Real(25f)))
        assertEquals(text.replace("\"fragment\":\"f\"", "\"fragment\":\"f\",\"sunIntensity\":25.0"), textOf(meta))
    }

    fun testRejectedAndEqualEditsWriteNothing() {
        val (dir, meta, _) = open("terrain", terrainMeta)
        assertEquals(AssetEditResult.Rejected(EditError.NOT_POSITIVE), update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(0)))
        assertEquals(AssetEditResult.Unchanged, update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(1600)))
        assertEquals(AssetEditResult.Rejected(EditError.UNSUPPORTED_FIELD), update(project, dir, "uuid", FieldValue.None, FieldValue.Text("x")))
        assertEquals(terrainMeta, textOf(meta))
    }

    fun testMalformedMetaIsNotWritten() {
        val (dir, meta, _) = open("bad", "{ not json")
        assertEquals(AssetEditResult.Unreadable, update(project, dir, "size", FieldValue.Int(1), FieldValue.Int(2)))
        assertEquals("{ not json", textOf(meta))
    }

    fun testAStaleEditorValueIsAConflictAndKeepsTheNewerValue() {
        val (dir, meta, _) = open("terrain", terrainMeta)
        assertEquals(AssetEditResult.Changed, update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(900)))
        val newer = textOf(meta)
        val result = update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(500))
        assertEquals(AssetEditResult.Conflict(FieldValue.Int(900)), result)
        assertEquals(newer, textOf(meta))
    }

    fun testUnsavedTextIsWhatIsEdited() {
        val (dir, meta, _) = open("terrain", terrainMeta)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(meta)!!.setText(terrainMeta.replace("\"size\": 1600", "\"size\": 1000"))
        }
        assertEquals(AssetEditResult.Conflict(FieldValue.Int(1000)), update(project, dir, "size", FieldValue.Int(1600), FieldValue.Int(5)))
        assertEquals(AssetEditResult.Changed, update(project, dir, "size", FieldValue.Int(1000), FieldValue.Int(5)))
    }

    fun testAMissingMetaFileIsUnreadable() {
        val dir = myFixture.addFileToProject("p/assets/empty/other.txt", "x").virtualFile.parent
        assertEquals(AssetEditResult.Unreadable, update(project, dir, "size", FieldValue.Int(1), FieldValue.Int(2)))
    }
}
