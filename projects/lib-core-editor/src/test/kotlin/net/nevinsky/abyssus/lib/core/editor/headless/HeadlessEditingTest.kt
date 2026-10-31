/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.headless

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.meta.AssetFieldValue
import net.nevinsky.abyssus.lib.core.editor.pick.TransformEdit
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.format.FormatProblem
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

/** Spec `headless-scene-editing`: validating and editing native documents as text, on a project folder, with no IDE. */
class HeadlessEditingTest {
    private val project = testProject("Untitled")
    private val sceneText = File(project, "scenes/Main Scene.scene").readText()
    // as the editor prints it: the fixture lacks the final newline every write adds
    private val terrainMeta = File(project, "assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b/meta.json").readText().trimEnd() + "\n"
    private val editing = HeadlessEditing(JsonProcessor(NOPLogger.NOP_LOGGER))

    /** The lines of [after] that differ from [before], which must have as many lines. */
    private fun changedLines(before: String, after: String): List<String> {
        val a = before.lines()
        val b = after.lines()
        assertEquals(a.size, b.size)
        return a.indices.filter { a[it] != b[it] }.map { b[it].trim() }
    }

    // Cases that check the edited text (one changed line, the same text as the scene view writes) wait for the
    // entity serializer (Phase 6.1); the edits themselves are covered on the DTO by the component and pick tests.

    @Test fun anEditEqualToTheCurrentValueIsUnchanged() {
        val same = TransformEdit(position = Vector3(-3.035308f, 0.9123962f, -3.2570944f))
        assertEquals(HeadlessEdit.Unchanged, editing.transform(sceneText, 0L, same))
    }

    @Test fun aLegacySceneIsRefusedWithTheReasonAndNoText() {
        val legacy = sceneText.replaceFirst("\"ecs\": {", "\"ecs\": {\n    \"componentIdentifiers\": {},")
        val refusal = editing.validate(legacy, DocumentKind.SCENE)!!
        assertEquals(FormatProblem.LEGACY_FIELD, refusal.rejection!!.problem)
        assertEquals(
            ResourceEditorMessages().message("unsupportedFormat.LEGACY_FIELD", refusal.rejection!!.path),
            refusal.message,
        )
        val edit = editing.transform(legacy, 0L, TransformEdit(position = Vector3(1f, 0f, 0f)))
        assertEquals(HeadlessEdit.Refused(refusal), edit)
        assertNull(editing.validate(sceneText, DocumentKind.SCENE))
        assertNull(editing.validate(File(project, "Untitled.abss").readText(), DocumentKind.PROJECT))
        assertNull(editing.validate(terrainMeta, DocumentKind.ASSET))
    }

    @Test fun anAssetPropertyEditChangesOnlyThatKey() {
        // an unknown extension key, written as the file's own printer writes it
        val withExtension = terrainMeta.replaceFirst("\"type\": \"TERRAIN\",", "\"type\": \"TERRAIN\",\n  \"x-tool\": {\n    \"kept\": 1.50\n  },")
        val edited = editing.setAssetProperty(withExtension, "size", AssetFieldValue.Int(1600), AssetFieldValue.Int(2048)) as HeadlessEdit.Edited
        // asset documents are still edited as a tree (Phase 5), which reads 1.50 back as 1.5
        assertEquals(listOf("\"kept\": 1.5", "\"size\": 2048,"), changedLines(withExtension, edited.text))
        val refused = editing.setAssetProperty(withExtension, "size", AssetFieldValue.Int(1600), AssetFieldValue.Int(0)) as HeadlessEdit.Refused
        assertEquals(ResourceEditorMessages().message("assetEditError.NOT_POSITIVE"), refused.refusal.message)
        val conflict = editing.setAssetProperty(withExtension, "size", AssetFieldValue.Int(999), AssetFieldValue.Int(2048)) as HeadlessEdit.Refused
        assertEquals(ResourceEditorMessages().message("assetFieldConflict"), conflict.refusal.message)
    }

    @Test fun aRejectedComponentEditIsRefusedWithTheReason() {
        val refused = editing.setComponentField(sceneText, 0L, "PositionComponent", "localPosition.x", "abc") as HeadlessEdit.Refused
        assertEquals(ResourceEditorMessages().message("componentNotANumber", "Position localPosition.x", "abc"), refused.refusal.message)
    }

    @Test fun aMissingEntityOrAnUnmovedSwitchIsUnchanged() {
        assertEquals(HeadlessEdit.Unchanged, editing.setEnabled(sceneText, net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget.Entity(99L), false))
        assertEquals(HeadlessEdit.Unchanged, editing.setFoliage(sceneText, 99L, "foliage_meadow"))
    }

    @Test fun removingAReferencedEntityIsRefusedWithTheRelationship() {
        val refused = editing.removeEntity(sceneText, 3L) as HeadlessEdit.Refused
        assertTrue(refused.refusal.message, refused.refusal.message.contains("lookAtId"))
    }
}
