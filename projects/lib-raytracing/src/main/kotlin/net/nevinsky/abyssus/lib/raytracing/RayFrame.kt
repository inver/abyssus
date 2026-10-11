/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

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
class RayFrame internal constructor(
    val key: RayFrameKey,
    val width: Int,
    val height: Int,
    color: FloatArray,
    depth: FloatArray,
    adopt: Boolean,
) {
    constructor(key: RayFrameKey, width: Int, height: Int, color: FloatArray, depth: FloatArray) :
        this(key, width, height, color, depth, false)

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
        // adopted arrays are owned by this frame from now on (the caller never touches them again), which saves a copy per frame
        this.color = if (adopt) color else color.copyOf()
        this.depth = if (adopt) depth else depth.copyOf()
    }

    fun colorValues(): FloatArray = color.copyOf()
    fun depthValues(): FloatArray = depth.copyOf()

    /** Writes the color values at the buffer's position without an intermediate copy. */
    fun copyColorTo(target: java.nio.FloatBuffer) { target.put(color) }
    fun copyDepthTo(target: java.nio.FloatBuffer) { target.put(depth) }

    /** Read-only views for in-module consumers that never mutate or retain them beyond this frame's life. */
    internal fun colorView(): FloatArray = color
    internal fun depthView(): FloatArray = depth
}
