/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.sky.hdr

import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

/** Small tone-mapped pictures of HDR skies for the chooser and the properties panel. No GL; call off the EDT. */
class HdrPreview(private val loader: ExrLoader, private val curve: ToneCurve) {
    /** Original dimensions without decoding pixels or applying preview reduction. Call off the EDT. */
    fun dimensions(file: File): Pair<Int, Int> = loader.dimensions(file)

    /**
     * The `.exr` [file] decoded at no more than about twice [maxWidth] (halved while reading), tone mapped as the scene
     * view draws it, and scaled to at most [maxWidth] wide. Throws [ExrFormatException] for an unreadable image.
     */
    fun image(file: File, maxWidth: Int): BufferedImage = image(loader.decode(file, maxOf(maxWidth * 2, 2)), maxWidth)

    fun image(hdr: HdrImage, maxWidth: Int): BufferedImage {
        val w = minOf(maxWidth, hdr.width).coerceAtLeast(1)
        val h = (w / 2).coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            // nearest source pixel: the decoded image is already within twice the target size
            val p = hdr.pixel(x * hdr.width / w, y * hdr.height / h)
            out.setRGB(x, y, (byte(p[0]) shl 16) or (byte(p[1]) shl 8) or byte(p[2]))
        }
        return out
    }

    private fun byte(radiance: Float) = (curve.display(radiance) * 255f).roundToInt().coerceIn(0, 255)
}
