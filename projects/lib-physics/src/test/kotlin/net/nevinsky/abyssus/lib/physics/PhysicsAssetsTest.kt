/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics

import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBakeState
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageRuntimeLoad
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.CAPSULE
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.layer
import net.nevinsky.abyssus.lib.physics.FoliageProject.Companion.layerJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

/** The CPU-only foliage reads of [PhysicsAssets]: no GL, no Jolt, and the same copy set a renderer prepares. */
class PhysicsAssetsTest {
    private val project = FoliageProject()
    private val copies = listOf(FoliageCopy(0, 10f, 10f, 0f, 1f), FoliageCopy(0, 20f, 30f, 1f, 1.5f))

    @After
    fun cleanUp() = project.close()

    private fun prepared() {
        project.foliage("[${layerJson(CAPSULE)}]")
        project.bake(listOf(layer(copies)))
    }

    private val assets get() = PhysicsAssets(project.dir)

    @Test
    fun aMatchingBakeIsReadOnceAndSharedByRenderingAndPhysics() {
        prepared()
        val assets = assets
        val first = assets.foliage("foliage_meadow", "terr")
        assertSame(first, assets.foliage("foliage_meadow", "terr"))
        val foliage = first.foliage!!
        assertEquals(FoliageBakeState.MATCHING, foliage.state)
        assertEquals(copies, foliage.bake.layers.single().chunks.single())
        // a game reads through the same policy, so both see the same prepared copy set
        val files = FileLoader(project.dir)
        val metas = AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)
        val game = FoliageRuntimeLoad(files, metas, TerrainLoader(files, metas)).read("foliage_meadow", "terr").foliage!!
        assertEquals(game.bake.layers.map { it.chunks }, foliage.bake.layers.map { it.chunks })
        assertEquals(game.state, foliage.state)
    }

    @Test
    fun aStaleBakeKeepsItsCopiesWithOneWarning() {
        project.foliage("[${layerJson(CAPSULE)}]")
        project.bake(listOf(layer(copies)), stale = true)
        val read = assets.foliage("foliage_meadow", "terr")
        assertEquals(FoliageBakeState.STALE, read.foliage!!.state)
        assertEquals(2, read.foliage!!.bake.copyCount)
        assertEquals(1, read.warnings.size)
    }

    @Test
    fun anUnverifiableBakeIsUsedAsSaved() {
        prepared()
        File(project.dir, "assets/foliage_meadow/layer-1.mask").writeBytes(ByteArray(3))
        val read = assets.foliage("foliage_meadow", "terr")
        assertEquals(FoliageBakeState.UNVERIFIABLE, read.foliage!!.state)
        assertEquals(2, read.foliage!!.bake.copyCount)
    }

    @Test
    fun aMissingBakeAWrongTerrainAndUnsupportedMetadataGiveNoFoliageAndAWarning() {
        prepared()
        project.deleteBake()
        assertTrue(assets.foliage("foliage_meadow", "terr").let { it.foliage == null && it.warnings.size == 1 })
        prepared()
        assertNull(assets.foliage("foliage_meadow", "other").foliage)
        val meta = File(project.dir, "assets/foliage_meadow/meta.json")
        meta.writeText(meta.readText().replace("\"formatVersion\":1", "\"formatVersion\":2"))
        val read = assets.foliage("foliage_meadow", "terr")
        assertNull(read.foliage)
        assertEquals(1, read.warnings.size)
    }

    @Test
    fun readingNeedsNoGlAndWritesNothing() {
        prepared()
        val before = project.dir.walkTopDown().filter { it.isFile }.associate { it.path to it.lastModified() }
        assets.foliage("foliage_meadow", "terr")
        assertEquals(before, project.dir.walkTopDown().filter { it.isFile }.associate { it.path to it.lastModified() })
    }
}
