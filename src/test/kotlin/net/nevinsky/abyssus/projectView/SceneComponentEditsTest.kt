/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
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

    private fun open(path: String, text: String = original): Pair<VirtualFile, TextEditor> {
        val f = myFixture.addFileToProject(path, text).virtualFile
        myFixture.openFileInEditor(f)
        return f to TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    }

    fun testAddLightIsOneUndoStep() {
        val (f, editor) = open("c/Lights.scene", File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())
        val start = textOf(f)
        val result = SceneComponentEdits.addLight(project, f, net.nevinsky.abyssus.ecs.scene.LightPreset.SUN, net.nevinsky.abyssus.sceneview.Vec3(10f, 0f, -4f))
        assertEquals(EditResult.Changed, result.result)
        assertEquals("7", result.entityId)
        assertEquals("Sun 7", components(f, "7")["NameComponent"]["name"].asText())
        UndoManager.getInstance(project).undo(editor)
        assertEquals(start, textOf(f))
    }

    fun testSpotlightBeamEditsEachUndoAndPreserveUnrelatedText() {
        val text = """{"ecs":{"entities":{"0":{"components":{"TypeComponent":{"type":"LIGHT_SPOT"},"LightComponent":{"light":{"intensity":1.000,"unknown":2.3400}}}}}}}"""
        val (f, editor) = open("c/Beam.scene", text)
        val before = textOf(f)
        assertEquals(EditResult.Unchanged, SceneComponentEdits.update(project, f, "0", "LightComponent", "coneAngle", "45"))
        assertTrue(SceneComponentEdits.update(project, f, "0", "LightComponent", "edgeSoftness", "101") is EditResult.Rejected)
        assertEquals(before, textOf(f))
        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, "0", "LightComponent", "coneAngle", "60"))
        val angle = textOf(f)
        assertEquals("2.3400", components(f, "0")["LightComponent"]["light"]["unknown"].toString())
        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, "0", "LightComponent", "edgeSoftness", "25"))
        assertEquals(0.25f, components(f, "0")["LightComponent"]["light"]["edgeSoftness"].floatValue())
        UndoManager.getInstance(project).undo(editor)
        assertEquals(angle, textOf(f))
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, textOf(f))
    }

    fun testMalformedSceneFieldsRejectLightWithoutWrite() {
        val text = """{"name":[],"ecs":{"entities":{}}}"""
        val f = myFixture.addFileToProject("c/bad-name.scene", text).virtualFile
        val result = SceneComponentEdits.addLight(project, f, net.nevinsky.abyssus.ecs.scene.LightPreset.SUN, net.nevinsky.abyssus.sceneview.Vec3(0f, 0f, 0f))
        assertTrue(result.result is EditResult.Rejected)
        assertFalse(canAddLight(f))
        assertEquals(text, textOf(f))
    }

    fun testUnreadableSceneRejectsLightWithoutWrite() {
        val f = myFixture.addFileToProject("c/bad-light.scene", "not json").virtualFile
        val result = SceneComponentEdits.addLight(project, f, net.nevinsky.abyssus.ecs.scene.LightPreset.SPOT, net.nevinsky.abyssus.sceneview.Vec3(0f, 0f, 0f))
        assertTrue(result.result is EditResult.Rejected)
        assertNull(result.entityId)
        assertEquals("not json", textOf(f))
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
