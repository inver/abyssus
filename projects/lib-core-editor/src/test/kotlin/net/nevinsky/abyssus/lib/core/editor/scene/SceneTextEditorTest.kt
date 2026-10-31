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

/** The text transform with a stand-in serializer; the real one is checked by the round-trip tests in Phase 6. */
class SceneTextEditorTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val text = """{"format":"abyssus","formatVersion":1,"name":"A","ecs":{}}"""

    private class FakeSerializer(json: JsonProcessor, private val fail: Boolean = false) : SceneSerializer(json) {
        var calls = 0
        override fun serialize(scene: SceneDto): String {
            calls++
            if (fail) throw IllegalStateException("cannot write")
            return "written:${scene.name}"
        }
    }

    private fun editor(serializer: SceneSerializer) = SceneTextEditor(SceneParser(json, ComponentRegistry()), serializer)

    @Test
    fun aMutationIsSerializedIntoTheNewText() {
        val outcome = editor(FakeSerializer(json)).edit(text) { scene -> true.also { scene.ecs.clear() } }
        assertEquals(TextEditOutcome.Edited("written:A"), outcome)
    }

    @Test
    fun aDeclinedMutationIsUnchangedAndNothingIsSerialized() {
        val serializer = FakeSerializer(json)
        assertEquals(TextEditOutcome.Unchanged, editor(serializer).edit(text) { false })
        assertEquals(0, serializer.calls)
    }

    @Test
    fun aLegacyOrMalformedDocumentIsRefusedAndNeverMutated() {
        var ran = false
        for (bad in listOf("""{"ecs":{}}""", "not json", """{"format":"abyssus","formatVersion":1,"ecs":{"componentIdentifiers":{}}}""")) {
            val outcome = editor(FakeSerializer(json)).edit(bad) { true.also { ran = true } }
            assertTrue(bad, outcome is TextEditOutcome.Refused)
        }
        assertFalse(ran)
    }

    @Test
    fun aSerializerFailureIsARefusalNotAnException() {
        val outcome = editor(FakeSerializer(json, fail = true)).edit(text) { true }
        assertTrue(outcome is TextEditOutcome.Refused)
        assertEquals("cannot write", (outcome as TextEditOutcome.Refused).cause.message)
    }

    @Test
    fun aSettingsCopyIsSerializedAndAnEqualOrDeclinedOneIsNot() {
        val serializer = FakeSerializer(json)
        val sut = editor(serializer)
        assertEquals(TextEditOutcome.Edited("written:renamed"), sut.replace(text) { it.copy(name = "renamed") })
        assertEquals(TextEditOutcome.Unchanged, sut.replace(text) { it.copy() })
        assertEquals(TextEditOutcome.Unchanged, sut.replace(text) { null })
        assertEquals(1, serializer.calls)
    }
}
