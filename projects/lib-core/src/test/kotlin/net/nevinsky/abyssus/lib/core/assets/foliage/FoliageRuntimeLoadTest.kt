/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/** The headless runtime read policy: no GL, no IDE and no writes. */
class FoliageRuntimeLoadTest {
    private val dir: File = Files.createTempDirectory("foliage-runtime").toFile()
    private val files = FileLoader(dir)
    private val metas = testMetaLoader(dir, warningsTo(mutableListOf()), files)
    private val terrains = TerrainLoader(files, metas)
    private val loader = FoliageLoader(files, metas, terrains)
    private val runtime = FoliageRuntimeLoad(files, metas, terrains)

    private val copies = listOf(
        FoliageCopy(0, 1f, 1f, 0f, 1f),
        FoliageCopy(1, 2f, 2f, 0.5f, 1.5f),
        FoliageCopy(0, 3f, 4f, 1f, 1f),
    )

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
        meta(TWO_MODELS)
        bakeCurrent()
    }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun meta(text: String) {
        File(dir, "assets/foliage_meadow").mkdirs()
        File(dir, "assets/foliage_meadow/meta.json").writeText(text)
    }

    /** Writes a bake of [copies] whose fingerprint matches the metadata as it is now. */
    private fun bakeCurrent(modelCount: Int = 2, layerId: Int = 1) {
        val prepared = loader.prepare("foliage_meadow")!!.staged
        val fingerprint = FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks)
        File(dir, "assets/foliage_meadow/foliage.data").writeBytes(
            FoliageDataFile().write(
                FoliageBake(fingerprint, 32f, listOf(FoliageLayerBake(layerId, modelCount, 1, 1, listOf(copies))))
            )
        )
    }

    private fun total(read: FoliageRuntimeRead) = read.foliage!!.bake.copyCount

    @Test
    fun aMatchingBakeGivesItsCopyCount() {
        val read = runtime.read("foliage_meadow", "terr")
        assertEquals(FoliageBakeState.MATCHING, read.foliage!!.state)
        assertEquals(3, total(read))
        assertTrue(read.warnings.toString(), read.warnings.isEmpty())
    }

    @Test
    fun aDensityChangeIsStaleAndKeepsTheSavedCopies() {
        meta(TWO_MODELS.replace(""""kind":"OBJECT"""", """"kind":"OBJECT","density":0.9"""))
        val read = runtime.read("foliage_meadow")
        assertEquals(FoliageBakeState.STALE, read.foliage!!.state)
        assertEquals(3, total(read))
        assertEquals(1, read.warnings.size)
    }

    @Test
    fun anUnreadableMaskLeavesAReadableBakeUnverifiable() {
        File(dir, "assets/foliage_meadow/layer-1.mask").writeBytes(ByteArray(3))
        val read = runtime.read("foliage_meadow")
        assertEquals(FoliageBakeState.UNVERIFIABLE, read.foliage!!.state)
        assertEquals(3, total(read))
        assertEquals(1, read.warnings.size)
    }

    @Test
    fun aMissingMaskFileIsAFullMask() {
        assertEquals(FoliageBakeState.MATCHING, runtime.read("foliage_meadow").foliage!!.state)
    }

    @Test
    fun aRemovedLayerDropsItsCopies() {
        bakeCurrent(layerId = 7)
        val read = runtime.read("foliage_meadow")
        assertEquals(0, total(read))
        assertTrue(read.warnings.toString(), read.warnings.any { "layer 7" in it })
    }

    @Test
    fun anIndexNoModelAnswersToIsDropped() {
        meta(ONE_MODEL)
        val read = runtime.read("foliage_meadow")
        assertEquals(FoliageBakeState.STALE, read.foliage!!.state)
        assertEquals(listOf(0, 0), read.foliage!!.bake.layers.single().chunks.single().map { it.model })
        assertTrue(read.warnings.toString(), read.warnings.any { "model index" in it })
    }

    @Test
    fun aMissingModelDropsOnlyItsCopies() {
        File(dir, "assets/rock").deleteRecursively()
        val read = runtime.read("foliage_meadow")
        assertEquals(listOf(0, 0), read.foliage!!.bake.layers.single().chunks.single().map { it.model })
        assertEquals(1, read.warnings.count { "'rock'" in it })
    }

    @Test
    fun nonFiniteCopiesAreDropped() {
        val prepared = loader.prepare("foliage_meadow")!!.staged
        val fingerprint = FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks)
        val bad = listOf(copies[0], FoliageCopy(0, Float.NaN, 1f, 0f, 1f), FoliageCopy(0, 1f, 1f, 0f, Float.POSITIVE_INFINITY))
        File(dir, "assets/foliage_meadow/foliage.data").writeBytes(
            FoliageDataFile().write(FoliageBake(fingerprint, 32f, listOf(FoliageLayerBake(1, 2, 1, 1, listOf(bad)))))
        )
        assertEquals(1, total(runtime.read("foliage_meadow")))
    }

    @Test
    fun aMissingOrUnreadableTerrainOrAnotherEntityTerrainSkipsTheFoliage() {
        assertNull(runtime.read("foliage_meadow", "other").foliage)
        File(dir, "assets/terr/terrain.data").writeBytes(ByteArray(5))
        val read = runtime.read("foliage_meadow", "terr")
        assertNull(read.foliage)
        assertEquals(1, read.warnings.size)
        File(dir, "assets/terr").deleteRecursively()
        assertNull(runtime.read("foliage_meadow").foliage)
    }

    @Test
    fun aMissingTruncatedOrOtherwiseUnreadableBakeSkipsTheFoliage() {
        val bake = File(dir, "assets/foliage_meadow/foliage.data")
        val whole = bake.readBytes()
        bake.writeBytes(whole.copyOf(whole.size - 5))
        assertNull(runtime.read("foliage_meadow").foliage)
        bake.delete()
        val read = runtime.read("foliage_meadow")
        assertNull(read.foliage)
        assertEquals(1, read.warnings.size)
    }

    @Test
    fun formatVersionTwoAndMissingFoliageAreSkipped() {
        meta(TWO_MODELS.replace(""""formatVersion":1""", """"formatVersion":2"""))
        assertNull(runtime.read("foliage_meadow").foliage)
        assertNull(runtime.read("nothing_here").foliage)
        assertNull(runtime.read("tree").foliage)
    }

    @Test
    fun warningsAreBounded() {
        val layers = (1..40).joinToString(",") { """{"id":$it,"kind":"OBJECT","models":[{"asset":"tree","weight":1.0}]}""" }
        meta("""{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr","maskResolution":16,"layers":[$layers]}}""")
        val prepared = loader.prepare("foliage_meadow")!!.staged
        val fingerprint = FoliageFingerprint().of(prepared.meta, prepared.terrain, prepared.masks)
        val removed = (101..140).map { FoliageLayerBake(it, 1, 1, 1, listOf(listOf(copies[0]))) }
        File(dir, "assets/foliage_meadow/foliage.data").writeBytes(FoliageDataFile().write(FoliageBake(fingerprint, 32f, removed)))
        val read = runtime.read("foliage_meadow")
        assertEquals(FOLIAGE_MAX_WARNINGS + 1, read.warnings.size)
        assertTrue(read.warnings.last(), read.warnings.last().startsWith("and "))
    }

    @Test
    fun readingChangesNoFile() {
        meta(TWO_MODELS.replace(""""kind":"OBJECT"""", """"kind":"OBJECT","density":0.9"""))
        File(dir, "assets/foliage_meadow/layer-1.mask").writeBytes(ByteArray(3))
        val before = snapshot()
        runtime.read("foliage_meadow")
        assertEquals(before, snapshot())
    }

    @Test
    fun theReaderNeedsNeitherTheIdeNorTheEditorGenerator() {
        for (name in listOf("com.intellij.openapi.application.ApplicationManager", "net.nevinsky.abyssus.lib.core.editor.foliage.FoliageScatter")) {
            try {
                Class.forName(name)
                fail("$name is on the lib-core classpath")
            } catch (_: ClassNotFoundException) {
                assertNotNull(name)
            }
        }
    }

    private fun snapshot(): Map<String, Pair<Long, Int>> = dir.walkTopDown().filter { it.isFile }
        .associate { it.path to (it.lastModified() to it.readBytes().contentHashCode()) }

    private companion object {
        const val TWO_MODELS =
            """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr","maskResolution":16,""" +
                """"layers":[{"id":1,"kind":"OBJECT","models":[{"asset":"tree","weight":1.0},{"asset":"rock","weight":1.0}]}]}}"""
        const val ONE_MODEL =
            """{"format":"abyssus","formatVersion":1,"type":"FOLIAGE","additional":{"terrain":"terr","maskResolution":16,""" +
                """"layers":[{"id":1,"kind":"OBJECT","models":[{"asset":"tree","weight":1.0}]}]}}"""
    }
}
