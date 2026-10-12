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
class RaySkySnapshot private constructor(
    val width: Int, val height: Int, floats: FloatArray?, bytes: ByteArray?, val hdr: Boolean, val revision: Long,
) : RaySnapshot {
    constructor(width: Int, height: Int, rgba: FloatArray, hdr: Boolean, revision: Long = 0L) :
        this(width, height, rgba.copyOf(), null, hdr, revision)

    /** From 8-bit RGBA display values (a quarter of the memory); only for skies that are not [hdr]. */
    constructor(width: Int, height: Int, rgba8: ByteArray, hdr: Boolean, revision: Long = 0L) :
        this(width, height, null, rgba8.copyOf(), hdr, revision)

    private val floats = floats
    private val bytes = bytes

    init {
        require(width > 0 && height > 0 && (floats?.size ?: bytes!!.size).toLong() == width.toLong() * height * 4)
        require(bytes == null || !hdr) { "An 8-bit sky cannot hold HDR radiance" }
    }

    override val byteSize: Long get() = floats?.let { it.size.toLong() * 4 } ?: bytes!!.size.toLong()

    /** The sky as linear-looking floats; an 8-bit sky is converted (prefer [rgba8] for those). */
    fun rgba(): FloatArray = floats?.copyOf() ?: FloatArray(bytes!!.size) { (bytes[it].toInt() and 255) / 255f }

    /** The 8-bit RGBA bytes of an 8-bit sky, else null. */
    fun rgba8(): ByteArray? = bytes?.copyOf()
}
