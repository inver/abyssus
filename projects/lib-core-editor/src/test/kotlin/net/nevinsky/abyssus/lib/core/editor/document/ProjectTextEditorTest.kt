/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.editor.scene.DocumentFormatter
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class ProjectTextEditorTest {
    private val log = NOPLogger.NOP_LOGGER
    private val json = JsonProcessor(log)
    private val parsing = DocumentParsing(json, log)
    private val editor = ProjectTextEditor(parsing, json)
    private val text =
        """{"format":"abyssus","formatVersion":1,"settings":{"export":{"jsonType":"json"}},"activeSceneName":"A",
"name":"P"}"""

    @Test
    fun anEditKeepsTheOtherKeysAndPrintsPretty() {
        val edited = editor.edit(text) { it.copy(physicsEnabled = true) } as TextEditOutcome.Edited
        val after = parsing.project(edited.text)
        assertEquals(true, after.physicsEnabled)
        assertEquals("A", after.activeSceneName)
        assertEquals("json", after.settings!!["export"]["jsonType"].asText())
        assertTrue(edited.text.contains("\n  \"physicsEnabled\": true"))
    }

    @Test
    fun aDeclinedOrEqualEditIsUnchanged() {
        assertEquals(TextEditOutcome.Unchanged, editor.edit(text) { null })
        assertEquals(TextEditOutcome.Unchanged, editor.edit(text) { it.copy() })
    }

    @Test
    fun aTextThatIsNotAProjectIsRefused() {
        assertTrue(editor.edit("not json") { it } is TextEditOutcome.Refused)
    }

    @Test
    fun theFormatterPrintsProjectsAndRefusesGarbage() {
        val formatter = DocumentFormatter(parsing, json)
        assertTrue(
            formatter
                .pretty(text, net.nevinsky.abyssus.lib.core.format.DocumentKind.PROJECT)!!
                .contains("\n  \"name\": \"P\"")
        )
        assertNull(formatter.pretty("nope", net.nevinsky.abyssus.lib.core.format.DocumentKind.PROJECT))
    }
}
