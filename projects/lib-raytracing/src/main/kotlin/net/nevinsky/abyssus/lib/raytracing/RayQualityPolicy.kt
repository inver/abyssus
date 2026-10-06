/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import kotlin.math.sqrt

/** Host frame/presentation payload bounds; scene structures and decoder memory have separate budgets. */
data class RayQualityLimits(
    val maxDimension: Int = 4096,
    val maxPixels: Long = 4_194_304,
    val frameMemoryBytes: Long = 128L * 1024 * 1024,
    val bytesPerPixel: Int = 80,
    val maxSamples: Int = 8,
    val maxRaysPerFrame: Long = 2_097_152,
    val maxAccumulatedSamples: Int = 256,
    val minimumScale: Double = 0.5,
    val targetFrameNanos: Long = 33_333_333,
) {
    init {
        require(maxDimension > 0 && maxPixels > 0 && frameMemoryBytes > 0 && bytesPerPixel > 0)
        require(maxSamples in 1..64 && maxRaysPerFrame > 0 && maxAccumulatedSamples > 0 && targetFrameNanos > 0)
        require(minimumScale.isFinite() && minimumScale > 0 && minimumScale <= 1)
    }
}

data class RayRenderQuality(val width: Int, val height: Int, val samples: Int, val frameBytes: Long)
class RayQualityLimitException(message: String) : IllegalStateException(message)

/** Owned by the serial rendering worker. Adaptation never goes below the declared minimum resolution. */
class RayQualityPolicy(val limits: RayQualityLimits = RayQualityLimits()) {
    private var meanNanos: Double? = null
    private var scale = limits.minimumScale

    fun observe(elapsedNanos: Long) {
        require(elapsedNanos >= 0)
        val elapsed = elapsedNanos.coerceAtLeast(1).toDouble()
        meanNanos = meanNanos?.let { it * 0.75 + elapsed * 0.25 } ?: elapsed
    }

    fun choose(
        outputWidth: Int,
        outputHeight: Int,
        stableFrames: Int,
        raysPerSample: Int = 1,
        maxRaysPerFrame: Long = limits.maxRaysPerFrame
    ): RayRenderQuality {
        require(outputWidth > 0 && outputHeight > 0 && stableFrames >= 0 && raysPerSample > 0 && maxRaysPerFrame > 0)
        // All bounds are hard, including every intersection query in the submitted frame.
        val memoryPixelCap = minOf(limits.maxPixels, limits.frameMemoryBytes / limits.bytesPerPixel)
        val pixelCap = minOf(memoryPixelCap, maxRaysPerFrame / raysPerSample)
        fun dimension(size: Int, factor: Double) = maxOf(1, (size * factor).toInt())
        fun fits(factor: Double, cap: Long): Boolean {
            val width = dimension(outputWidth, factor)
            val height = dimension(outputHeight, factor)
            return width <= limits.maxDimension && height <= limits.maxDimension && width.toLong() * height <= cap
        }

        fun fits(factor: Double) = fits(factor, pixelCap)
        if (!fits(
                limits.minimumScale,
                memoryPixelCap
            )
        ) throw RayQualityLimitException("Minimum ray frame exceeds dimension, pixel or memory bounds")
        if (!fits(limits.minimumScale)) {
            throw RayQualityLimitException("Saved ray budget is too small for the minimum ray frame")
        }
        meanNanos?.let {
            scale = when {
                it > limits.targetFrameNanos * 1.1 -> scale * sqrt(limits.targetFrameNanos / it)
                it < limits.targetFrameNanos * 0.65 -> scale * 1.125
                else -> scale
            }.coerceIn(limits.minimumScale, 1.0)
        }
        var selected = if (stableFrames < 2) limits.minimumScale else scale
        if (!fits(selected)) {
            var low = limits.minimumScale
            var high = selected
            repeat(32) {
                val middle = (low + high) * 0.5
                if (fits(middle)) low = middle else high = middle
            }
            selected = low
        }
        val width = dimension(outputWidth, selected)
        val height = dimension(outputHeight, selected)
        val pixels = width.toLong() * height
        val timingSamples =
            meanNanos?.let { (limits.targetFrameNanos / it).toInt().coerceIn(1, limits.maxSamples) } ?: 1
        val samples = minOf(
            limits.maxSamples, timingSamples, 1 shl minOf(stableFrames / 2, 6),
            (maxRaysPerFrame / pixels / raysPerSample).coerceIn(1, 64).toInt()
        )
        return RayRenderQuality(width, height, samples, pixels * limits.bytesPerPixel)
    }
}
