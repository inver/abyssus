/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.clouds

import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.assets.testMetaLoader
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CloudsLoaderTest {
    private val dir: File = Files.createTempDirectory("clouds").toFile()
    private val log = RecordingLogger()
    private val metas = testMetaLoader(dir)
    private val loader = CloudsLoader(metas, JsonProcessor(), log)

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
        asset("clouds_storm", """{"technique": "volumetric", "low": {"type": "stratocumulus"}, "mid": {"type": "altostratus", "coverage": 0.9}}""")
        assertEquals(MetaType.CLOUDS, metas.loadBaseMeta("clouds_storm")!!.type)
        val prepared = loader.prepare("clouds_storm")!!.staged
        assertEquals(CloudTechnique.VOLUMETRIC, prepared.meta.technique)
        assertEquals(listOf(CloudType.ALTOSTRATUS, CloudType.STRATOCUMULUS), prepared.meta.bandsFarToNear.map { it.type })
        assertEquals(0.9f, prepared.meta.bands.getValue(CloudLevel.MID).coverage)
        assertNotNull(prepared.noise)
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun aBadBandIsSkippedAndLoggedOnce() {
        asset("clouds_bad", """{"low": {"type": "cirrus"}, "high": {"type": "cirrus"}}""")
        val prepared = loader.prepare("clouds_bad")!!.staged
        assertEquals(setOf(CloudLevel.HIGH), prepared.meta.bands.keys)
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun withoutBandsThereIsNoNoise() {
        asset("clouds_empty", "{}")
        val prepared = loader.prepare("clouds_empty")!!.staged
        assertTrue(prepared.meta.bands.isEmpty())
        assertNull(prepared.noise)
    }

    @Test
    fun anUnreadableAssetPreparesNothing() {
        File(dir, "assets/clouds_broken").apply { mkdirs() }.resolve("meta.json").writeText("{ not json")
        assertNull(loader.prepare("clouds_broken"))
        assertNull(loader.prepare("clouds_missing"))
    }
}
