/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** [SchemaMerge], the rule [ComponentSchemas] applies, as a pure function of the project's and contributed schemas. */
class ComponentSchemasTest {
    private val merge = SchemaMerge()
    private val customSchema = File("src/test/testData/project/Custom/abyssus/components.schema.json").readText()
    private val project = ProjectSchemaText("Custom/abyssus/components.schema.json", customSchema)

    private fun schema(name: String, field: String) =
        """{"version": 1, "components": [{"name": "$name", "label": "${name.removeSuffix("Component")}", "fields": [{"name": "$field", "type": "decimal", "default": 1}]}]}"""

    private fun plugin(name: String, text: String?) = ContributedSchemaText(name, "/schemas/markers.json", text)

    @Test fun theProjectWinsPerNameWithOneMessageNamingThePlugin() {
        val snapshot = merge.merge(project, listOf(plugin("Plane Tools", schema("PlaneComponent", "wingspan"))))
        assertEquals(listOf("PlaneComponent"), snapshot.components.map { it.name })
        assertEquals("lineLength", snapshot.components.single().fields.first().name)
        assertEquals(1, snapshot.problems.size)
        assertTrue(snapshot.problems.single(), snapshot.problems.single().contains("PlaneComponent") && snapshot.problems.single().contains("Plane Tools"))
    }

    @Test fun aBrokenFileGivesOneMessageNamingIt() {
        val snapshot = merge.merge(project.copy(text = "{ not json"), listOf(plugin("Markers", schema("MarkerComponent", "size"))))
        assertEquals(listOf("MarkerComponent"), snapshot.components.map { it.name })
        assertEquals(1, snapshot.problems.size)
        assertTrue(snapshot.problems.single(), snapshot.problems.single().startsWith("Custom/abyssus/components.schema.json: "))
        // the plane is then unknown to the editor: shown read only
        assertEquals(null, snapshot.editor.kindOf("PlaneComponent"))
    }

    @Test fun aRemovedContributionDropsItsComponents() {
        val with = merge.merge(null, listOf(plugin("Markers", schema("MarkerComponent", "size"))))
        assertEquals(listOf("MarkerComponent"), with.components.map { it.name })
        val without = merge.merge(null, emptyList())
        assertEquals(emptyList<String>(), without.components.map { it.name })
        assertEquals(null, without.editor.kindOf("MarkerComponent"))
    }

    @Test fun contributionsAreEditedLikeProjectComponents() {
        val snapshot = merge.merge(project, listOf(plugin("Markers", schema("MarkerComponent", "size"))))
        assertEquals(listOf("PlaneComponent", "MarkerComponent"), snapshot.components.map { it.name })
        assertEquals(emptyList<String>(), snapshot.problems)
        assertEquals("Marker", snapshot.editor.kindOf("MarkerComponent")!!.label)
    }

    @Test fun aBuiltInNameAndAMissingResourceAreReported() {
        val snapshot = merge.merge(null, listOf(plugin("Odd", schema("NameComponent", "x")), plugin("Gone", null)))
        assertEquals(emptyList<String>(), snapshot.components.map { it.name })
        assertEquals(2, snapshot.problems.size)
        assertTrue(snapshot.problems[0], snapshot.problems[0].contains("NameComponent"))
        assertTrue(snapshot.problems[1], snapshot.problems[1].contains("Gone") && snapshot.problems[1].contains("markers.json"))
    }
}
