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
    fun disablingAComponentListsItInTheEntityHeader() {
        val edited = editing.setEnabled(sceneText, collider, false) as HeadlessEdit.Edited
        val entity = SceneJson().parse(edited.text)["ecs"]["0"]
        assertEquals(listOf("RenderComponent"), entity["disabledComponents"].map { it.asText() })
        assertTrue(changedLines(sceneText, edited.text).any { it.contains("disabledComponents") })
        // the component entry itself is untouched
        assertEquals(SceneJson().parse(sceneText)["ecs"]["0"]["components"], entity["components"])
        val withoutList = SceneJson().parse(edited.text)
        (withoutList["ecs"]["0"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("disabledComponents")
        assertEquals(SceneJson().compact(SceneJson().parse(sceneText)), SceneJson().compact(withoutList))
    }

    @Test
    fun enablingUnlistsTheComponentAndUndoRestoresTheText() {
        val disabled = (editing.setEnabled(sceneText, collider, false) as HeadlessEdit.Edited).text
        val enabled = editing.setEnabled(disabled, collider, true) as HeadlessEdit.Edited
        assertEquals(sceneText.trimEnd() + "\n", enabled.text.trimEnd() + "\n")
        assertNull(SceneJson().parse(enabled.text)["ecs"]["0"]["disabledComponents"])
    }

    @Test
    fun disablingAnEntityWritesTheFlagBesideItsComponents() {
        val edited = editing.setEnabled(sceneText, EnabledTarget.Entity("2"), false) as HeadlessEdit.Edited
        val entity = SceneJson().parse(edited.text)["ecs"]["2"]
        assertEquals(1, entity["flags"].intValue())
        assertNull(entity["enabled"])
        val keys = entity.fieldNames().asSequence().toList()
        assertEquals("flags", keys.last())
        assertTrue(keys.indexOf("components") < keys.indexOf("flags"))
    }

    @Test
    fun enablingAnEntityKeepsTheOtherFlagBits() {
        val withBits = SceneJson().parse(sceneText).also { (it["ecs"]["2"] as com.fasterxml.jackson.databind.node.ObjectNode).put("flags", 5) }
        val enabled = editing.setEnabled(SceneJson().compact(withBits), EnabledTarget.Entity("2"), true) as HeadlessEdit.Edited
        assertEquals(4, SceneJson().parse(enabled.text)["ecs"]["2"]["flags"].intValue())
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
        val group = SceneJson().parse("""{"type":"GROUP","components":{}}""")
        val object_ = SceneJson().parse("""{"type":"OBJECT","components":{}}""")
        assertFalse(SetEnabledAction().isTogglable(group))
        assertTrue(SetEnabledAction().isTogglable(object_))
    }

    @Test
    fun anUnsupportedDocumentIsRefusedAndUnchanged() {
        val legacy = """{"format":"abyssus","formatVersion":1,"ecs":{"0":{"enabled":false,"components":{"RenderComponent":{}}}}}"""
        val result = editing.setEnabled(legacy, EnabledTarget.Entity("0"), false)
        assertTrue(result.toString(), result is HeadlessEdit.Refused)
        assertEquals(FormatProblem.LEGACY_FIELD, (result as HeadlessEdit.Refused).refusal.rejection?.problem)
    }

    @Test
    fun unknownExtensionKeysSurviveTheRoundTrip() {
        val text = """{"format":"abyssus","formatVersion":1,"ecs":{"0":{"components":{"WindComponent":{"speed":1.50,"extra":{"a":1}}}}}}"""
        val target = EnabledTarget.Component("0", "WindComponent")
        val disabled = (editing.setEnabled(text, target, false) as HeadlessEdit.Edited).text
        val enabled = (editing.setEnabled(disabled, target, true) as HeadlessEdit.Edited).text
        assertEquals(SceneJson().compact(SceneJson().parse(text)), SceneJson().compact(SceneJson().parse(enabled)))
        assertTrue(enabled.contains("1.50"))
    }
}
