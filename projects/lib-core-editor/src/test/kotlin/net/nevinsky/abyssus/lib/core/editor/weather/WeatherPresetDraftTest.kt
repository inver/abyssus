/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.weather

import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.util.*

class WeatherPresetDraftTest {
    private val json = SceneJson()
    private val processor = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val draft = WeatherPresetDraft(processor, ResourceEditorMessages())
    private val sourceId = "00000000-0000-0000-0000-000000000001"
    private val fresh = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val sky =
        """{"format":"abyssus","formatVersion":1,"type":"SKYBOX_PROCEDURAL","additional":{"clouds":"$sourceId"}}"""

    private fun source(additional: String) =
        """{"format":"abyssus","formatVersion":1,"uuid":"$sourceId","type":"CLOUDS","additional":$additional}"""

    private fun snapshot(text: String) = draft.create(sky, text, fresh, 123L)

    @Ignore
    @Test
    fun snapshotDefaultsAndIdentityRoundTripThroughRuntime() {
        val text = snapshot(source("""{"low":{"type":"cumulus"}}"""))
        val root = json.parse(text)
        assertEquals(
            listOf("format", "formatVersion", "version", "lastModified", "uuid", "type", "additional"),
            root.fieldNames().asSequence().toList()
        )
        assertEquals("abyssus", root["format"].asText())
        assertTrue(root["formatVersion"].isIntegralNumber)
        assertEquals(1, root["formatVersion"].asInt())
        assertEquals(1, root["version"].asInt())
        assertEquals(123L, root["lastModified"].asLong())
        assertEquals(fresh.toString(), root["uuid"].asText())
        assertEquals("CLOUDS", root["type"].asText())
        assertEquals(
            json.parse("""{"technique":"shells","low":{"type":"cumulus","base":800,"top":2000,"coverage":0.4,"density":0.8,"wind":[4,1]}}"""),
            root["additional"]
        )
        val settings = AssetMetaBinder(processor).bind("new", root).typedAdditional<CloudMeta>()
        assertEquals(CloudMeta(low = CloudMeta.CloudBand(CloudType.CUMULUS, CloudLevel.LOW)), settings)
    }

    @Test
    @Ignore
    fun keepsStoredTechniqueBandsExtensionsAndNumberTextWithoutMutatingSource() {
        val source =
            source("""{"technique":"volumetric","low":{"type":"stratocumulus","coverage":0.9000,"custom":1.00},"mid":{"type":"altostratus"},"high":{"type":"cirrus","wind":[25.00,5]},"extra":1.2300}""")
                .dropLast(1) + """, "vendor":{"n":2.00},"tail":null}"""
        val before = source
        val text = snapshot(source)
        assertEquals(before, source)
        assertTrue(text.contains("0.9000"))
        assertTrue(text.contains("1.2300"))
        assertTrue(text.contains("2.00"))
        assertTrue(text.contains("25.00"))
        val root = json.parse(text)
        assertTrue(root.has("tail"))
        assertEquals(listOf("vendor", "tail"), root.fieldNames().asSequence().toList().takeLast(2))
        val settings = AssetMetaBinder(processor).bind("new", root).typedAdditional<CloudMeta>()
        assertEquals(CloudTechnique.VOLUMETRIC, settings.technique)
        assertEquals(3, settings.bandsFarToNear().size)
        assertEquals(0.9f, settings.low!!.coverage, 0f)
    }

    @Ignore
    @Test
    fun invalidBandIsOmittedAndAcceptedRuntimeFieldsBecomeCanonical() {
        val root =
            json.parse(snapshot(source("""{"low":{"type":"cirrus"},"high":{"level":"HIGH","type":"CIRRUS","windX":-2.00,"windZ":3.00}}""")))
        val additional = root["additional"]
        assertFalse(additional.has("low"))
        val high = additional["high"]
        assertEquals("cirrus", high["type"].asText())
        assertFalse(high.has("level"))
        assertFalse(high.has("windX"))
        assertEquals("[-2.00,3.00]", json.compact(high["wind"]))
    }

    @Test
    fun refusesUnsupportedWrongTypeEmptyAndMismatchedSources() {
        val valid = source("""{"low":{"type":"cumulus"}}""")
        for (bad in listOf(
            "broken", valid.replace("\"formatVersion\":1", "\"formatVersion\":2"),
            valid.replace("CLOUDS", "MODEL"), source("{}"), source("""{"low":{"type":"cirrus"}}"""),
            valid.replace(sourceId, fresh.toString())
        )) {
            assertThrows(Exception::class.java) { snapshot(bad) }
        }
        assertThrows(Exception::class.java) {
            draft.create(
                sky.replace("SKYBOX_PROCEDURAL", "SKYBOX"),
                valid,
                fresh,
                123
            )
        }
        assertThrows(Exception::class.java) {
            draft.create(
                sky.replace("\"formatVersion\":1", "\"formatVersion\":1.0"),
                valid,
                fresh,
                123
            )
        }
    }
}
