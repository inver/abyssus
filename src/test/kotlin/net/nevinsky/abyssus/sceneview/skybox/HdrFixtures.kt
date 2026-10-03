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

package net.nevinsky.abyssus.sceneview.skybox

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Writes Radiance `.hdr` files from a pixel function, so tests can build their own skies. */
object HdrFixtures {
    const val FIXTURE_WIDTH = 64
    const val FIXTURE_HEIGHT = 32

    /**
     * The pixels of the `Untitled` fixture `assets/skybox_hdr/sky.hdr`: a blue sky brightening toward the zenith, a sun
     * patch far above white, and a dark ground below the horizon. The committed file was written by [bytes] from this
     * function.
     */
    fun skyPixel(x: Int, y: Int): FloatArray = when {
        x in 30..33 && y in 4..6 -> floatArrayOf(20f, 18f, 14f)
        y < 16 -> (1f + (15 - y) / 15f).let { k -> floatArrayOf(0.3f * k, 0.5f * k, 1.0f * k) }
        else -> floatArrayOf(0.08f, 0.06f, 0.04f)
    }

    /** One RGBE pixel as Radiance stores it. */
    fun rgbe(r: Float, g: Float, b: Float): ByteArray {
        val v = max(r, max(g, b))
        if (v < 1e-32f) return byteArrayOf(0, 0, 0, 0)
        val e = Math.getExponent(v) + 1 // v = m * 2^e with m in [0.5, 1)
        val scale = 256f / Math.scalb(1f, e)
        return byteArrayOf((r * scale).toInt().toByte(), (g * scale).toInt().toByte(), (b * scale).toInt().toByte(), (e + 128).toByte())
    }

    /** A complete `.hdr` file of [width] x [height] pixels from [pixel]; run-length encoded scanlines unless [flat]. */
    fun bytes(width: Int, height: Int, flat: Boolean = false, header: String? = null, pixel: (Int, Int) -> FloatArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((header ?: "#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y $height +X $width\n").toByteArray(Charsets.US_ASCII))
        for (y in 0 until height) {
            val row = Array(width) { x -> pixel(x, y).let { rgbe(it[0], it[1], it[2]) } }
            if (flat) row.forEach(out::write) else writeRle(out, row)
        }
        return out.toByteArray()
    }

    fun write(file: File, width: Int, height: Int, flat: Boolean = false, pixel: (Int, Int) -> FloatArray): File {
        file.parentFile.mkdirs()
        file.writeBytes(bytes(width, height, flat, pixel = pixel))
        return file
    }

    /** A uniform sky of [radiance] in every pixel. */
    fun uniform(radiance: Float): (Int, Int) -> FloatArray = { _, _ -> floatArrayOf(radiance, radiance, radiance) }

    /** New-style run-length encoding: a `2 2 hi lo` marker, then each channel as runs and literal spans. */
    private fun writeRle(out: ByteArrayOutputStream, row: Array<ByteArray>) {
        val width = row.size
        out.write(2); out.write(2); out.write(width shr 8); out.write(width and 0xff)
        for (c in 0 until 4) {
            val data = ByteArray(width) { row[it][c] }
            var i = 0
            while (i < width) {
                var run = 1
                while (i + run < width && run < 127 && data[i + run] == data[i]) run++
                if (run >= 3) {
                    out.write(128 + run); out.write(data[i].toInt() and 0xff)
                    i += run
                } else {
                    var n = 0
                    while (i + n < width && n < 128) {
                        if (i + n + 2 < width && data[i + n] == data[i + n + 1] && data[i + n] == data[i + n + 2]) break
                        n++
                    }
                    out.write(n)
                    out.write(data, i, n)
                    i += n
                }
            }
        }
    }
}
