/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.testing.RecordingLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSettingsReaderTest {
    private val json = JsonProcessor()
    private val log = RecordingLogger()
    private val reader = CloudSettingsReader(log)

    private fun read(text: String?) = reader.read("sky", text?.let(json::readObject))

    @Test
    fun cumulusDefaults() {
        val band = read("""{"low": {"type": "cumulus"}}""").bands.getValue(CloudLevel.LOW)
        assertEquals(CloudType.CUMULUS, band.type)
        assertEquals(800f, band.base)
        assertEquals(2000f, band.top)
        assertEquals(0.4f, band.coverage)
        assertEquals(0.8f, band.density)
        assertTrue("a light wind", band.windX * band.windX + band.windZ * band.windZ in 1f..50f)
    }

    @Test
    fun givenFieldsReplaceTheDefaults() {
        val band = read("""{"low": {"type": "cumulus", "coverage": 0.5, "base": 1000, "wind": [-2, 3.5]}}""")
            .bands.getValue(CloudLevel.LOW)
        assertEquals(CloudBand(CloudLevel.LOW, CloudType.CUMULUS, base = 1000f, coverage = 0.5f, windX = -2f, windZ = 3.5f), band)
    }

    @Test
    fun missingTechniqueMeansShells() {
        assertEquals(CloudTechnique.SHELLS, read("""{}""").technique)
        assertEquals(CloudTechnique.VOLUMETRIC, read("""{"technique": "volumetric"}""").technique)
        assertEquals(CloudTechnique.LAYERED, read("""{"technique": "layered"}""").technique)
    }

    @Test
    fun noBandsMeanNothingToDraw() {
        assertFalse(read(null).visible)
        assertFalse(read("""{"technique": "layered"}""").visible)
        assertTrue(read("""{"low": {"type": "cumulus"}}""").visible)
    }

    @Test
    fun theTemplatesAreValidCloudAssets() {
        for (name in listOf("fair", "overcast", "storm")) {
            val text = javaClass.getResourceAsStream("/clouds/templates/$name.json").use { String(it.readAllBytes()) }
            val meta = json.readObject(text)
            assertEquals("CLOUDS", meta["type"].asText())
            assertTrue(name, reader.read(name, meta["additional"]).visible)
        }
        assertTrue(log.warnings.toString(), log.warnings.isEmpty())
    }

    @Test
    fun threeLevelsDrawHighestFirst() {
        val settings = read(
            """{"low": {"type": "cumulus"}, "mid": {"type": "altocumulus"}, "high": {"type": "cirrus"}}"""
        )
        assertEquals(listOf(CloudType.CIRRUS, CloudType.ALTOCUMULUS, CloudType.CUMULUS), settings.bandsFarToNear.map { it.type })
    }

    private fun assertOnlyLowSkipped(low: String) {
        log.entries.clear()
        val settings = read("""{"low": $low, "mid": {"type": "altostratus"}}""")
        assertEquals("only the bad band is skipped for $low", setOf(CloudLevel.MID), settings.bands.keys)
        assertEquals("logged once for $low: ${log.warnings}", 1, log.warnings.size)
        assertTrue(log.warnings.single().contains("'low'"))
    }

    @Test
    fun anInvalidBandSkipsOnlyItself() {
        assertOnlyLowSkipped("""{"type": "cirrus"}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "base": 1500, "top": 1500}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "base": 1800, "top": 1200}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "top": 3000}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "base": 100}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "coverage": "lots"}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "wind": [1]}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "wind": [1, "x"]}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "coverage": 1.5}""")
        assertOnlyLowSkipped("""{"type": "cumulus", "density": -1}""")
        assertOnlyLowSkipped("""{"type": "nimbus"}""")
        assertOnlyLowSkipped("""{"coverage": 0.3}""")
        assertOnlyLowSkipped("""7""")
    }

    @Test
    fun everyTypeDefaultLiesWithinItsBand() {
        for (type in CloudType.entries) {
            assertTrue("$type base", type.base in type.level.limits)
            assertTrue("$type top", type.top in type.level.limits)
            assertTrue("$type base below top", type.base < type.top)
            assertTrue("$type coverage", type.coverage in 0f..1f)
        }
    }
}
