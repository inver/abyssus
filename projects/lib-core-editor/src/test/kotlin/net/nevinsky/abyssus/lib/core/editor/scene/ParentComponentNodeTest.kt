/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.entityWriter
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.scene.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

/** `ParentComponent.node` is optional: present it reads back and prints, absent it is not written; `DUMMY` binds. */
class ParentComponentNodeTest {
    private val registry = ComponentRegistry()
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER, listOf(entityWriter(registry)))
    private val parser = SceneParser(json, registry)

    private fun scene(parent: String) =
        """
        {
          "formatVersion": 1,
          "id": 1,
          "name": "S",
          "ecs": {
            "0": { "type": "OBJECT", "components": { "PositionComponent": {} } },
            "1": { "type": "DUMMY", "components": {
              "NameComponent": { "name": "Dummy" },
              "PositionComponent": {},
              "ParentComponent": $parent
            } }
          }
        }
        """.trimIndent()

    @Test
    fun nodeReadsBackAndPrintsUnchanged() {
        val first = json.pretty(parser.parse(scene("""{ "parentEntityId": 0, "node": "LeftWing" }""")))
        val dummy = parser.parse(first).ecs.getValue(1L)
        val link = dummy.components.filterIsInstance<ParentComponent>().single()
        assertEquals(0, link.parentEntityId)
        assertEquals("LeftWing", link.node)
        assertEquals(EntityType.DUMMY, dummy.components.filterIsInstance<HeaderComponent>().single().type)
        assertTrue(first, first.contains("\"node\": \"LeftWing\""))
        assertEquals(first, json.pretty(parser.parse(first)))
    }

    @Test
    fun missingNodeIsNotWritten() {
        val first = json.pretty(parser.parse(scene("""{ "parentEntityId": 0 }""")))
        val link = parser.parse(first).ecs.getValue(1L).components.filterIsInstance<ParentComponent>().single()
        assertNull(link.node)
        assertFalse(first, first.contains("\"node\""))
        assertTrue(first, first.contains("\"DUMMY\""))
        assertEquals(first, json.pretty(parser.parse(first)))
    }
}
