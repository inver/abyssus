/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEdit
import net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing
import net.nevinsky.abyssus.lib.core.format.FormatProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SetEnabledActionTest {
    private val sceneText = File(testProject("Untitled"), "scenes/Main Scene.scene").readText()
    private val editing = HeadlessEditing()
    private val collider = EnabledTarget.Component("0", "RenderComponent")

    private fun lines(text: String) = text.lines()

    private fun changedLines(before: String, after: String): List<String> {
        val a = lines(before)
        val b = lines(after)
        return b.filter { it !in a }
    }

    @Test
    fun disablingAComponentWritesOnlyItsFlag() {
        val edited = editing.setEnabled(sceneText, collider, false) as HeadlessEdit.Edited
        val render = SceneJson().parse(edited.text)["ecs"]["0"]["components"]["RenderComponent"]
        assertEquals(false, render["enabled"].booleanValue())
        assertTrue(changedLines(sceneText, edited.text).any { it.contains("\"enabled\": false") })
        // every other line of the file is unchanged, bar the separator the new key needs
        assertEquals(lines(sceneText).size + 1, lines(edited.text).size)
        val withoutFlag = SceneJson().parse(edited.text)
        (withoutFlag["ecs"]["0"]["components"]["RenderComponent"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("enabled")
        assertEquals(SceneJson().compact(SceneJson().parse(sceneText)), SceneJson().compact(withoutFlag))
    }

    @Test
    fun enablingRemovesTheKeyAndUndoRestoresTheText() {
        val disabled = (editing.setEnabled(sceneText, collider, false) as HeadlessEdit.Edited).text
        val enabled = editing.setEnabled(disabled, collider, true) as HeadlessEdit.Edited
        assertEquals(sceneText.trimEnd() + "\n", enabled.text.trimEnd() + "\n")
        assertNull(SceneJson().parse(enabled.text)["ecs"]["0"]["components"]["RenderComponent"]["enabled"])
    }

    @Test
    fun disablingAnEntityWritesTheFlagBesideItsComponents() {
        val edited = editing.setEnabled(sceneText, EnabledTarget.Entity("2"), false) as HeadlessEdit.Edited
        val entity = SceneJson().parse(edited.text)["ecs"]["2"]
        assertEquals(false, entity["enabled"].booleanValue())
        val keys = entity.fieldNames().asSequence().toList()
        assertEquals("enabled", keys.last())
        assertTrue(keys.indexOf("components") < keys.indexOf("enabled"))
    }

    @Test
    fun anAlreadyDisabledOrEnabledTargetIsUnchanged() {
        assertEquals(HeadlessEdit.Unchanged, editing.setEnabled(sceneText, collider, true))
        val disabled = (editing.setEnabled(sceneText, collider, false) as HeadlessEdit.Edited).text
        assertEquals(HeadlessEdit.Unchanged, editing.setEnabled(disabled, collider, false))
    }

    @Test
    fun fixedComponentsAndMissingTargetsAreNotSwitched() {
        for (kind in listOf("NameComponent", "PositionComponent", "ParentComponent"))
            assertEquals(kind, HeadlessEdit.Unchanged, editing.setEnabled(sceneText, EnabledTarget.Component("0", kind), false))
        assertEquals(HeadlessEdit.Unchanged, editing.setEnabled(sceneText, EnabledTarget.Component("0", "NoSuchComponent"), false))
        assertEquals(HeadlessEdit.Unchanged, editing.setEnabled(sceneText, EnabledTarget.Entity("nope"), false))
        val action = SetEnabledAction()
        assertTrue(action.isTogglable("RenderComponent"))
        assertTrue(action.isTogglable("net.example.PlaneComponent"))
        assertFalse(action.isTogglable("net.nevinsky.abyssus.lib.core.ecs.component.NameComponent"))
    }

    @Test
    fun aGroupEntityIsNotSwitched() {
        val group = SceneJson().parse("""{"components":{"TypeComponent":{"type":"GROUP"}}}""")
        val object_ = SceneJson().parse("""{"components":{"TypeComponent":{"type":"OBJECT"}}}""")
        assertFalse(SetEnabledAction().isTogglable(group))
        assertTrue(SetEnabledAction().isTogglable(object_))
    }

    @Test
    fun anUnsupportedDocumentIsRefusedAndUnchanged() {
        val legacy = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"RenderComponent":{"enabled":"no"}}}}}}"""
        val result = editing.setEnabled(legacy, EnabledTarget.Entity("0"), false)
        assertTrue(result.toString(), result is HeadlessEdit.Refused)
        assertEquals(FormatProblem.ENABLED_FLAG, (result as HeadlessEdit.Refused).refusal.rejection?.problem)
    }

    @Test
    fun unknownExtensionKeysSurviveTheRoundTrip() {
        val text = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"WindComponent":{"speed":1.50,"extra":{"a":1}}}}}}}"""
        val target = EnabledTarget.Component("0", "WindComponent")
        val disabled = (editing.setEnabled(text, target, false) as HeadlessEdit.Edited).text
        val enabled = (editing.setEnabled(disabled, target, true) as HeadlessEdit.Edited).text
        assertEquals(SceneJson().compact(SceneJson().parse(text)), SceneJson().compact(SceneJson().parse(enabled)))
        assertTrue(enabled.contains("1.50"))
    }
}
