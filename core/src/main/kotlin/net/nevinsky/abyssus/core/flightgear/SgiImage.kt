/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.flightgear

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/** An SGI image that cannot be decoded, with the reason. */
class SgiImageException(message: String) : RuntimeException(message)

/** File name extensions of SGI images. */
val SGI_EXTENSIONS = setOf("rgb", "rgba", "sgi", "bw", "int", "inta")

/**
 * SGI images (magic 474), verbatim or RLE, one byte per channel, 1-4 channels: grey, grey and alpha, RGB, RGBA. Rows
 * are stored bottom-up; [decode] returns them top-down.
 */
class SgiImage {
    fun decode(bytes: ByteArray): BufferedImage {
        if (bytes.size < 512) throw SgiImageException("shorter than an SGI header")
        val data = ByteBuffer.wrap(bytes)
        if (data.getShort(0).toInt() != 474) throw SgiImageException("not an SGI image")
        val rle = bytes[2].toInt() == 1
        val bpc = bytes[3].toInt()
        if (bpc != 1) throw SgiImageException("$bpc bytes per channel are not supported")
        val width = data.getShort(6).toInt() and 0xFFFF
        val height = data.getShort(8).toInt() and 0xFFFF
        val channels = (data.getShort(10).toInt() and 0xFFFF).coerceAtLeast(1)
        if (width == 0 || height == 0 || channels > 4) throw SgiImageException("unsupported size ${width}x$height, $channels channels")
        if (width.toLong() * height > 64L * 1024 * 1024) throw SgiImageException("image too large")
        val planes = Array(channels) { ByteArray(width * height) }
        for (c in 0 until channels) for (row in 0 until height) {
            val target = planes[c]
            if (rle) {
                val table = 512 + (row + c * height) * 4
                if (table + 4 > bytes.size) throw SgiImageException("truncated RLE table")
                var p = data.getInt(table)
                var x = 0
                while (true) {
                    if (p !in bytes.indices) throw SgiImageException("truncated RLE data")
                    val control = bytes[p++].toInt() and 0xFF
                    val count = control and 0x7F
                    if (count == 0) break
                    if (x + count > width) throw SgiImageException("RLE row longer than the image")
                    if (control and 0x80 != 0) {
                        if (p + count > bytes.size) throw SgiImageException("truncated RLE data")
                        System.arraycopy(bytes, p, target, row * width + x, count)
                        p += count
                    } else {
                        if (p >= bytes.size) throw SgiImageException("truncated RLE data")
                        java.util.Arrays.fill(target, row * width + x, row * width + x + count, bytes[p++])
                    }
                    x += count
                }
            } else {
                val start = 512 + (c * height + row) * width
                if (start + width > bytes.size) throw SgiImageException("truncated image data")
                System.arraycopy(bytes, start, target, row * width, width)
            }
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (row in 0 until height) for (x in 0 until width) {
            val i = row * width + x
            fun v(c: Int) = planes[c][i].toInt() and 0xFF
            val (r, g, b, a) = when (channels) {
                1 -> listOf(v(0), v(0), v(0), 255)
                2 -> listOf(v(0), v(0), v(0), v(1))
                3 -> listOf(v(0), v(1), v(2), 255)
                else -> listOf(v(0), v(1), v(2), v(3))
            }
            image.setRGB(x, height - 1 - row, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
        return image
    }

    /** [bytes] as PNG. */
    fun toPng(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { ImageIO.write(decode(bytes), "png", it) }.toByteArray()
}
