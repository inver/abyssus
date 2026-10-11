/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDataFile
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageChunkSize
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.editor.terrainData
import net.nevinsky.abyssus.lib.core.editor.testProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class FoliageScatterTest {
    private val scatter = FoliageScatter(FoliageFingerprint())
    private val dataFile = FoliageDataFile()
    private val resolution = 256
    private val terrainName = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"
    private val untitled: TerrainData by lazy { terrainData(testProject("Untitled"), terrainName) }

    private fun layer(
        id: Int,
        density: Float,
        seed: Int = 0,
        models: List<FoliageModelMeta> = listOf(FoliageModelMeta("tree")),
        minHeight: Float? = null,
        maxHeight: Float? = null,
        maxSlope: Float? = null,
    ): FoliageLayerMeta = FoliageLayerMeta(
        id = id,
        models = models,
        density = density,
        minHeight = minHeight,
        maxHeight = maxHeight,
        maxSlope = maxSlope,
        seed = seed,
    )

    private fun meta(vararg layers: FoliageLayerMeta): FoliageMeta =
        FoliageMeta(terrain = terrainName, maskResolution = resolution, layers = layers.toList())

    private fun uniformMask(value: Int): ByteArray = ByteArray(resolution * resolution) { value.toByte() }

    private fun variedTerrain(): TerrainData {
        val base = untitled
        val heights = FloatArray(base.resolution * base.resolution)
        for (z in 0 until base.resolution) for (x in 0 until base.resolution) {
            val wx = x * base.size / (base.resolution - 1).toFloat()
            val wz = z * base.size / (base.resolution - 1).toFloat()
            heights[z * base.resolution + x] =
                27f + 22f * sin(2.0 * PI * wx / 200.0).toFloat() * cos(2.0 * PI * wz / 200.0).toFloat()
        }
        return TerrainData(base.resolution, heights, base.size, base.uv)
    }

    @Test
    fun equalInputsGiveByteEqualBakes() {
        val settings = meta(
            layer(1, 0.01f),
            layer(2, 0.01f, seed = -7717, models = listOf(FoliageModelMeta("tree", 3f), FoliageModelMeta("rock", 1f))),
        )
        val masks = mapOf(1 to ByteArray(resolution * resolution) { ((it * 37) % 256).toByte() })
        val first = scatter.scatter(untitled, settings, masks)
        val second = scatter.scatter(untitled, settings, masks)
        assertNull(first.refusal.toString(), first.refusal)
        assertTrue("copies ${first.total}", first.total > 1_000)
        assertArrayEquals(dataFile.write(first.bake), dataFile.write(second.bake))
        assertEquals(first.counts, second.counts)
        assertEquals(first.bake.layers[0].copyCount, first.counts[1]!!)
        assertEquals(first.bake.layers[1].copyCount, first.counts[2]!!)
    }

    @Test
    fun changingASeedChangesOnlyThatLayer() {
        val still = layer(1, 0.01f, seed = 41)
        val moved = layer(2, 0.01f, seed = 41)
        val base = scatter.scatter(untitled, meta(still, moved), emptyMap())
        val other = scatter.scatter(untitled, meta(still, moved.copy(seed = 42)), emptyMap())
        assertEquals(base.bake.layers[0], other.bake.layers[0])
        assertNotEquals(base.bake.layers[1], other.bake.layers[1])
        assertTrue(base.bake.layers[1].copyCount > 0)
    }

    @Test
    fun noTwoCopiesAreCloserThanOneFifthOfTheSpacing() {
        val flat = TerrainData(2, FloatArray(4), 64, 1f)
        for (density in listOf(1f, 0.25f, 0.0625f)) {
            val copies = scatter.scatter(flat, meta(layer(1, density)), emptyMap())
                .bake.layers.single().chunks.flatten()
            val cells = (64f * sqrt(density)).toInt()
            assertEquals(cells * cells, copies.size)
            val spacing = 0.2f / sqrt(density)
            var closest = Float.MAX_VALUE
            for (a in copies.indices) for (b in a + 1 until copies.size) {
                val dx = copies[a].x - copies[b].x
                val dz = copies[a].z - copies[b].z
                val distanceSquared = dx * dx + dz * dz
                if (distanceSquared < closest) closest = distanceSquared
            }
            assertTrue(
                "density $density: closest ${sqrt(closest)} is below $spacing",
                sqrt(closest) + 1e-3f >= spacing,
            )
        }
    }

    @Test
    fun theFlatFixtureTerrainKeepsNothingBetweenFiveAndForty() {
        // The Untitled heights are all 0, so a 5 to 40 height range refuses every candidate, and the slope limit of
        // 20 degrees keeps them all: the terrain is flat.
        val excluded = scatter.scatter(
            untitled, meta(layer(1, 0.01f, minHeight = 5f, maxHeight = 40f, maxSlope = 20f)), emptyMap(),
        )
        assertEquals(0, excluded.total)
        val kept = scatter.scatter(untitled, meta(layer(1, 0.01f, maxSlope = 20f)), emptyMap())
        assertEquals(25_600, kept.total)
    }

    @Test
    fun everyCopyStandsWithinTheHeightRangeAndBelowTheSlopeLimit() {
        // The fixture terrain is flat at 0, so its heights are ramped over the same grid: the field rises past 40 and
        // steepens past 30 degrees, which is what the rules have to exclude.
        val varied = variedTerrain()
        val limited = scatter.scatter(
            varied, meta(layer(1, 0.01f, minHeight = 5f, maxHeight = 40f, maxSlope = 20f)), emptyMap(),
        )
        assertNull(limited.refusal.toString(), limited.refusal)
        assertTrue("copies ${limited.total}", limited.total > 0)
        for (copy in limited.bake.layers.single().chunks.flatten()) {
            val height = varied.heightAt(copy.x, copy.z)
            assertTrue("height $height", height != null && height in 5f..40f)
            assertTrue("slope at ${copy.x},${copy.z}", terrainSlopeDegrees(varied, copy.x, copy.z) <= 20f)
        }
        var steepest = 0f
        for (i in 0..64) for (j in 0..64) {
            steepest = max(steepest, terrainSlopeDegrees(varied, i * varied.size / 64f, j * varied.size / 64f))
        }
        assertTrue("steepest $steepest", steepest > 30f)
        val unlimited = scatter.scatter(varied, meta(layer(1, 0.01f)), emptyMap())
        assertEquals(25_600, unlimited.total)
        assertTrue("${limited.total} of ${unlimited.total}", limited.total < unlimited.total)
    }

    @Test
    fun anEmptyMaskGivesNoCopies() {
        val outcome = scatter.scatter(untitled, meta(layer(1, 0.01f)), mapOf(1 to uniformMask(0)))
        assertNull(outcome.refusal.toString(), outcome.refusal)
        assertEquals(0, outcome.total)
        assertEquals(0, outcome.bake.layers.single().copyCount)
    }

    @Test
    fun aMaskOf128GivesAboutHalfTheCopies() {
        val settings = meta(layer(1, 0.01f))
        val full = scatter.scatter(untitled, settings, emptyMap())
        val half = scatter.scatter(untitled, settings, mapOf(1 to uniformMask(128)))
        assertEquals(25_600, full.total)
        val ratio = half.total.toDouble() / full.total
        assertTrue("ratio $ratio", ratio in 0.47..0.53)
    }

    @Test
    fun weightsOfThreeToOneGiveAboutThreeQuarters() {
        val settings = meta(
            layer(1, 0.01f, models = listOf(FoliageModelMeta("tree", 3f), FoliageModelMeta("rock", 1f))),
        )
        val copies = scatter.scatter(untitled, settings, emptyMap()).bake.layers.single().chunks.flatten()
        val share = copies.count { it.model == 0 }.toDouble() / copies.size
        assertTrue("share $share", share in 0.72..0.78)
    }

    @Test
    fun everyCopyHasAPositionYawScaleAndModelIndexInRange() {
        val copies = scatter.scatter(
            untitled,
            meta(layer(1, 0.01f, models = listOf(FoliageModelMeta("tree", 1f)))),
            emptyMap(),
        ).bake.layers.single().chunks.flatten()
        for (copy in copies) {
            assertTrue("${copy.x},${copy.z}", copy.x in 0f..untitled.size.toFloat() && copy.z in 0f..untitled.size.toFloat())
            assertTrue("yaw ${copy.yaw}", copy.yaw in 0f..2f * PI.toFloat())
            assertTrue("scale ${copy.scale}", copy.scale in 0.8f..1.2f)
            assertEquals(0, copy.model)
        }
    }

    @Test
    fun reScatteringTheChunksUnderARectangleGivesTheFullBakesBytes() {
        val settings = meta(layer(1, 0.01f), layer(2, 0.01f, seed = 991))
        val masks = mapOf(1 to ByteArray(resolution * resolution) { (128 + it % 128).toByte() })
        val full = scatter.scatter(untitled, settings, masks)
        assertNull(full.refusal.toString(), full.refusal)
        assertEquals(2, full.bake.layers.size)
        val chunkSize = foliageChunkSize(untitled.size)
        val chunksX = ceil(untitled.size.toDouble() / chunkSize).toInt()
        val random = Random(20261010)
        repeat(20) {
            val x0 = random.nextInt(untitled.size)
            val z0 = random.nextInt(untitled.size)
            val x1 = (x0 + 10 + random.nextInt(700)).coerceAtMost(untitled.size - 1)
            val z1 = (z0 + 10 + random.nextInt(700)).coerceAtMost(untitled.size - 1)
            val requested = mutableSetOf<FoliageChunk>()
            val wanted = HashSet<Int>()
            for (cj in floor(z0 / chunkSize).toInt()..floor(z1 / chunkSize).toInt()) {
                for (ci in floor(x0 / chunkSize).toInt()..floor(x1 / chunkSize).toInt()) {
                    requested += FoliageChunk(ci, cj)
                    wanted += cj * chunksX + ci
                }
            }
            val partial = scatter.scatter(untitled, settings, masks, chunks = requested)
            assertNull(partial.refusal.toString(), partial.refusal)
            assertTrue("partial copies ${partial.total}", partial.total > 0)
            val merged = full.bake.layers.mapIndexed { index, layerBake ->
                val regenerated = partial.bake.layers[index]
                FoliageLayerBake(
                    layerBake.id, layerBake.modelCount, layerBake.chunksX, layerBake.chunksZ,
                    layerBake.chunks.mapIndexed { position, list ->
                        if (position in wanted) regenerated.chunks[position] else list
                    },
                )
            }
            val mergedBytes = dataFile.write(FoliageBake(full.bake.fingerprint, full.bake.chunkSize, merged))
            assertArrayEquals("rectangle $x0,$z0 to $x1,$z1", dataFile.write(full.bake), mergedBytes)
        }
    }

    @Test
    fun densityOneIsRefusedWithAnEstimateOfTwoAndAHalfMillionCopies() {
        val outcome = scatter.scatter(untitled, meta(layer(1, 1f)), emptyMap())
        assertEquals(ScatterRefusal.COPY_LIMIT, outcome.refusal)
        assertEquals(2_560_000L, outcome.estimate)
        assertEquals(0, outcome.total)
        assertTrue(outcome.bake.layers.isEmpty())
        assertTrue(outcome.counts.isEmpty())
    }

    @Test
    fun theCopyLimitCountsAllLayersTogether() {
        // Each layer alone is well under the limit; together they are not, so the refusal comes from the estimate
        // pass before any copy is generated.
        val alone = scatter.scatter(untitled, meta(layer(1, 0.25f)), emptyMap())
        assertNull(alone.refusal.toString(), alone.refusal)
        assertEquals(640_000, alone.total)
        val together = scatter.scatter(untitled, meta(layer(1, 0.25f), layer(2, 0.25f)), emptyMap())
        assertEquals(ScatterRefusal.COPY_LIMIT, together.refusal)
        assertEquals(1_280_000L, together.estimate)
        assertEquals(0, together.total)
    }

    @Test
    fun aLayerOverTheCandidateGuardIsRefusedBeforeGenerating() {
        val outcome = scatter.scatter(untitled, meta(layer(1, 7f)), emptyMap())
        assertEquals(ScatterRefusal.CANDIDATE_LIMIT, outcome.refusal)
        assertEquals(17_918_289L, outcome.estimate)
        assertEquals(0, outcome.total)
        assertTrue(outcome.bake.layers.isEmpty())
    }
}
