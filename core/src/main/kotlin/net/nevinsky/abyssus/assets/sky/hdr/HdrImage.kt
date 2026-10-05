/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.hdr

/** An equirectangular image as RGB half floats, row by row from the top, the layout an `RGB16F` texture takes. */
class HdrImage(val width: Int, val height: Int, val rgb: ShortArray) {
    /** The radiance of pixel ([x], [y]) as floats. */
    fun pixel(x: Int, y: Int): FloatArray {
        val i = (y * width + x) * 3
        return floatArrayOf(
            java.lang.Float.float16ToFloat(rgb[i]),
            java.lang.Float.float16ToFloat(rgb[i + 1]),
            java.lang.Float.float16ToFloat(rgb[i + 2])
        )
    }
}

/** The tallest equirectangular image the view takes; the widest is twice that. */
const val MAX_HDR_HEIGHT = 4096

/** The widest image handed to the GPU. */
const val MAX_HDR_WIDTH = 4096
