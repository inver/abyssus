/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class CloudSettingsReaderTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val binder = AssetMetaBinder(json)

    private fun read(additional: String): CloudMeta = binder.bind("sky", json.readObject(
        """{"format":"abyssus","formatVersion":1,"type":"CLOUDS","additional":$additional}"""
    )).typedAdditional()

    @Test fun cumulusDefaults() {
        val band = read("""{"low":{"level":"LOW","type":"CUMULUS"}}""").low!!
        assertEquals(CloudBand(CloudLevel.LOW, CloudType.CUMULUS), band)
        assertEquals(800f, band.base, 0f)
        assertEquals(2000f, band.top, 0f)
        assertEquals(0.4f, band.coverage, 0f)
        assertEquals(0.8f, band.density, 0f)
        assertTrue(band.windX * band.windX + band.windZ * band.windZ in 1f..50f)
    }

    @Test fun givenFieldsReplaceDefaults() {
        val band = read("""{"low":{"level":"LOW","type":"CUMULUS","coverage":0.5,"base":1000,"windX":-2,"windZ":3.5}}""").low!!
        assertEquals(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, base = 1000f, coverage = 0.5f, windX = -2f, windZ = 3.5f), band)
    }

    @Test fun techniquesBindByEnumNameAndOmissionMeansShells() {
        assertEquals(CloudTechnique.SHELLS, read("{}").technique)
        for (technique in CloudTechnique.entries) {
            assertEquals(technique, read("""{"technique":"${technique.name}"}""").technique)
        }
    }

    @Test fun noBandsMeanNothingToDraw() {
        assertFalse(read("{}").visible)
        assertFalse(read("""{"technique":"LAYERED"}""").visible)
        assertTrue(read("""{"low":{"level":"LOW","type":"CUMULUS"}}""").visible)
    }

    @Test fun threeLevelsDrawHighestFirst() {
        val clouds = read("""{"low":{"level":"LOW","type":"CUMULUS"},"mid":{"level":"MID","type":"ALTOCUMULUS"},"high":{"level":"HIGH","type":"CIRRUS"}}""")
        assertEquals(listOf(CloudType.CIRRUS, CloudType.ALTOCUMULUS, CloudType.CUMULUS), clouds.bandsFarToNear().map { it.type })
    }

    @Test fun malformedBandRejectsTheMetadataWithoutChangingItsTree() {
        for (band in listOf("7", "{}", """{"level":"LOW"}""", """{"type":"CUMULUS"}""",
            """{"level":"LOW","type":"NIMBUS"}""", """{"level":"LOW","type":"CUMULUS","coverage":"lots"}""")) {
            val tree = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"CLOUDS","additional":{"low":$band}}""")
            val before = tree.toString()
            assertThrows(com.fasterxml.jackson.databind.JsonMappingException::class.java) { binder.bind("bad", tree) }
            assertEquals(before, tree.toString())
        }
    }

    @Test fun everyTypeDefaultLiesWithinItsBand() {
        for (type in CloudType.entries) {
            assertTrue(type.base in type.level.limits)
            assertTrue(type.top in type.level.limits)
            assertTrue(type.base < type.top)
            assertTrue(type.coverage in 0f..1f)
        }
    }
}
