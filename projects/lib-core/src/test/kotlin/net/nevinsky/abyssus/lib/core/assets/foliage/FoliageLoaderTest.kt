/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

class FoliageLoaderTest {
    private val dir: File = Files.createTempDirectory("foliage-loader").toFile()
    private val files = FileLoader(dir)
    private val messages = mutableListOf<String>()
    private val metas = testMetaLoader(dir, warningsTo(messages), files)
    private val terrains = TerrainLoader(files, metas)
    private val loader = FoliageLoader(files, metas, terrains)

    init {
        File(dir, "assets/terr").mkdirs()
        DataOutputStream(File(dir, "assets/terr/terrain.data").outputStream()).use { out ->
            repeat(4) { out.writeFloat(it.toFloat() * 2f) }
        }
        File(dir, "assets/terr/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":10,"uv":1.0}}"""
        )
        for (model in listOf("tree", "rock")) {
            File(dir, "assets/$model").mkdirs()
            File(dir, "assets/$model/meta.json").writeText(
                """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"model.gltf"}}"""
            )
        }
        foliage()
    }

    private fun foliage(text: String = TWO_MODEL_LAYER) {
        File(dir, "assets/foliage_meadow").mkdirs()
        File(dir, "assets/foliage_meadow/meta.json").writeText(text)
    }

    private fun bake(bake: FoliageBake?) {
        val file = File(dir, "assets/foliage_meadow/foliage.data")
        if (bake == null) file.delete() else file.writeBytes(FoliageDataFile().write(bake))
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun theDependenciesListTheTerrainAndBothModelsOfATwoModelLayer() {
        val prepared = loader.prepare("foliage_meadow")!!.staged
        assertEquals(setOf("terr", "tree", "rock"), loader.dependencies(prepared))
    }

    @Test
    fun aModelWithoutAFolderIsNotADependency() {
        foliage(TWO_MODEL_LAYER.replace(""""asset":"rock","weight":1.0""", """"asset":"gone","weight":1.0"""))
        val prepared = loader.prepare("foliage_meadow")!!.staged
        assertEquals(setOf("terr", "tree"), loader.dependencies(prepared))
    }

    @Test
    fun aFormatVersionTwoIsRefusedWithAReason() {
        foliage(TWO_MODEL_LAYER.replace(""""formatVersion":1""", """"formatVersion":2"""))
        assertNull(loader.prepare("foliage_meadow"))
        assertTrue(messages.single(), messages.single().contains("formatVersion"))
    }

    @Test
    fun aMissingBakeIsPreparedAsStaleNotFailed() {
        bake(null)
        val prepared = loader.prepare("foliage_meadow")!!.staged
        assertNull(prepared.bake)
        assertTrue(prepared.stale)
        assertEquals(0, prepared.copyCount)
    }

    @Test
    fun aBakeOfTheCurrentInputsIsNotStaleAndAnotherOneIs() {
        val meta = loader.prepare("foliage_meadow")!!.staged
        val fingerprint = FoliageFingerprint().of(meta.meta, meta.terrain, meta.masks)
        bake(FoliageDataFile().empty(fingerprint, foliageChunkSize(10)))
        assertFalse(loader.prepare("foliage_meadow")!!.staged.stale)
        bake(FoliageDataFile().empty(ByteArray(FOLIAGE_FINGERPRINT_BYTES), foliageChunkSize(10)))
        assertTrue(loader.prepare("foliage_meadow")!!.staged.stale)
    }

    @Test
    fun aMissingBakeStillReadsFullMasksAndTheTerrain() {
        bake(null)
        val prepared = loader.prepare("foliage_meadow")!!.staged
        val mask = prepared.masks.getValue(1)
        assertEquals(16 * 16, mask.size)
        for (b in mask) assertEquals(FOLIAGE_MASK_MAX, b.toInt() and 0xFF)
        assertNotNull(prepared.terrain)
        assertEquals(10, prepared.terrain!!.size)
    }

    private companion object {
        const val TWO_MODEL_LAYER =
            """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr","maskResolution":16,""" +
                """"layers":[{"id":1,"kind":"OBJECT","models":[{"asset":"tree","weight":1.0},{"asset":"rock","weight":1.0}]}]}}"""
    }
}
