/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class CloudSettingsReaderTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val binder = AssetMetaBinder(json)

    private fun read(additional: String): CloudMeta = binder.bind(
        "sky", json.readObject(
            """{"format":"abyssus","formatVersion":1,"type":"CLOUDS","additional":$additional}"""
        )
    ).typedAdditional()

    @Test
    fun cumulusDefaults() {
        val band = read("""{"low":{"level":"LOW","type":"CUMULUS"}}""").low!!
        assertEquals(CloudBand(CloudType.CUMULUS, CloudLevel.LOW), band)
        assertEquals(800f, band.base, 0f)
        assertEquals(2000f, band.top, 0f)
        assertEquals(0.4f, band.coverage, 0f)
        assertEquals(0.8f, band.density, 0f)
        assertTrue(band.windX * band.windX + band.windZ * band.windZ in 1f..50f)
    }

    @Test
    fun givenFieldsReplaceDefaults() {
        val band =
            read("""{"low":{"level":"LOW","type":"CUMULUS","coverage":0.5,"base":1000,"windX":-2,"windZ":3.5}}""").low!!
        assertEquals(
            CloudBand(
                CloudType.CUMULUS,
                CloudLevel.LOW,
                base = 1000f,
                coverage = 0.5f,
                windX = -2f,
                windZ = 3.5f
            ), band
        )
    }

    @Test
    fun techniquesBindByEnumNameAndOmissionMeansShells() {
        assertEquals(CloudTechnique.SHELLS, read("{}").technique)
        for (technique in CloudTechnique.entries) {
            assertEquals(technique, read("""{"technique":"${technique}"}""").technique)
        }
    }

    @Test
    fun noBandsMeanNothingToDraw() {
        assertFalse(read("{}").visible)
        assertFalse(read("""{"technique":"LAYERED"}""").visible)
        assertTrue(read("""{"low":{"level":"LOW","type":"CUMULUS"}}""").visible)
    }

    @Test
    fun threeLevelsDrawHighestFirst() {
        val clouds =
            read("""{"low":{"level":"LOW","type":"CUMULUS"},"mid":{"level":"MID","type":"ALTOCUMULUS"},"high":{"level":"HIGH","type":"CIRRUS"}}""")
        assertEquals(
            listOf(CloudType.CIRRUS, CloudType.ALTOCUMULUS, CloudType.CUMULUS),
            clouds.bandsFarToNear().map { it.type })
    }

    @Test
    fun malformedBandIsSkippedWithoutChangingItsTree() {
        for (band in listOf(
            "7", "{}", """{"level":"LOW"}""",
            """{"level":"LOW","type":"NIMBUS"}""", """{"level":"LOW","type":"CUMULUS","coverage":"lots"}"""
        )) {
            val tree =
                json.readObject("""{"format":"abyssus","formatVersion":1,"type":"CLOUDS","additional":{"low":$band}}""")
            val before = tree.toString()
            assertFalse(binder.bind("bad", tree).typedAdditional<CloudMeta>().visible)
            assertEquals(before, tree.toString())
        }
    }

    @Test
    fun canonicalTypesInferTheirLevelAndDefaults() {
        for (type in CloudType.entries) {
            val clouds = read("""{"${type.level.key}":{"type":"${type}"}}""")
            assertEquals(listOf(CloudBand(type, type.level)), clouds.bandsFarToNear())
        }
        for (technique in CloudTechnique.entries) {
            assertEquals(technique, read("""{"technique":"${technique}"}""").technique)
        }
    }

    @Test
    fun canonicalWindAndCompleteSettingsSurviveBinding() {
        val band =
            read("""{"technique":"VOLUMETRIC","high":{"type":"CIRRUS","base":7000,"top":10000,"coverage":0.5,"density":0.2,"wind":[-2,3.5]}}""").high!!
        assertEquals(CloudBand(CloudType.CIRRUS, CloudLevel.HIGH, 7000f, 10000f, 0.5f, 0.2f, -2f, 3.5f), band)
    }

    @Ignore
    @Test
    fun invalidBandsDoNotDiscardValidNeighbours() {
        for (band in listOf(
            "7", "{}", """""{"type":"CIRRUS"}""", """{"type":"CUMULUS","base":2000,"top":800}""",
            """{"type":"CUMULUS","base":299}""", """{"type":"CUMULUS","top":2501}""",
            """{"type":"CUMULUS","coverage":1.1}""", """{"type":"CUMULUS","density":-1}""",
            """{"type":"CUMULUS","wind":[1]}""", """{"type":"CUMULUS","wind":["bad",1]}""",
            """{"type":"CUMULUS","wind":null}""", """{"type":"CUMULUS","base":"800"}"""
        )) {
            val clouds = read("""{"low":$band,"high":{"type":"CIRRUS"}}""")
            assertNull(band, clouds.low)
            assertEquals(CloudBand(CloudType.CIRRUS, CloudLevel.HIGH), clouds.high)
        }
    }

    @Test
    fun everyTypeDefaultLiesWithinItsBand() {
        for (type in CloudType.entries) {
            assertTrue(type.base in type.level.limits)
            assertTrue(type.top in type.level.limits)
            assertTrue(type.base < type.top)
            assertTrue(type.coverage in 0f..1f)
        }
    }
}
