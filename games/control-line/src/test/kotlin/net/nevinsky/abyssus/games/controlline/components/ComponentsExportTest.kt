/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.components

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.runtime.schema.exportSchema
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ComponentsExportTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun export(): ByteArray = Files.readAllBytes(exportSchema(ControlLineComponents(), temp.newFolder().toPath()))

    private fun JsonNode.component(name: String): JsonNode =
        get("components").single { it.get("name").asText() == name }

    private fun JsonNode.field(name: String): JsonNode = get("fields").single { it.get("name").asText() == name }

    @Test
    fun schemaListsBothComponentsWithGroupsAndLimits() {
        val schema = ObjectMapper().readTree(export())
        val names = schema.get("components").map { it.get("name").asText() }
        assertTrue(names.toString(), names.containsAll(listOf("PilotComponent", "PlaneComponent", "RigidBodyComponent", "ColliderComponent")))

        val plane = schema.component("PlaneComponent")
        assertEquals("Plane", plane.get("label").asText())
        val line = plane.field("lineLength")
        assertEquals("Lines", line.get("group").asText())
        assertEquals(5.0, line.get("min").asDouble(), 0.0)
        assertEquals(30.0, line.get("max").asDouble(), 0.0)
        assertEquals("Aerodynamics", plane.field("wingArea").get("group").asText())
        assertTrue(plane.field("wingArea").get("minExclusive").asBoolean())
        assertEquals(listOf("SPEED", "STUNT", "TRAINER"), plane.field("planeClass").get("choices").map { it.asText() })

        val pilot = schema.component("PilotComponent")
        assertEquals("Handle", pilot.field("handleHeight").get("group").asText())
        assertEquals(0.0, pilot.field("handleHeight").get("min").asDouble(), 0.0)
    }

    @Test
    fun twoExportsAreByteIdentical() = assertArrayEquals(export(), export())
}
