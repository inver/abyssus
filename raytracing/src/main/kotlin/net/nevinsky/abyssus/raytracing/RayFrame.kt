/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

/** Structural generations reject incompatible frames; ordinary motion revisions need not match exactly. */
data class RayFrameKey(
    val sceneGeneration: Long,
    val contextGeneration: Long,
    val cameraRevision: Long,
    val poseRevision: Long,
)

/**
 * Completed host-memory frame: tightly packed linear RGBA floats and GL window depth in [0,1], with misses at 1.
 * Pixels are row-major from the bottom-left. Copies isolate native producer memory and GL upload consumers.
 */
class RayFrame(
    val key: RayFrameKey,
    val width: Int,
    val height: Int,
    color: FloatArray,
    depth: FloatArray,
) {
    private val color: FloatArray
    private val depth: FloatArray

    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive" }
        val pixels = width.toLong() * height
        require(pixels <= Int.MAX_VALUE / 4 && color.size.toLong() == pixels * 4 && depth.size.toLong() == pixels) {
            "Frame buffers must match dimensions"
        }
        require(color.all { it.isFinite() }) { "Color must contain finite linear values" }
        require(depth.all { it.isFinite() && it in 0f..1f }) { "Depth must use GL window depth" }
        this.color = color.copyOf()
        this.depth = depth.copyOf()
    }

    fun colorValues(): FloatArray = color.copyOf()
    fun depthValues(): FloatArray = depth.copyOf()
}
