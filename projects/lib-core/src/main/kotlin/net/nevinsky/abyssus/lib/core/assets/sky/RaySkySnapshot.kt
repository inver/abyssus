/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.sky

import net.nevinsky.abyssus.lib.core.assets.loading.RaySnapshot

/** The widest equirectangular sky handed to a ray backend; the height is half of it. */
const val RAY_SKY_MAX_WIDTH = 1024

/**
 * An immutable CPU copy of the scene's sky as an equirectangular RGBA float image (centre column faces -Z, top row is
 * +Y, rows from the top), for ray tracing. [hdr] skies hold linear radiance, possibly far above 1; the others hold the
 * display values their images store (a baked procedural sky is such a display sample, with its clouds when it was baked
 * with them; the scene's linear diffuse sky light travels separately). Downsampled to at most [RAY_SKY_MAX_WIDTH] wide.
 * [revision] counts the bakes of one view's sky, so a consumer can tell a new bake from the old one.
 */
class RaySkySnapshot(val width: Int, val height: Int, rgba: FloatArray, val hdr: Boolean, val revision: Long = 0L) : RaySnapshot {
    private val pixels = rgba.copyOf()

    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height * 4)
    }

    override val byteSize: Long get() = pixels.size.toLong() * 4
    fun rgba(): FloatArray = pixels.copyOf()
}
