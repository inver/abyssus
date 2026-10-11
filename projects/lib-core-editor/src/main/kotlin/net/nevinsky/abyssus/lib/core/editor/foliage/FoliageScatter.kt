/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.assets.foliage.FOLIAGE_MASK_MAX
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCopy
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageFingerprint
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerBake
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.foliageChunkSize
import net.nevinsky.abyssus.lib.core.assets.foliage.fullMask
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** The most copies a foliage asset holds in total; a scatter that would go over it is refused. */
const val FOLIAGE_COPY_LIMIT: Int = 1_000_000

/**
 * The most candidates one layer may have. The cell loops walk every candidate, so a layer above this is refused
 * before any estimate or generation, which bounds the time dense layers cost even when their masks are empty.
 */
const val FOLIAGE_CANDIDATE_LIMIT: Int = 16_000_000

private const val TWO_PI: Float = 6.2831855f

/** One cell of a bake's chunk grid: [i] along x and [j] along z, each from 0. A chunk is `chunkSize` world units. */
data class FoliageChunk(val i: Int, val j: Int)

/**
 * Why a [ScatterOutcome] holds no copies: [COPY_LIMIT] over [FOLIAGE_COPY_LIMIT], or [CANDIDATE_LIMIT] over
 * [FOLIAGE_CANDIDATE_LIMIT]. The panel turns it into the reason shown beside the count.
 */
enum class ScatterRefusal { COPY_LIMIT, CANDIDATE_LIMIT }

/**
 * The result of [FoliageScatter.scatter]: the [bake] with a copy [count] per layer id and their [total]. On a
 * [refusal] the bake is empty and must not be merged anywhere; [estimate] is then what the refusal was measured
 * against: the estimated copy count for a copy limit, the candidate count for the candidate guard. Without a
 * [refusal] [estimate] is the generated [total].
 */
class ScatterOutcome(
    val bake: FoliageBake,
    val counts: Map<Int, Int>,
    val total: Int,
    val refusal: ScatterRefusal? = null,
    val estimate: Long = 0,
)

/**
 * The slope in degrees of the terrain surface at terrain-local ([x], [z]): the angle between the surface normal and
 * straight up, from the exact gradient of the bilinear height field. The scatter rules use it for `maxSlope`; the
 * mesh normals come from central differences over the grid, so the two agree to within a cell's gradient.
 */
fun terrainSlopeDegrees(terrain: TerrainData, x: Float, z: Float): Float {
    val size = terrain.size.toFloat()
    if (size <= 0f) return 0f
    val cx = x.coerceIn(0f, size)
    val cz = z.coerceIn(0f, size)
    val cells = terrain.resolution - 1
    val step = size / cells
    val gx = minOf((cx / step).toInt(), cells - 1)
    val gz = minOf((cz / step).toInt(), cells - 1)
    val fx = cx / step - gx
    val fz = cz / step - gz
    val row = terrain.resolution
    val h00 = terrain.heights[gz * row + gx]
    val h10 = terrain.heights[gz * row + gx + 1]
    val h01 = terrain.heights[(gz + 1) * row + gx]
    val h11 = terrain.heights[(gz + 1) * row + gx + 1]
    val slopeX = ((h10 - h00) * (1f - fz) + (h11 - h01) * fz) / step
    val slopeZ = ((h01 - h00) * (1f - fx) + (h11 - h10) * fx) / step
    return atan(sqrt(slopeX * slopeX + slopeZ * slopeZ)) * (180f / PI.toFloat())
}

/** The SplitMix64 step: `0x9E3779B97F4A7C15`, written as its negative because Kotlin hex Longs stop at `0x7FF…`. */
private const val GOLDEN_GAMMA = -0x61C8864680B583EBL

/** The first SplitMix64 mixing multiplier, `0xBF58476D1CE4E5B9`, likewise negated. */
private const val MIX_A = -0x40A7B892E31B1A47L

/** The second SplitMix64 mixing multiplier, `0x94D049BB133111EB`, likewise negated. */
private const val MIX_B = -0x6B2FB644ECCEEE15L

/** The SplitMix64 stream one cell draws from: [unit] gives the next float in [0, 1). */
private class SplitMix64(seed: Long) {
    private var state = seed

    fun next(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        return z xor (z ushr 31)
    }

    fun unit(): Float = (next() ushr 40) / 16777216f
}

/** The SplitMix64 finalizer folded into [h] with [v]; a bijection, so distinct folds stay distinct. */
private fun fold(h: Long, v: Long): Long {
    var z = h + v
    z = (z xor (z ushr 30)) * MIX_A
    z = (z xor (z ushr 27)) * MIX_B
    return z xor (z ushr 31)
}

/** The stream of the cell at ([i], [j]) of the layer with [seed] and [id]: cells never depend on their neighbours. */
private fun cellStream(seed: Int, layerId: Int, i: Int, j: Int): SplitMix64 {
    var h = fold(0x2545F4914F6CDD1DL, seed.toLong())
    h = fold(h, layerId.toLong())
    h = fold(h, i.toLong())
    return SplitMix64(fold(h, j.toLong()))
}

