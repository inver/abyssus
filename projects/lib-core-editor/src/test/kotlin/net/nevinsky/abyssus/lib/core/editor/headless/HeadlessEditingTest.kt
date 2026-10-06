/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.headless

import net.nevinsky.abyssus.lib.core.format.FormatProblem
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.content.Vec3
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.meta.FieldValue
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.core.editor.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec `headless-scene-editing`: validating and editing native documents as text, on a project folder, with no IDE. */
class HeadlessEditingTest {
    private val project = testProject("Untitled")
    private val sceneText = File(project, "scenes/Main Scene.scene").readText()
    // as the editor prints it: the fixture lacks the final newline every write adds
    private val terrainMeta = File(project, "assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/meta.json").readText().trimEnd() + "\n"
    private val editing = HeadlessEditing()

    /** The lines of [after] that differ from [before], which must have as many lines. */
    private fun changedLines(before: String, after: String): List<String> {
        val a = before.lines()
        val b = after.lines()
        assertEquals(a.size, b.size)
        return a.indices.filter { a[it] != b[it] }.map { b[it].trim() }
    }

    @Test fun moveAnEntityChangesOnlyItsXPosition() {
        val model0 = SceneJson().parse(sceneText)["ecs"]["0"]["components"]["PositionComponent"]["localPosition"]
        val edit = TransformEdit(position = Vec3(1f, model0["y"].floatValue(), model0["z"].floatValue()))
        val moved = editing.transform(sceneText, "0", edit) as HeadlessEdit.Edited
        assertEquals(listOf("\"x\": 1.0,"), changedLines(sceneText, moved.text))
        assertEquals(1.0, SceneJson().parse(moved.text)["ecs"]["0"]["components"]["PositionComponent"]["localPosition"]["x"].doubleValue(), 0.0)
        assertEquals(HeadlessEdit.Unchanged, editing.transform(moved.text, "0", edit))
    }

    @Test fun aLegacySceneIsRefusedWithTheReasonAndNoText() {
        val legacy = sceneText.replaceFirst("\"ecs\": {", "\"ecs\": {\n    \"componentIdentifiers\": {},")
        val refusal = editing.validate(legacy, DocumentKind.SCENE)!!
        assertEquals(FormatProblem.LEGACY_FIELD, refusal.rejection!!.problem)
        assertEquals(
            ResourceEditorMessages().message("unsupportedFormat.LEGACY_FIELD", refusal.rejection!!.path),
            refusal.message,
        )
        val edit = editing.transform(legacy, "0", TransformEdit(position = Vec3(1f, 0f, 0f)))
        assertEquals(HeadlessEdit.Refused(refusal), edit)
        assertNull(editing.validate(sceneText, DocumentKind.SCENE))
        assertNull(editing.validate(File(project, "Untitled.abss").readText(), DocumentKind.PROJECT))
        assertNull(editing.validate(terrainMeta, DocumentKind.ASSET))
    }

    @Test fun anAssetPropertyEditChangesOnlyThatKey() {
        // an unknown extension key, written as the file's own printer writes it
        val withExtension = terrainMeta.replaceFirst("\"type\": \"TERRAIN\",", "\"type\": \"TERRAIN\",\n  \"x-tool\": {\n    \"kept\": 1.50\n  },")
        val edited = editing.setAssetProperty(withExtension, "size", FieldValue.Int(1600), FieldValue.Int(2048)) as HeadlessEdit.Edited
        assertEquals(listOf("\"size\": 2048,"), changedLines(withExtension, edited.text))
        assertTrue(edited.text.contains("\"kept\": 1.50"))
        val refused = editing.setAssetProperty(withExtension, "size", FieldValue.Int(1600), FieldValue.Int(0)) as HeadlessEdit.Refused
        assertEquals(ResourceEditorMessages().message("assetEditError.NOT_POSITIVE"), refused.refusal.message)
        val conflict = editing.setAssetProperty(withExtension, "size", FieldValue.Int(999), FieldValue.Int(2048)) as HeadlessEdit.Refused
        assertEquals(ResourceEditorMessages().message("assetFieldConflict"), conflict.refusal.message)
    }

    @Test fun aComponentEditGoesThroughTheComponentEditor() {
        val edited = editing.setComponentField(sceneText, "0", "PositionComponent", "localPosition.x", "1") as HeadlessEdit.Edited
        assertEquals(listOf("\"x\": 1,"), changedLines(sceneText, edited.text))
        val refused = editing.setComponentField(sceneText, "0", "PositionComponent", "localPosition.x", "abc") as HeadlessEdit.Refused
        assertEquals(ResourceEditorMessages().message("componentNotANumber", "Position localPosition.x", "abc"), refused.refusal.message)
    }
}
