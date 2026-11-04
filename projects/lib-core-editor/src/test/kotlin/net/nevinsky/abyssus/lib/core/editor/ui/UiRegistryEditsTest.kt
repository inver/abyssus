package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.dto.ProjectDto
import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import net.nevinsky.abyssus.lib.core.editor.document.ProjectTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class UiRegistryEditsTest {
    private val log = NOPLogger.NOP_LOGGER
    private val json = JsonProcessor(log)
    private val parsing = DocumentParsing(json, log)
    private val edits = UiRegistryEdits()
    private val project = ProjectDto(name = "P", uiAssetNames = listOf("menus"))

    @Test
    fun registeringAppendsOnce() {
        val added = edits.register(project, "hud")!!
        assertEquals(listOf("menus", "hud"), added.uiAssetNames)
        assertNull(edits.register(added, "hud"))
        assertNull(edits.register(project, " "))
    }

    @Test
    fun unregisteringRemovesOnlyThatName() {
        assertEquals(emptyList<String>(), edits.unregister(project, "menus")!!.uiAssetNames)
        assertNull(edits.unregister(project, "other"))
    }

    @Test
    fun anEmptiedListIsNotWritten() {
        val bare = """{"formatVersion":1,"name":"P"}"""
        val editor = ProjectTextEditor(parsing, json)
        val with = editor.edit(bare) { edits.register(it, "menus") } as TextEditOutcome.Edited
        assertTrue(with.text.contains("\"uiAssetNames\""))
        val without = editor.edit(with.text) { edits.unregister(it, "menus") } as TextEditOutcome.Edited
        assertTrue(!without.text.contains("uiAssetNames"))
        assertEquals(TextEditOutcome.Unchanged, editor.edit(bare) { edits.unregister(it, "menus") })
    }

    @Test
    fun otherProjectKeysSurviveAnEdit() {
        val text =
            """{"formatVersion":1,"name":"P","activeSceneName":"A","mainCamera":{"near":0.1},
"uiAssetNames":["menus"]}"""
        val edited = ProjectTextEditor(parsing, json).edit(text) { edits.register(it, "hud") } as TextEditOutcome.Edited
        val after = parsing.project(edited.text)
        assertEquals("A", after.activeSceneName)
        assertEquals(0.1f, after.mainCamera!!.near!!, 0f)
        assertEquals(listOf("menus", "hud"), after.uiAssetNames)
    }
}
