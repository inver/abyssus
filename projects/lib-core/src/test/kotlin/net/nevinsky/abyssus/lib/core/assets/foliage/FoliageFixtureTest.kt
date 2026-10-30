/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * The committed `Foliage` fixture: a terrain, `tree`, one model and a foliage asset with an OBJECT and a DETAIL layer,
 * a painted mask and a bake. The bake is the generated one, so it must load as not stale; a test that edits the meta,
 * the mask or the terrain must regenerate it (see the fixture's README).
 */
class FoliageFixtureTest {
    private val project = testProject("Foliage")
    private val files = testFileLoader(project)
    private val metas = testMetaLoader(project)
    private val loader = FoliageLoader(files, metas, TerrainLoader(files, metas))

    private val prepared get() = loader.prepare("foliage_meadow")!!.staged

    @Test
    fun theFixtureBakeMatchesTheFingerprintOfItsInputs() {
        val foliage = prepared
        assertFalse("the committed bake is stale", foliage.stale)
        val bake = checkNotNull(foliage.bake) { "the committed bake reads" }
        assertEquals(2, foliage.meta.layers.size)
        assertEquals(2, bake.layers.size)
        assertTrue("the OBJECT layer has copies", bake.layers.first { it.id == 0 }.copyCount > 0)
        assertTrue("the DETAIL layer has copies", bake.layers.first { it.id == 1 }.copyCount > 0)
        assertEquals(bake.copyCount, foliage.copyCount)
    }

    @Test
    fun theFixtureDeclaresItsTerrainAndBothLayerModels() {
        assertEquals(
            setOf("terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b", "tree", "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"),
            prepared.dependencies,
        )
        assertNotNull(prepared.terrain)
    }

    @Test
    fun theFixtureMaskIsThePaintedOneAndAnUnpaintedLayerIsFull() {
        assertEquals(64 * 64, prepared.masks.getValue(1).size)
        assertEquals(96, prepared.masks.getValue(1)[48].toInt() and 0xFF) // the painted corner texel (x 48, z 0)
        assertEquals(255, prepared.masks.getValue(1)[32 * 64 + 32].toInt() and 0xFF) // outside it
        assertEquals(64 * 64, prepared.masks.getValue(0).size) // no file: the loader's full mask
    }

    @Test
    fun theFingerprintFollowsTheInputsTheBakeWasMadeFrom() {
        val changed = FoliageMeta(
            terrain = prepared.meta.terrain,
            maskResolution = prepared.meta.maskResolution,
            layers = prepared.meta.layers.map { it.copy(density = it.density * 2) },
        )
        val other = FoliageFingerprint().of(changed, prepared.terrain, prepared.masks)
        assertFalse(checkNotNull(prepared.bake).fingerprint.contentEquals(other))
    }
}