/** One layer's cell grid: [cells] by [cells] cells of side [cell] world units, tiling the terrain square. */
private class LayerGrid(val cells: Int, val cell: Float)

/**
 * The jittered grid of one layer over a terrain of [size] units, or null when the layer scatters nothing (a density
 * of zero, or no models). The side is `size / floor(size * sqrt(density))`, so cells never come out smaller than
 * `1/sqrt(density)` and the jitter box keeps neighbours `0.2 / sqrt(density)` apart at any density.
 */
private fun gridOf(size: Int, layer: FoliageLayerMeta): LayerGrid? {
    if (!(layer.density > 0f) || layer.models.isEmpty()) return null
    // The side count when the cell is exactly `1 / sqrt(density)`: the margin only absorbs the float noise of
    // `density` itself, which would otherwise cost a whole row of cells (0.01 is not exact in binary).
    val cells = max(1, floor(size * sqrt(layer.density.toDouble()) * (1.0 + 1e-6)).toInt())
    return LayerGrid(cells, size.toFloat() / cells)
}

/**
 * The scatter generator: for each layer, one candidate per jittered grid cell, drawn and kept by an independent
 * SplitMix64 stream per cell (design decision 2). Equal settings, masks and terrain give byte-equal bakes, and
 * re-scattering a set of [chunks] gives exactly the bytes of the full bake's chunks, because a cell's draw depends
 * only on the seed, the layer id and the cell's own coordinates. Pure Kotlin over [TerrainData] and byte masks: it
 * runs off the GL thread and touches no `Gdx.*`.
 */
class FoliageScatter(private val fingerprints: FoliageFingerprint) {

    /**
     * The copies of [settings] over [terrain]. [masks] holds each layer's mask by layer id; a layer without an entry
     * behaves as a full mask. A candidate is kept when a draw falls below `mask / 255`, scaled to 0 where the height
     * or slope rules refuse the spot, and gets its model by weight, a yaw in `[0, 2π)` and a scale in the layer's
     * range.
     *
     * With [chunks] null the whole bake is generated and the limits hold: a layer over [FOLIAGE_CANDIDATE_LIMIT]
     * candidates, or a bake over [copyLimit] copies, is refused with an empty bake and a [ScatterRefusal] instead of
     * being built. The copy limit is checked against an estimate taken at the cell centers before generation, so an
     * over-dense layer is refused without generating every copy, and counted during generation as a backstop.
     *
     * With [chunks] set, only the copies whose candidates fall in those chunks are generated and the copy limit is
     * left to the caller, which merges the chunks into the full bake it already holds; the candidate guard still
     * applies, because those loops walk every cell.
     */
    fun scatter(
        terrain: TerrainData,
        settings: FoliageMeta,
        masks: Map<Int, ByteArray>,
        chunks: Set<FoliageChunk>? = null,
        copyLimit: Int = FOLIAGE_COPY_LIMIT,
    ): ScatterOutcome {
        val resolution = settings.maskResolution
        val fingerprint = fingerprints.of(settings, terrain, masks)
        val chunkSize = foliageChunkSize(terrain.size)
        val chunksX = max(1, ceil(terrain.size.toDouble() / chunkSize).toInt())
        val grids = settings.layers.map { layer -> gridOf(terrain.size, layer) }
        val layerMasks = settings.layers.map { layer -> masks[layer.id] ?: fullMask(resolution) }
        for (mask in layerMasks) {
            require(mask.size == resolution * resolution) { "a $resolution by $resolution mask" }
        }

        for (grid in grids) {
            if (grid != null) {
                val candidates = grid.cells.toLong() * grid.cells
                if (candidates > FOLIAGE_CANDIDATE_LIMIT) {
                    return refused(fingerprint, chunkSize, ScatterRefusal.CANDIDATE_LIMIT, candidates)
                }
            }
        }

        var estimate = 0.0
        if (chunks == null) {
            for (index in settings.layers.indices) {
                val grid = grids[index] ?: continue
                estimate += estimateLayer(settings.layers[index], grid, layerMasks[index], resolution, terrain)
            }
            if (estimate > copyLimit) {
                return refused(fingerprint, chunkSize, ScatterRefusal.COPY_LIMIT, estimate.toLong())
            }
        }

        val bakeLayers = ArrayList<FoliageLayerBake>(settings.layers.size)
        val counts = LinkedHashMap<Int, Int>(settings.layers.size)
        var total = 0
        for (index in settings.layers.indices) {
            val layer = settings.layers[index]
            val grid = grids[index]
            val lists = MutableList(chunksX * chunksX) { ArrayList<FoliageCopy>() }
            if (grid != null) {
                val mask = layerMasks[index]
                for (j in 0 until grid.cells) {
                    for (i in 0 until grid.cells) {
                        val stream = cellStream(layer.seed, layer.id, i, j)
                        val x = (i + 0.1f + 0.8f * stream.unit()) * grid.cell
                        val z = (j + 0.1f + 0.8f * stream.unit()) * grid.cell
                        val ci = minOf((x / chunkSize).toInt(), chunksX - 1)
                        val cj = minOf((z / chunkSize).toInt(), chunksX - 1)
                        if (chunks != null && FoliageChunk(ci, cj) !in chunks) continue
                        if (stream.unit() >= keepFactor(layer, mask, resolution, terrain, x, z)) continue
                        lists[cj * chunksX + ci].add(
                            FoliageCopy(
                                pickModel(layer.models, stream.unit()),
                                x,
                                z,
                                stream.unit() * TWO_PI,
                                layer.scale.min + stream.unit() * (layer.scale.max - layer.scale.min),
                            )
                        )
                        total++
                        if (chunks == null && total > copyLimit) {
                            return refused(fingerprint, chunkSize, ScatterRefusal.COPY_LIMIT, total.toLong())
                        }
                    }
                }
            }
            val bakeLayer = FoliageLayerBake(layer.id, layer.models.size, chunksX, chunksX, lists)
            bakeLayers += bakeLayer
            counts[layer.id] = bakeLayer.copyCount
        }
        return ScatterOutcome(FoliageBake(fingerprint, chunkSize, bakeLayers), counts, total, estimate = total.toLong())
    }

