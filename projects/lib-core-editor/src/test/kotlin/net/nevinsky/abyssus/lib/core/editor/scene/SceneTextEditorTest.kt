/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/** The text transform: parse, edit, write back through [JsonProcessor.pretty]. */
class SceneTextEditorTest {
    private val registry = ComponentRegistry()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER, registry)
    private val text = """{"format":"abyssus","formatVersion":1,"name":"A","ecs":{}}"""

    private fun editor() = SceneTextEditor(SceneParser(json, registry), json)

    @Test
    fun aMutationIsWrittenAsPrettyText() {
        val outcome = editor().edit(text) { scene -> true.also { scene.ecs.clear() } }
        val edited = (outcome as TextEditOutcome.Edited).text
        assertTrue(edited.endsWith("\n"))
        assertTrue(edited.contains("\"name\" : \"A\""))
    }

    @Test
    fun aDeclinedMutationIsUnchanged() {
        assertEquals(TextEditOutcome.Unchanged, editor().edit(text) { false })
    }

    @Test
    fun aLegacyOrMalformedDocumentIsRefusedAndNeverMutated() {
        var ran = false
        for (bad in listOf("""{"ecs":{}}""", "not json", """{"format":"abyssus","formatVersion":1,"ecs":{"componentIdentifiers":{}}}""")) {
            val outcome = editor().edit(bad) { true.also { ran = true } }
            assertTrue(bad, outcome is TextEditOutcome.Refused)
        }
        assertFalse(ran)
    }

    @Test
    fun aSettingsCopyIsWrittenAndAnEqualOrDeclinedOneIsNot() {
        val sut = editor()
        val edited = sut.replace(text) { it.copy(name = "renamed") }
        assertTrue((edited as TextEditOutcome.Edited).text.contains("renamed"))
        assertEquals(TextEditOutcome.Unchanged, sut.replace(text) { it.copy() })
        assertEquals(TextEditOutcome.Unchanged, sut.replace(text) { null })
    }
}
