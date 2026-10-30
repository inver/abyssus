/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.water

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.water.WaterEdit
import net.nevinsky.abyssus.lib.core.editor.water.WaterEditError
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import java.io.File

class WaterSceneEditsTest : BasePlatformTestCase() {
    private val original = SceneJson().pretty(
        SceneJson().parse(File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()),
    )
    private lateinit var file: VirtualFile
    private lateinit var projectFile: VirtualFile
    private lateinit var meta: VirtualFile
    private val edits get() = WaterSceneEdits(project)
    private val document get() = FileDocumentManager.getInstance().getDocument(file)!!

    override fun setUp() {
        super.setUp()
        file = myFixture.addFileToProject("p/scenes/Main Scene.scene", original).virtualFile
        projectFile = myFixture.addFileToProject("p/p.abss", "untouched project").virtualFile
        meta = myFixture.addFileToProject("p/assets/terrain/meta.json", "untouched meta").virtualFile
        myFixture.openFileInEditor(file)
    }

    private fun undoRedo(before: String, after: String) {
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val undo = UndoManager.getInstance(project)
        undo.undo(editor)
        assertEquals(before, document.text)
        undo.redo(editor)
        assertEquals(after, document.text)
        undo.undo(editor)
        assertEquals(before, document.text)
        undo.redo(editor)
    }

    private fun assertOtherFilesUntouched() {
        assertEquals("untouched project", String(projectFile.contentsToByteArray()))
        assertEquals("untouched meta", String(meta.contentsToByteArray()))
    }

    fun testOpeningWritesNothing() {
        assertEquals(original, document.text)
    }

    fun testAddIsOneUndoableCommandThatKeepsTheRestOfTheText() {
        val added = edits.add(file, WaterPreset.LAKE, 1.0, 2.0, 3.0)
        assertEquals(WaterEdit.Changed, added.edit)
        val id = added.id!!
        val after = document.text
        assertTrue(after.contains("\"$id\""))
        assertEquals(SceneJson().parse(original).get("ecs"), SceneJson().parse(after).get("ecs"))
        undoRedo(original, after)
        assertOtherFilesUntouched()
    }

    fun testEveryEditKindHasExactUndoAndRedo() {
        val id = edits.add(file, WaterPreset.SEA, 0.0, 0.0, 0.0).id!!
        val steps = listOf(
            { edits.setField(file, id, WaterField.CLARITY, 4.0) },
            { edits.move(file, id, 5.0, 6.0, 7.0) },
            { edits.rename(file, id, "Bay") },
            { edits.setEnabled(file, id, false) },
            { edits.remove(file, id) },
        )
        for (step in steps) {
            val before = document.text
            assertEquals(WaterEdit.Changed, step())
            assertTrue(before != document.text)
            undoRedo(before, document.text)
        }
        assertOtherFilesUntouched()
    }

    fun testRejectedAndNoOpEditsLeaveTextAndUndoStackAlone() {
        val id = edits.add(file, WaterPreset.LAKE, 0.0, 0.0, 0.0).id!!
        val text = document.text
        assertEquals(WaterEdit.Unchanged, edits.setField(file, id, WaterField.LEVEL, 0.0))
        assertEquals(WaterEdit.Rejected(WaterEditError.BlankName), edits.rename(file, id, " "))
        assertTrue(edits.setField(file, id, WaterField.WIDTH, -1.0) is WaterEdit.Rejected)
        assertEquals(WaterEdit.Rejected(WaterEditError.NotFound), edits.remove(file, "nope"))
        assertEquals(text, document.text)
        // the add is still the latest undoable step
        val editor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(original, document.text)
    }

    fun testIncompatibleContainerIsNeverReplaced() {
        val odd = myFixture.addFileToProject("odd.scene", """{"format":"abyssus","formatVersion":1,"abyssus":[1]}""").virtualFile
        val result = edits.add(odd, WaterPreset.LAKE, 0.0, 0.0, 0.0)
        assertEquals(WaterEdit.Rejected(WaterEditError.IncompatibleContainer), result.edit)
        assertEquals(null, result.id)
        assertEquals("""{"format":"abyssus","formatVersion":1,"abyssus":[1]}""", FileDocumentManager.getInstance().getDocument(odd)!!.text)
    }

    fun testNonNativeSceneIsLeftUnchanged() {
        val legacy = myFixture.addFileToProject("legacy.scene", "{}").virtualFile
        assertEquals(WaterEdit.Unchanged, edits.add(legacy, WaterPreset.LAKE, 0.0, 0.0, 0.0).edit)
        assertEquals("{}", FileDocumentManager.getInstance().getDocument(legacy)!!.text)
    }
}
