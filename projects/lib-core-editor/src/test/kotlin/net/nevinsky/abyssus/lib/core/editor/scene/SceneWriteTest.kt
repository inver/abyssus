/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class SceneWriteTest {
    private val registry = ComponentRegistry()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER, listOf(entityWriter(registry)))
    private val parser = SceneParser(json, registry)
    private val serializer = json

    private val reader = json.binderWith(net.nevinsky.abyssus.lib.core.dto.EntityDtoModule(registry))

    /**
     * Reads written text back without the marker check, which G9 removes: [SceneDto] has no `format` fields to write.
     */
    private fun reparse(written: String) =
        reader.bind(json.readObject(written), net.nevinsky.abyssus.lib.core.dto.SceneDto::class.java)

    private val text =
        """
        {
          "format": "abyssus",
          "formatVersion": 1,
          "id": "s1",
          "name": "A",
          "ecs": {
            "1": {
              "type": "OBJECT",
              "flags": 1,
              "disabledComponents": ["NameComponent", "Mystery"],
              "components": {
                "NameComponent": { "name": "Hull" },
                "Mystery": { "k": 1.50 }
              }
            }
          }
        }
        """
            .trimIndent()

    @Test
    fun aSceneRoundTripsToTheSameScene() {
        val scene = parser.parse(text)
        val written = serializer.pretty(scene)
        assertEquals(scene.ecs.keys, reparse(written).ecs.keys)
        val again = reparse(written).ecs.getValue(1L)
        val header = again.header()
        assertEquals(false, header.entityEnabled)
        assertEquals(setOf(NameComponent::class.java), header.disabledComponents)
        assertEquals("Hull", again.component(NameComponent::class.java)?.name)
        val raw = again.component(RawComponentsWrapper::class.java)!!.entries.single()
        assertEquals("Mystery", raw.name)
        assertTrue(raw.disabled)
        assertTrue(raw.json.contains("1.5"))
    }

    @Test
    fun theTextIsPrettyPrintedAndStable() {
        val written = serializer.pretty(parser.parse(text))
        assertTrue(written.endsWith("\n"))
        assertTrue(written.lines().size > 5)
        assertEquals(written, serializer.pretty(reparse(written)))
    }
}
