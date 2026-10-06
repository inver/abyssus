/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain.generation

import kotlin.random.Random

/**
 * A grayscale picture of a height field: [pixels] hold one byte per height, row after row, black at the settings'
 * minimum height and white at the maximum.
 */
class HeightmapImage(val width: Int, val height: Int, val pixels: ByteArray)

/** Draws [heights] (a square grid) as a [HeightmapImage] mapping [minHeight]..[maxHeight] onto black..white. */
fun heightmapImage(heights: FloatArray, resolution: Int, minHeight: Float, maxHeight: Float): HeightmapImage {
    require(heights.size == resolution * resolution) { "heights are not $resolution squared" }
    val span = (maxHeight - minHeight).takeIf { it > 0f && it.isFinite() } ?: 1f
    val pixels = ByteArray(heights.size) { i ->
        val unit = ((heights[i] - minHeight) / span).coerceIn(0f, 1f)
        Math.round(unit * 255f).toByte()
    }
    return HeightmapImage(resolution, resolution, pixels)
}

/** The state of the terrain a draft was started from; an Apply is only allowed while it still holds. */
data class SourceSnapshot(
    val metaText: String?,
    val heightsSha256: String?,
    val recipeText: String?,
    val folderExists: Boolean,
)

/** A preview that finished: [heights] for exactly these [settings], [size] and [resolution]. */
class TerrainPreview(
    val settings: TerrainGenerationSettings,
    val size: Int,
    val resolution: Int,
    val heights: FloatArray,
) {
    val image: HeightmapImage by lazy { heightmapImage(heights, resolution, settings.minHeight, settings.maxHeight) }
}

/** What a background generation is asked to make; [token] identifies it among the draft's requests. */
data class PreviewRequest(val token: Long, val settings: TerrainGenerationSettings, val size: Int, val resolution: Int)

/**
 * The draft of one terrain generation: current settings and geometry, which background request is the latest, the
 * finished preview and the source it must still match. Free of Swing, GL and threads: the caller serializes access
 * (the plugin uses the UI thread) and runs the generation itself. Only the latest request's result is accepted, and
 * any change to the settings, geometry or source, a cancel or a newer request makes the earlier results stale.
 */
class TerrainGenerationDraft(
    settings: TerrainGenerationSettings,
    size: Int,
    resolution: Int,
    private val source: SourceSnapshot,
) {
    var settings: TerrainGenerationSettings = settings
        private set
    var size: Int = size
        private set
    var resolution: Int = resolution
        private set

    private var latestToken = 0L
    private var pending: Long? = null
    private var sourceStale = false

    /** The finished preview for the current inputs, if any. */
    var preview: TerrainPreview? = null
        private set

    val generating: Boolean get() = pending != null

    /** Why generation cannot start for the current inputs; empty when it can. */
    fun settingsErrors(): List<SettingsError> = settings.errors()

    fun updateSettings(next: TerrainGenerationSettings) {
        if (next == settings) return
        settings = next
        invalidate()
    }

    /** Changes the geometry (a new terrain's inputs); the picture of other geometry is dropped. */
    fun updateGeometry(size: Int, resolution: Int) {
        if (size == this.size && resolution == this.resolution) return
        this.size = size
        this.resolution = resolution
        invalidate()
    }

    /** Changes only the seed, to a new value from [random] different from the current one. */
    fun randomizeSeed(random: Random) {
        var seed = random.nextInt()
        if (seed == settings.seed) seed = seed xor 1
        updateSettings(settings.copy(seed = seed))
    }

    /** Starts a preview of the current inputs; null when they are invalid. A newer request supersedes the pending one. */
    fun begin(): PreviewRequest? {
        if (settings.errors().isNotEmpty() || size <= 0 || resolution !in MIN_TERRAIN_RESOLUTION..255) return null
        preview = null
        latestToken++
        pending = latestToken
        return PreviewRequest(latestToken, settings, size, resolution)
    }

    /** Accepts [heights] for [request] when it is still the latest and the inputs did not change; false when stale. */
    fun complete(request: PreviewRequest, heights: FloatArray): Boolean {
        if (pending != request.token || request.settings != settings || request.size != size || request.resolution != resolution) return false
        pending = null
        preview = TerrainPreview(settings, size, resolution, heights)
        return true
    }

    /** Records that [request] failed; only the latest request clears the pending state. */
    fun fail(request: PreviewRequest) {
        if (pending == request.token) pending = null
    }

    /** Discards the pending request and the preview (Cancel, a changed selection, disposal). */
    fun cancel() = invalidate()

    /** The source changed outside the draft: no preview applies any more until a new one is made. */
    fun sourceChanged() {
        sourceStale = true
        invalidate()
    }

    /** True when the source is the one the draft started from (checked again by [canApply]'s caller on the commit). */
    fun matchesSource(current: SourceSnapshot): Boolean = !sourceStale && current == source

    /** The preview that may be applied now, or null: it must match the settings and geometry and the source. */
    fun applicable(current: SourceSnapshot): TerrainPreview? =
        preview?.takeIf { it.settings == settings && it.size == size && it.resolution == resolution && matchesSource(current) }

    private fun invalidate() {
        preview = null
        pending = null
        latestToken++
    }
}
