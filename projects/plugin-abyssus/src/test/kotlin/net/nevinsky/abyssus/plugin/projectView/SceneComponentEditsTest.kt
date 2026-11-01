/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.components.LightPreset
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import java.io.File
import net.nevinsky.abyssus.plugin.testMetaFiles

class SceneComponentEditsTest : BasePlatformTestCase() {
    private val reader get() = net.nevinsky.abyssus.plugin.dto.SceneDocumentCache.of(project)

    private val original = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    private fun textOf(f: VirtualFile) = FileDocumentManager.getInstance().getDocument(f)!!.text

    private fun entityDto(f: VirtualFile, id: Long) = net.nevinsky.abyssus.plugin.parsedScene(textOf(f)).entity(id)!!

    private fun open(path: String, text: String = original): Pair<VirtualFile, TextEditor> {
        val f = myFixture.addFileToProject(path, text).virtualFile
        myFixture.openFileInEditor(f)
        return f to TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
    }

    fun testAddLightIsOneUndoStep() {
        val (f, editor) = open("c/Lights.scene", File("src/test/testData/project/Lights/scenes/Creation Baseline.scene").readText())
        val start = textOf(f)
        val result = SceneComponentEdits.addLight(project, f, LightPreset.SUN, com.badlogic.gdx.math.Vector3(10f, 0f, -4f), reader)
        assertEquals(EditResult.Changed, result.result)
        assertEquals(7L, result.entityId)
        assertEquals("Sun 7", entityDto(f, 7).component(NameComponent::class.java)!!.name)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(start, textOf(f))
    }

    fun testSpotlightBeamEditsEachUndoAndPreserveUnrelatedText() {
        val text = """{"formatVersion":1,"ecs":{"0":{"type":"LIGHT_SPOT","components":{"LightComponent":{"light":{"intensity":1.000}}}}}}"""
        val (f, editor) = open("c/Beam.scene", text)
        val before = textOf(f)
        assertEquals(EditResult.Unchanged, SceneComponentEdits.update(project, f, 0L, "LightComponent", "coneAngle", "45", testMetaFiles()))
        assertTrue(SceneComponentEdits.update(project, f, 0L, "LightComponent", "edgeSoftness", "101", testMetaFiles()) is EditResult.Rejected)
        assertEquals(before, textOf(f))
        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, 0L, "LightComponent", "coneAngle", "60", testMetaFiles()))
        val angle = textOf(f)
                assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, 0L, "LightComponent", "edgeSoftness", "25", testMetaFiles()))
        assertEquals(0.25f, entityDto(f, 0).component(LightComponent::class.java)!!.light.edgeSoftness, 0f)
        UndoManager.getInstance(project).undo(editor)
        assertEquals(angle, textOf(f))
        UndoManager.getInstance(project).undo(editor)
        assertEquals(before, textOf(f))
    }

    fun testMalformedSceneFieldsRejectLightWithoutWrite() {
        val text = """{"format":"abyssus","formatVersion":1,"name":[],"ecs":{}}"""
        val f = myFixture.addFileToProject("c/bad-name.scene", text).virtualFile
        val result = SceneComponentEdits.addLight(project, f, LightPreset.SUN, com.badlogic.gdx.math.Vector3(0f, 0f, 0f), reader)
        assertTrue(result.result is EditResult.Rejected)
        assertFalse(canAddLight(f, reader))
        assertEquals(text, textOf(f))
    }

    fun testUnreadableSceneRejectsLightWithoutWrite() {
        val f = myFixture.addFileToProject("c/bad-light.scene", "not json").virtualFile
        val result = SceneComponentEdits.addLight(project, f, LightPreset.SPOT, com.badlogic.gdx.math.Vector3(0f, 0f, 0f), reader)
        assertTrue(result.result is EditResult.Rejected)
        assertNull(result.entityId)
        assertEquals("not json", textOf(f))
    }

    fun testAddUpdateRemoveEachUndoAsOneStep() {
        val (f, editor) = open("c/Main Scene.scene")
        val undo = UndoManager.getInstance(project)
        val start = textOf(f)

        assertEquals(EditResult.Changed, SceneComponentEdits.add(project, f, 0L, "LightComponent", testMetaFiles()))
        val added = textOf(f)
        assertNotNull(entityDto(f, 0).component(LightComponent::class.java))

        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, 0L, "LightComponent", "intensity", "2", testMetaFiles()))
        assertEquals(2f, entityDto(f, 0).component(LightComponent::class.java)!!.light.intensity, 0f)
        val edited = textOf(f)

        assertEquals(EditResult.Changed, SceneComponentEdits.remove(project, f, 0L, "LightComponent"))
        assertNull(entityDto(f, 0).component(LightComponent::class.java))

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
        assertTrue(SceneComponentEdits.update(project, f, 0L, "PositionComponent", "localPosition.x", "abc", testMetaFiles()) is EditResult.Rejected)
        assertEquals(EditResult.Unchanged, SceneComponentEdits.update(project, f, 0L, "PositionComponent", "localPosition.x", "-3.035308", testMetaFiles()))
        assertTrue(SceneComponentEdits.remove(project, f, 0L, "PickableComponent") is EditResult.Rejected)
        assertEquals(before, textOf(f))
    }

    fun testUpdatingOneValueChangesOneLine() {
        val (f, _) = open("c/Line.scene")
        val before = textOf(f).lines()
        assertEquals(EditResult.Changed, SceneComponentEdits.update(project, f, 4L, "CameraComponent", "camera.fieldOfView", "50", testMetaFiles()))
        val after = textOf(f).lines()
        assertEquals(before.size, after.size)
        assertEquals(1, before.indices.count { before[it] != after[it] })
    }

    fun testUnreadableSceneIsRejected() {
        val f = myFixture.addFileToProject("c/bad.scene", "not json").virtualFile
        assertTrue(SceneComponentEdits.add(project, f, 0L, "LightComponent", testMetaFiles()) is EditResult.Rejected)
        assertEquals("not json", textOf(f))
    }
}
