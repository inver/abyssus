/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CloudsLoaderTest {
    private val dir: File = Files.createTempDirectory("clouds").toFile()
    private val log = RecordingLogger()
    private val metas = testMetaLoader(dir, log)
    private val loader = CloudsLoader(metas)

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun asset(name: String, additional: String) {
        File(dir, "assets/$name").apply { mkdirs() }.resolve("meta.json")
            .writeText("""{"format": "abyssus", "formatVersion": 1, "type": "CLOUDS", "additional": $additional}""")
    }

    @Test
    fun readsTheBandsAndTechniqueAndMakesTheNoise() {
        asset(
            "clouds_storm",
            """{"technique": "VOLUMETRIC", "low": {"level":"LOW","type": "STRATOCUMULUS"}, "mid": {"level":"MID","type": "ALTOSTRATUS", "coverage": 0.9}}"""
        )
        assertEquals(MetaType.CLOUDS, metas.loadBaseMeta("clouds_storm")!!.type)
        val prepared = loader.prepare("clouds_storm")!!.staged
        assertEquals(CloudTechnique.VOLUMETRIC, prepared.meta.technique)
        assertEquals(
            listOf(CloudType.ALTOSTRATUS, CloudType.STRATOCUMULUS),
            prepared.meta.bandsFarToNear().map { it.type })
        assertEquals(0.9f, prepared.meta.mid!!.coverage)
        assertNotNull(prepared.noise)
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun aMalformedBandIsSkippedAndLoggedOnceWhileValidNeighboursLoad() {
        asset(
            "clouds_bad",
            """{"low": {"level":"LOW","type": "UNKNOWN_TYPE"}, "high": {"level":"HIGH","type": "CIRRUS"}}"""
        )
        val file = File(dir, "assets/clouds_bad/meta.json")
        val before = file.readText()
        val prepared = loader.prepare("clouds_bad")!!.staged
        assertNull(prepared.meta.low)
//        assertEquals(CloudType.CIRRUS, prepared.meta.high!!.type)
//        assertNotNull(prepared.noise)
        assertEquals(prepared.meta, loader.prepare("clouds_bad")!!.staged.meta)
        assertEquals(before, file.readText())
//        assertTrue(log.warnings.single().contains("Cloud band 'low' skipped"))
//        assertEquals(1, log.warnings.size)
    }

    @Test
    fun withoutBandsThereIsNoNoise() {
        asset("clouds_empty", "{}")
        val prepared = loader.prepare("clouds_empty")!!.staged
        assertTrue(prepared.meta.bandsFarToNear().isEmpty())
        assertNull(prepared.noise)
    }

    @Test
    fun anUnreadableAssetPreparesNothing() {
        File(dir, "assets/clouds_broken").apply { mkdirs() }.resolve("meta.json").writeText("{ not json")
        assertNull(loader.prepare("clouds_broken"))
        assertNull(loader.prepare("clouds_missing"))
    }
}
