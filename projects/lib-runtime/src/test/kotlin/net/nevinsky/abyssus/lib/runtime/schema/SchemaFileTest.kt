/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.runtime.schema

import net.nevinsky.abyssus.lib.runtime.testProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class SchemaFileTest {
    private val custom = testProject("Custom").toPath()

    private fun <T> inCopy(withSchema: Boolean, test: (Path) -> T): T {
        val temp = Files.createTempDirectory("schema-export")
        try {
            custom.toFile().copyRecursively(temp.toFile())
            if (!withSchema) temp.resolve("abyssus").toFile().deleteRecursively()
            return test(temp)
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    private fun snapshot(dir: Path): Map<String, List<Byte>> = Files.walk(dir).use { paths ->
        paths.filter { Files.isRegularFile(it) && (it.toString().endsWith(".scene") || it.toString().endsWith(".abss")) }
            .toList().associate { dir.relativize(it).toString() to Files.readAllBytes(it).toList() }
    }

    @Test fun exportReproducesTheFixtureByteForByteAndRepeatsIdentically() = inCopy(withSchema = true) { dir ->
        val expected = Files.readAllBytes(custom.resolve(SCHEMA_FILE))
        val before = snapshot(dir)
        val file = exportSchema(PlaneRegistry(), dir)
        assertArrayEquals(expected, Files.readAllBytes(file))
        val first = Files.readAllBytes(file)
        exportSchema(PlaneRegistry(), dir)
        assertArrayEquals(first, Files.readAllBytes(file))
        assertEquals(before, snapshot(dir))
    }

    @Test fun exportCreatesTheAbyssusFolder() = inCopy(withSchema = false) { dir ->
        assertFalse(Files.exists(dir.resolve("abyssus")))
        val file = exportSchema(PlaneRegistry(), dir)
        assertEquals(dir.resolve(SCHEMA_FILE), file)
        assertTrue(Files.isRegularFile(file))
    }

    @Test fun mainWritesThroughTheRegistryClassName() = inCopy(withSchema = false) { dir ->
        main(arrayOf(PlaneRegistry::class.java.name, dir.toString()))
        assertArrayEquals(Files.readAllBytes(custom.resolve(SCHEMA_FILE)), Files.readAllBytes(dir.resolve(SCHEMA_FILE)))
    }

    @Test fun theFixtureParsesToThePlaneSchema() {
        val parsed = SchemaFile().parse(Files.readString(custom.resolve(SCHEMA_FILE)))
        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals(listOf(ComponentSchemaReader().read(PlaneComponent::class.java)), parsed.components)
        val line = parsed.components.single().fields.first()
        assertEquals(listOf(FieldType.DECIMAL, 18f, 5.0, 30.0, "Lines"), listOf(line.type, line.default, line.min, line.max, line.group))
    }

    @Test fun anUnknownVersionIsRejected() {
        val parsed = SchemaFile().parse("""{"version": 2, "components": [{"name": "PlaneComponent", "fields": []}]}""")
        assertEquals(emptyList<ComponentSchema>(), parsed.components)
        assertEquals(1, parsed.problems.size)
        assertTrue(parsed.problems.single(), parsed.problems.single().contains("version"))
    }

    @Test fun anUnknownFieldTypeRejectsOnlyItsComponent() {
        val parsed = SchemaFile().parse(
            """{"version": 1, "components": [
              {"name": "WingComponent", "fields": [{"name": "span", "type": "matrix"}]},
              {"name": "MarkerComponent", "fields": [{"name": "size", "type": "decimal", "default": 2, "minExclusive": true, "min": 0}]}]}""",
        )
        assertEquals(listOf("MarkerComponent"), parsed.components.map { it.name })
        assertEquals(SchemaField("size", "size", FieldType.DECIMAL, 2f, min = 0.0, minExclusive = true), parsed.components.single().fields.single())
        assertEquals(listOf("component WingComponent field span has the unknown type matrix"), parsed.problems)
    }

    @Test fun anExclusiveMinimumIsWrittenNextToMin() {
        val schema = ComponentSchema("MarkerComponent", "x.Marker", "Marker", listOf(SchemaField("size", "Size", FieldType.DECIMAL, 2f, min = 0.0, minExclusive = true)))
        val text = SchemaFile().write(listOf(schema))
        assertTrue(text, text.contains("\"min\": 0,\n          \"minExclusive\": true"))
        assertEquals(listOf(schema), SchemaFile().parse(text).components)
    }
}
