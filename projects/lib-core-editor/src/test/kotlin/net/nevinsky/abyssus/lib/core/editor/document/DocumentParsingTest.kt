/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class DocumentParsingTest {
    private val parsing = DocumentParsing(JsonProcessor(NOPLogger.NOP_LOGGER), NOPLogger.NOP_LOGGER)

    @Test
    fun headerAdmissionMatchesTheNativeValidatorBeforeDtoCoercion() {
        val headers = listOf(
            "\"format\":\"abyssus\",\"formatVersion\":1",
            "\"formatVersion\":1",
            "\"format\":\"other\",\"formatVersion\":1",
            "\"format\":7,\"formatVersion\":1",
            *listOf("1.0", "\"1\"", "2", "1e0", "null").map { "\"format\":\"abyssus\",\"formatVersion\":$it" }.toTypedArray(),
            "\"format\":\"abyssus\"",
        )
        for (kind in DocumentKind.entries) for (header in headers) {
            val text = "{$header}"
            val expected = runCatchingKeepingCancellation {
                AbyssusDocumentFormat().requireSupported(SceneJson().parse(text), kind)
            }.isSuccess
            assertEquals("$kind $text", expected, runCatchingKeepingCancellation { parsing.requireSupported(text, kind) }.isSuccess)
        }
        for (text in listOf("{", "[]", """{"format":"abyssus","formatVersion":1,"ecs":{"componentIdentifiers":{}}}""")) {
            assertNull(runCatchingKeepingCancellation { parsing.parse(text) }.getOrNull())
        }
    }

    @Test
    fun projectUiNamesKeepOnlyTextualEntriesAndMalformedListsStayEmpty() {
        val header = "\"format\":\"abyssus\",\"formatVersion\":1"
        val mixed = "{$header,\"uiAssetNames\":[\"one\",4,null,{},false,\"two\",[\"ignored\"]]}"
        assertEquals(listOf("one", "two"), parsing.project(mixed).uiAssetNames)
        assertEquals(listOf("one", "two"), parsing.uiAssetNames(mixed))
        assertEquals(listOf("one", "two"), parsing.uiAssetNames(mixed.dropLast(1) + ",\"scenes\":false}"))
        for (member in listOf("", ",\"uiAssetNames\":null", ",\"uiAssetNames\":{}", ",\"uiAssetNames\":\"one\"")) {
            assertEquals(emptyList<String>(), parsing.uiAssetNames("{$header$member}"))
        }
    }

    @Test
    fun foliageKeepsWrongTypeDefaultsAndSkipsMalformedLayerAndModelEntries() {
        val text = """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{
            "terrain":12,"maskResolution":"16","layers":[null,{"id":"2"},{"id":1.0},
            {"id":3,"density":"0.9","seed":4.0,"kind":false,"models":[null,{"asset":7},{"asset":"tree","weight":"2"}],
             "scale":{"min":"9","max":2},"collider":{"future":1.00}}]}}"""
        val settings = parsing.foliageMeta(text)
        assertEquals("", settings.terrain)
        assertEquals(256, settings.maskResolution)
        val layer = settings.layers.single()
        assertEquals(3, layer.id)
        assertEquals(0.05f, layer.density, 0f)
        assertEquals(0, layer.seed)
        assertEquals("OBJECT", layer.kind)
        assertEquals("tree", layer.models.single().asset)
        assertEquals(1f, layer.models.single().weight, 0f)
        assertEquals(0.8f, layer.scale.min, 0f)
        assertEquals(2f, layer.scale.max, 0f)
        assertEquals("1.00", layer.collider!!["future"].toString())
    }

    @Test
    fun narrowAssetReadsPreserveStrictReferencesAndScalarIdentityText() {
        val text = """{"format":"abyssus","formatVersion":1,"type":"SKYBOX_PROCEDURAL","uuid":123,"additional":{"clouds":42,"terrainFile":false}}"""
        val fields = parsing.assetFields(text)
        assertNull(fields.clouds)
        assertEquals("123", fields.uuidText)
        assertEquals("false", fields.terrainFile)
        assertEquals("", parsing.assetFields("""{"format":"abyssus","formatVersion":1}""").terrainFile)
        assertEquals("weather", parsing.assetFields(text.replace("\"clouds\":42", "\"clouds\":\"weather\"")).clouds)
    }
}
