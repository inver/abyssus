/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.assets.sky.hdr

import java.awt.image.BufferedImage
import java.io.InputStream
import kotlin.math.roundToInt

/** Small tone-mapped pictures of HDR skies for the chooser and the properties panel. No GL; call off the EDT. */
class HdrPreview(private val decoder: RadianceDecoder, private val curve: ToneCurve) {
    /**
     * [input] decoded at no more than about twice [maxWidth] (halved while reading), tone mapped as the scene view
     * draws it, and scaled to at most [maxWidth] wide. Throws [RadianceFormatException] for an unreadable image.
     */
    fun image(input: InputStream, maxWidth: Int): BufferedImage {
        val hdr = decoder.read(input, maxOf(maxWidth * 2, 2))
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
