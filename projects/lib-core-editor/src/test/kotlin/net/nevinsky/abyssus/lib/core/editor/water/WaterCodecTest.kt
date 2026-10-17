/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.water

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.water.WaterField
import net.nevinsky.abyssus.lib.core.water.WaterPreset
import net.nevinsky.abyssus.lib.core.water.WaterPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WaterCodecTest {
    private val codec = WaterCodec()
    private val json = SceneJson()

    private fun read(abyssus: String?) = codec.read(abyssus?.let(json::parse))

    @Test fun absentOrEmptyExtensionsHaveNoWater() {
        assertEquals(emptyList<WaterSurface>(), read(null).surfaces)
        assertEquals(emptyList<WaterSurface>(), read("{}").surfaces)
        assertEquals(emptyList<WaterSurface>(), read("""{"waterSurfaces":{}}""").surfaces)
        assertEquals(emptyList<WaterSurface>(), read("""{"other":1}""").surfaces)
        assertFalse(read("{}").incompatible)
    }

    @Test fun malformedContainersAreIncompatible() {
        for (text in listOf("[]", "3", "\"x\"", "null", """{"waterSurfaces":[]}""", """{"waterSurfaces":null}""")) {
            val result = read(text)
            assertTrue(text, result.incompatible)
            assertTrue(result.surfaces.isEmpty())
        }
    }

    @Test fun savedSurfacesRoundTripWithTheirIdsAndOrder() {
        val lake = WaterPresets().defaults(WaterPreset.LAKE, "id-1", "Lake", 1.0, 2.0, 3.0)
        val sea = WaterPresets().defaults(WaterPreset.SEA, "id-2", "Sea 2")
        val surfaces = json.parseObject("{}")
        surfaces.set<com.fasterxml.jackson.databind.JsonNode>("id-1", codec.node(lake))
        surfaces.set<com.fasterxml.jackson.databind.JsonNode>("id-2", codec.node(sea.copy(enabled = false)))
        val result = codec.read(json.parseObject("{}").also { it.set<com.fasterxml.jackson.databind.JsonNode>(WATER_SURFACES, surfaces) })
        assertEquals(listOf(lake, sea.copy(enabled = false)), result.valid)
        assertEquals(listOf("id-1", "id-2"), result.surfaces.map { it.id })
    }

    @Test fun missingScalarsTakeTheRecordedPresetsDefaults() {
        val surface = codec.surface("a", json.parse("""{"preset":"SEA","position":{"y":5}}"""))
        val settings = (surface as ValidWater).settings
        val defaults = WaterPresets().defaults(WaterPreset.SEA, "a", "a")
        assertEquals(defaults.copy(level = 5.0), settings)
    }

    @Test fun unknownPresetAndWrongTypesMakeAnInvalidRow() {
        for (text in listOf(
            """{"name":"x","preset":"OCEAN"}""", """{"name":"x"}""", """{"preset":"LAKE","width":"wide"}""",
            """{"preset":"LAKE","position":{"y":null}}""", """{"preset":"LAKE","enabled":"yes"}""",
            """{"preset":"LAKE","width":0}""", """{"preset":"LAKE","foamAmount":2}""", """{"preset":"LAKE","clarity":-1}""",
            "5", "[]",
        )) {
            val surface = codec.surface("a", json.parse(text))
            assertTrue(text, surface is InvalidWater)
            assertTrue((surface as InvalidWater).problems.isNotEmpty())
        }
    }

    @Test fun invalidSurfaceKeepsItsNameForTheRow() {
        val surface = codec.surface("a", json.parse("""{"name":"Broken","preset":"LAKE","width":-4}"""))
        assertEquals("Broken", surface.name)
        assertEquals("a", codec.surface("a", json.parse("""{"preset":"LAKE","width":-4}""")).name)
    }

    @Test fun oneInvalidSurfaceDoesNotHideTheOthers() {
        val result = read("""{"waterSurfaces":{"a":{"preset":"LAKE"},"b":{"preset":"X"},"c":{"preset":"SEA","position":{"y":9}}}}""")
        assertEquals(listOf("a", "b", "c"), result.surfaces.map { it.id })
        assertEquals(listOf("a", "c"), result.valid.map { it.id })
        assertEquals(9.0, result.valid[1].level, 0.0)
    }

    @Test fun surfacesKeepIndependentLevels() {
        val result = read("""{"waterSurfaces":{"a":{"preset":"SEA","position":{"y":0}},"b":{"preset":"LAKE","position":{"y":12.5}}}}""")
        assertEquals(listOf(0.0, 12.5), result.valid.map { it.level })
    }

    @Test fun nodeWritesEveryDocumentedField() {
        val node = codec.node(WaterPresets().defaults(WaterPreset.LAKE, "a", "Lake"))
        val expected = setOf(
            "name", "preset", "position", "width", "length", "tint", "clarity", "waveAmplitude", "waveLength", "waveSpeed",
            "foamAmount", "foamWidth", "enabled",
        )
        assertEquals(expected, node.fieldNames().asSequence().toSet())
        assertEquals(setOf("x", "y", "z"), node.get("position").fieldNames().asSequence().toSet())
        assertEquals(setOf("r", "g", "b"), node.get("tint").fieldNames().asSequence().toSet())
        for (field in WaterField.entries) {
            var current: com.fasterxml.jackson.databind.JsonNode = node
            for (key in field.path) current = current.get(key)
            assertTrue(field.toString(), current.isNumber)
        }
    }

    @Test fun untitledMainSceneHasNoWaterAndReadingChangesNothing() {
        val file = File("../plugin-abyssus/src/test/testData/project/Untitled/scenes/Main Scene.scene")
        val text = file.readText()
        val scene = parseScene(text)
        assertEquals(null, scene.water)
        assertTrue(sceneContentOf(scene).water.isEmpty())
        assertEquals(text, file.readText())
    }

    @Test fun sceneContentCarriesSavedWaterAndInvalidRows() {
        val text = """{"format":"abyssus","formatVersion":1,"abyssus":{"waterSurfaces":{"a":{"name":"Lake","preset":"LAKE"},"b":{"preset":"?"}}}}"""
        val water = sceneContentOf(parseScene(text)).water
        assertEquals(listOf("Lake", "b"), water.map { it.name })
        assertTrue(water[0] is ValidWater)
        assertTrue(water[1] is InvalidWater)
    }
}
