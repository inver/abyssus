/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.terrainData
import net.nevinsky.abyssus.lib.core.editor.testProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge [SceneFoliage] uses when a draft moves: only the chunks a patch holds are replaced, so a brush stroke
 * costs a re-scatter of the chunks it touched and nothing else (design decision 7).
 */
class FoliageBakeMergeTest {
    private val dataFile = FoliageDataFile()
    private val scatter = FoliageScatter(FoliageFingerprint())
    private val resolution = 256
    private val terrainName = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    private val untitled: TerrainData by lazy { terrainData(testProject("Untitled"), terrainName) }

    @Test
    fun thePatchReplacesOnlyTheChunksItHolds() {
        val base = bake(1, layer(7) { i, j -> copies(0, i, j) })
        val patch = bake(
            2,
            layer(7) { i, j -> if (i == 0 && j == 0 || i == 2 && j == 1) copies(9, i, j) else emptyList() },
        )
        val merged = mergeFoliageChunks(base, patch, setOf(FoliageChunk(0, 0), FoliageChunk(2, 1)))

        assertArrayEquals("the patch describes the merged bake", patch.fingerprint, merged.fingerprint)
        assertEquals(base.chunkSize, merged.chunkSize, 0f)
        val layerBake = merged.layers.single()
        assertEquals("the patch's own copy", 9, layerBake.chunk(0, 0).single().model)
        assertEquals(9, layerBake.chunk(2, 1).single().model)
        assertEquals("an untouched chunk keeps the base's copies", 0, layerBake.chunk(1, 1).single().model)
        assertEquals(11f, layerBake.chunk(1, 1).single().x, 0f)
        assertEquals("the patch's empty chunks must not empty the base's", 9, merged.copyCount)
    }

    @Test
    fun aChunkOutsideTheGridChangesNothing() {
        val base = bake(1, layer(7) { i, j -> copies(0, i, j) })
        val patch = bake(2, layer(7) { i, j -> copies(9, i, j) })
        val merged = mergeFoliageChunks(base, patch, setOf(FoliageChunk(5, 5)))
        assertSame(base, mergeFoliageChunks(base, patch, emptySet()))
        assertEquals("no chunk of the 3 by 3 grid was asked for", 0, merged.layers.single().chunk(0, 0).single().model)
        assertEquals(9, merged.copyCount)
    }

    @Test
    fun anotherChunkSizeTakesThePatchWhole() {
        val base = bake(1, layer(7) { i, j -> copies(0, i, j) })
        val patch = bake(2, listOf(layer(7) { i, j -> copies(5, i, j) }), chunkSize = 64f)
        assertSame("another terrain or another chunking: the patch is the bake", patch, mergeFoliageChunks(base, patch, setOf(FoliageChunk(1, 1))))
    }

    @Test
    fun layersOnlyOneSideHoldsAreKept() {
        val base = bake(1, listOf(layer(7) { i, j -> copies(0, i, j) }, layer(8) { i, j -> copies(1, i, j) }))
        val patch = bake(2, listOf(layer(8) { i, j -> copies(6, i, j) }, layer(9) { i, j -> copies(2, i, j) }))
        val merged = mergeFoliageChunks(base, patch, setOf(FoliageChunk(1, 1)))

        assertEquals("the base's order, then the patch's new layer", listOf(7, 8, 9), merged.layers.map { it.id })
        assertEquals("the chunk the patch holds", 6, merged.layers[1].chunk(1, 1).single().model)
        assertEquals("a chunk it does not hold", 1, merged.layers[1].chunk(0, 0).single().model)
        assertEquals("a layer only the base holds keeps every copy", 0, merged.layers[0].chunk(2, 2).single().model)
        assertEquals("a layer only the patch holds arrives as it comes", 2, merged.layers[2].chunk(0, 0).single().model)
    }

    @Test
    fun aLayerOnAnotherGridIsTakenAsItComes() {
        val base = bake(1, layer(7) { i, j -> copies(0, i, j) })
        val wide = FoliageLayerBake(7, 1, 5, 5, List(25) { listOf(FoliageCopy(4, it.toFloat(), 1f, 0f, 1f)) })
        val patch = bake(2, listOf(wide))
        assertSame("a patch of another chunk grid cannot be merged chunkwise", wide, mergeFoliageChunks(base, patch, setOf(FoliageChunk(0, 0))).layers.single())
    }

    @Test
    fun reScatteringTheDirtyChunksAndMergingGivesTheFullBakesBytes() {
        val settings = FoliageMeta(
            terrain = terrainName,
            maskResolution = resolution,
            layers = listOf(
                FoliageLayerMeta(id = 1, models = listOf(FoliageModelMeta("tree")), density = 0.01f, seed = 0),
                FoliageLayerMeta(id = 2, models = listOf(FoliageModelMeta("rock")), density = 0.01f, seed = 991),
            ),
        )
        val masks = mapOf(1 to ByteArray(resolution * resolution) { (128 + it % 128).toByte() })
        val full = scatter.scatter(untitled, settings, masks)
        assertTrue("copies ${full.total}", full.total > 1_000)

        val chunks = setOf(FoliageChunk(0, 0), FoliageChunk(3, 7), FoliageChunk(12, 4), FoliageChunk(49, 49))
        val patch = scatter.scatter(untitled, settings, masks, chunks = chunks)
        val merged = mergeFoliageChunks(full.bake, patch.bake, chunks)
        assertArrayEquals(
            "the untouched chunks must be byte-identical to the full bake",
            dataFile.write(full.bake), dataFile.write(merged),
        )
    }

    /** A 3 by 3 grid of one copy per chunk, the [model] and the chunk's own coordinates making each recognisable. */
    private fun layer(id: Int, fill: (i: Int, j: Int) -> List<FoliageCopy>): FoliageLayerBake {
        val chunks = ArrayList<List<FoliageCopy>>(9)
        for (j in 0 until 3) for (i in 0 until 3) chunks += fill(i, j)
        return FoliageLayerBake(id, 1, 3, 3, chunks)
    }

    private fun copies(model: Int, i: Int, j: Int): List<FoliageCopy> = listOf(FoliageCopy(model, i * 10f + j, 1f, 0f, 1f))

    private fun bake(fingerprint: Byte, layers: List<FoliageLayerBake>, chunkSize: Float = 32f): FoliageBake =
        FoliageBake(ByteArray(32) { fingerprint }, chunkSize, layers)

    private fun bake(fingerprint: Byte, vararg layers: FoliageLayerBake): FoliageBake = bake(fingerprint, layers.toList())
}
