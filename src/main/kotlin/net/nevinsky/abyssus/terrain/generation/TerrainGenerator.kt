/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain.generation

import net.nevinsky.abyssus.core.assets.terrain.MAX_TERRAIN_RESOLUTION
import net.nevinsky.abyssus.terrain.noise.NoiseSamplerFactory

/** The smallest terrain resolution: two heights per side. */
const val MIN_TERRAIN_RESOLUTION = 2

private const val MAX_OCTAVES = 8
private const val MAX_LACUNARITY = 4f

/** Why a setting is refused; the plugin shows a localized message for each. */
enum class SettingsError {
    FEATURE_SIZE,
    HEIGHT_NOT_FINITE,
    HEIGHT_RANGE,
    OCTAVES,
    PERSISTENCE,
    LACUNARITY,
}

/**
 * What the noise is made from: an integer [seed], a [featureSize] in world units (the size of the biggest hills), the
 * [minHeight]..[maxHeight] the noise is mapped onto, and the fractal's [octaves], [persistence] (how much each octave
 * keeps of the one before) and [lacunarity] (how much finer each octave is).
 */
data class TerrainGenerationSettings(
    val seed: Int = 12345,
    val featureSize: Float = 200f,
    val minHeight: Float = 0f,
    val maxHeight: Float = 120f,
    val octaves: Int = 5,
    val persistence: Float = 0.5f,
    val lacunarity: Float = 2f,
) {
    /** Every setting that is not acceptable, in field order; empty when generation may run. */
    fun errors(): List<SettingsError> = buildList {
        if (!featureSize.isFinite() || featureSize <= 0f) add(SettingsError.FEATURE_SIZE)
        if (!minHeight.isFinite() || !maxHeight.isFinite()) add(SettingsError.HEIGHT_NOT_FINITE)
        else if (minHeight >= maxHeight) add(SettingsError.HEIGHT_RANGE)
        if (octaves !in 1..MAX_OCTAVES) add(SettingsError.OCTAVES)
        if (!persistence.isFinite() || persistence < 0f || persistence > 1f) add(SettingsError.PERSISTENCE)
        if (!lacunarity.isFinite() || lacunarity < 1f || lacunarity > MAX_LACUNARITY) add(SettingsError.LACUNARITY)
    }

    val valid: Boolean get() = errors().isEmpty()
}

/**
 * Makes terrain heights from [TerrainGenerationSettings]: fractal noise sampled at world positions, so the same
 * settings, size and noise generator give the same heights at any resolution, then mapped onto the height range.
 * Heights are laid out row after row (z-major) as [net.nevinsky.abyssus.core.assets.terrain.TerrainData] expects. Pure
 * CPU work, safe on any thread.
 */
class TerrainGenerator(private val noise: NoiseSamplerFactory) {
    /**
     * The `resolution` x `resolution` heights of a terrain [size] world units wide. [checkCancelled] is called once
     * per row and may throw to stop; nothing is returned in that case.
     */
    fun generate(
        resolution: Int,
        size: Int,
        settings: TerrainGenerationSettings,
        checkCancelled: () -> Unit = {},
    ): FloatArray {
        require(resolution in MIN_TERRAIN_RESOLUTION..MAX_TERRAIN_RESOLUTION) { "resolution $resolution is outside 2..$MAX_TERRAIN_RESOLUTION" }
        require(size > 0) { "size must be positive" }
        require(settings.valid) { "invalid generation settings: ${settings.errors()}" }

        val sampler = noise.create(settings.seed, 1f / settings.featureSize, settings.octaves, settings.persistence, settings.lacunarity)
        val min = settings.minHeight.toDouble()
        val span = settings.maxHeight.toDouble() - min
        val step = size.toDouble() / (resolution - 1)
        val heights = FloatArray(resolution * resolution)
        for (z in 0 until resolution) {
            checkCancelled()
            val wz = (z * step).toFloat()
            for (x in 0 until resolution) {
                val n = sampler.sample((x * step).toFloat(), wz)
                require(n.isFinite()) { "noise produced a non-finite value" }
                val unit = ((n.toDouble().coerceIn(-1.0, 1.0)) + 1.0) / 2.0
                val h = (min + unit * span).toFloat().coerceIn(settings.minHeight, settings.maxHeight)
                require(h.isFinite()) { "height is not finite" }
                heights[z * resolution + x] = h
            }
        }
        return heights
    }
}