    /** The copy count the settings are expected to give: the keep factors sampled at every cell center. */
    private fun estimateLayer(
        layer: FoliageLayerMeta,
        grid: LayerGrid,
        mask: ByteArray,
        resolution: Int,
        terrain: TerrainData,
    ): Double {
        var estimate = 0.0
        for (j in 0 until grid.cells) {
            for (i in 0 until grid.cells) {
                val center = (i + 0.5f) * grid.cell
                estimate += keepFactor(layer, mask, resolution, terrain, center, (j + 0.5f) * grid.cell).toDouble()
            }
        }
        return estimate
    }

    /**
     * How likely the candidate at ([x], [z]) is kept: the mask value from 0 through 1, and 0 where the terrain's
     * height lies outside the layer's range or its slope is above the maximum. A spot outside the terrain is 0.
     */
    private fun keepFactor(
        layer: FoliageLayerMeta,
        mask: ByteArray,
        resolution: Int,
        terrain: TerrainData,
        x: Float,
        z: Float,
    ): Float {
        val allowed = maskAt(mask, resolution, terrain.size, x, z)
        if (allowed <= 0f) return 0f
        if (layer.minHeight != null || layer.maxHeight != null) {
            val height = terrain.heightAt(x, z) ?: return 0f
            layer.minHeight?.let { if (height < it) return 0f }
            layer.maxHeight?.let { if (height > it) return 0f }
        }
        layer.maxSlope?.let { if (terrainSlopeDegrees(terrain, x, z) > it) return 0f }
        return allowed
    }

    /** The index of the model picked by weight from [draw] in [0, 1); the last one when the weights run out. */
    private fun pickModel(models: List<FoliageModelMeta>, draw: Float): Int {
        var threshold = draw * models.sumOf { it.weight.toDouble() }
        for (index in models.indices) {
            threshold -= models[index].weight
            if (threshold < 0.0) return index
        }
        return models.lastIndex
    }

    /** An empty bake for [refusal]: a refusal is never merged, so it carries no layers and no counts. */
    private fun refused(
        fingerprint: ByteArray,
        chunkSize: Float,
        refusal: ScatterRefusal,
        estimate: Long,
    ): ScatterOutcome = ScatterOutcome(FoliageBake(fingerprint, chunkSize, emptyList()), emptyMap(), 0, refusal, estimate)
}

/** The mask value at terrain-local ([x], [z]), bilinear over [resolution] texels like `heightAt`, from 0 through 1. */
private fun maskAt(mask: ByteArray, resolution: Int, size: Int, x: Float, z: Float): Float {
    if (resolution < 2) return (mask.getOrElse(0) { FOLIAGE_MASK_MAX.toByte() }.toInt() and 0xFF) / 255f
    if (x < 0f || z < 0f || x > size || z > size) return 0f
    val step = size.toFloat() / (resolution - 1)
    val gx = minOf((x / step).toInt(), resolution - 2)
    val gz = minOf((z / step).toInt(), resolution - 2)
    val fx = x / step - gx
    val fz = z / step - gz
    val m00 = mask[gz * resolution + gx].toInt() and 0xFF
    val m10 = mask[gz * resolution + gx + 1].toInt() and 0xFF
    val m01 = mask[(gz + 1) * resolution + gx].toInt() and 0xFF
    val m11 = mask[(gz + 1) * resolution + gx + 1].toInt() and 0xFF
    return ((m00 * (1f - fx) + m10 * fx) * (1f - fz) + (m01 * (1f - fx) + m11 * fx) * fz) / 255f
}
