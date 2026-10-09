/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.assets.sky

import net.nevinsky.abyssus.lib.gdx.assets.loading.RaySnapshot

/** The widest equirectangular sky handed to a ray backend; the height is half of it. */
const val RAY_SKY_MAX_WIDTH = 1024

/**
 * An immutable CPU copy of the scene's sky as an equirectangular RGBA float image (centre column faces -Z, top row is
 * +Y, rows from the top), for ray tracing. [hdr] skies hold linear radiance, possibly far above 1; the others hold the
 * display values their images store. Downsampled to at most [RAY_SKY_MAX_WIDTH] wide.
 */
class RaySkySnapshot(val width: Int, val height: Int, rgba: FloatArray, val hdr: Boolean) : RaySnapshot {
    private val pixels = rgba.copyOf()

    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height * 4)
    }

    override val byteSize: Long get() = pixels.size.toLong() * 4
    fun rgba(): FloatArray = pixels.copyOf()
}
