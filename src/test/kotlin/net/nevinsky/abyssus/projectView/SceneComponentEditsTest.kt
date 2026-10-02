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

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.ecs.scene.EditResult
import net.nevinsky.abyssus.filetype.SceneJson
import java.io.File

class SceneComponentEditsTest : BasePlatformTestCase() {
    private val original = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    private fun textOf(f: VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    private fun components(f: VirtualFile, id: String) = SceneJson.parse(textOf(f))["ecs"]["entities"][id]["components"]

    private fun open(path: String): Pair<VirtualFile, TextEditor> {
        val f = myFixture.addFileToProject(path, original).virtualFile
        myFixture.openFileInEditor(f)
        return f to TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    }

    fun testAddUpdateRemoveEachUndoAsOneStep() {
        val (f, editor) = open("c/Main Scene.scene")
        val undo = UndoManager.getInstance(project)
        val start = textOf(f)

        assertEquals(EditResult.Changed, SceneComponentEdits.add(project, f, "0", "LightComponent"))
        val added = textOf(f)
        assertTrue(components(f, "0").has("LightComponent"))

        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, "0", "LightComponent", "intensity", "2"))
        assertEquals(2, components(f, "0")["LightComponent"]["light"]["intensity"].asInt())
        val edited = textOf(f)

        assertEquals(EditResult.Changed, SceneComponentEdits.remove(project, f, "0", "LightComponent"))
        assertFalse(components(f, "0").has("LightComponent"))

        undo.undo(editor)
        assertEquals(edited, textOf(f))
        undo.undo(editor)
        assertEquals(added, textOf(f))
        undo.undo(editor)
        assertEquals(start, textOf(f))
    }

    fun testRejectedAndUnchangedEditsWriteNothing() {
        val (f, _) = open("c/Same.scene")
        val before = textOf(f)
        assertTrue(SceneComponentEdits.update(project, f, "0", "PositionComponent", "localPosition.x", "abc") is EditResult.Rejected)
        assertEquals(EditResult.Unchanged, SceneComponentEdits.update(project, f, "0", "PositionComponent", "localPosition.x", "-3.035308"))
        assertTrue(SceneComponentEdits.remove(project, f, "0", "PickableComponent") is EditResult.Rejected)
        assertEquals(before, textOf(f))
    }

    fun testUpdatingOneValueChangesOneLine() {
        val (f, _) = open("c/Line.scene")
        val before = textOf(f).lines()
        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, "4", "CameraComponent", "camera.fieldOfView", "50"))
        val after = textOf(f).lines()
        assertEquals(before.size, after.size)
        assertEquals(1, before.indices.count { before[it] != after[it] })
    }

    fun testUnreadableSceneIsRejected() {
        val f = myFixture.addFileToProject("c/bad.scene", "not json").virtualFile
        assertTrue(SceneComponentEdits.add(project, f, "0", "LightComponent") is EditResult.Rejected)
        assertEquals("not json", textOf(f))
    }
}
