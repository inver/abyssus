/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater

/** An SGI image that cannot be decoded, with the reason. */
class SgiImageException(message: String) : RuntimeException(message)

/** File name extensions of SGI images. */
val SGI_EXTENSIONS = setOf("rgb", "rgba", "sgi", "bw", "int", "inta")

/** Decoded pixels, top row first, as `0xAARRGGBB`. */
class SgiPixels(val width: Int, val height: Int, private val argb: IntArray) {
    fun getRGB(x: Int, y: Int): Int = argb[y * width + x]

    /** As an 8-bit RGBA PNG (no filtering, zlib's default compression); the bytes depend only on the pixels. */
    fun toPng(): ByteArray {
        val raw = ByteArray(height * (1 + width * 4))
        var p = 0
        for (y in 0 until height) {
            raw[p++] = 0 // filter: none
            for (x in 0 until width) {
                val c = argb[y * width + x]
                raw[p++] = (c shr 16).toByte()
                raw[p++] = (c shr 8).toByte()
                raw[p++] = c.toByte()
                raw[p++] = (c ushr 24).toByte()
            }
        }
        val deflater = Deflater()
        val compressed = ByteArrayOutputStream()
        try {
            deflater.setInput(raw)
            deflater.finish()
            val buffer = ByteArray(1 shl 16)
            while (!deflater.finished()) compressed.write(buffer, 0, deflater.deflate(buffer))
        } finally {
            deflater.end()
        }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put(8).put(6).put(0).put(0).put(0).array() // 8 bits per channel, RGBA, deflate, no filter, no interlace
        chunk(out, "IHDR", header)
        chunk(out, "IDAT", compressed.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }
        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(typeBytes)
        out.write(data)
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }
}

/**
 * SGI images (magic 474), verbatim or RLE, one byte per channel, 1-4 channels: grey, grey and alpha, RGB, RGBA. Rows
 * are stored bottom-up; [decode] returns them top-down.
 */
class SgiImage {
    fun decode(bytes: ByteArray): SgiPixels {
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
        val argb = IntArray(width * height)
        for (row in 0 until height) for (x in 0 until width) {
            val i = row * width + x
            fun v(c: Int) = planes[c][i].toInt() and 0xFF
            val (r, g, b, a) = when (channels) {
                1 -> listOf(v(0), v(0), v(0), 255)
                2 -> listOf(v(0), v(0), v(0), v(1))
                3 -> listOf(v(0), v(1), v(2), 255)
                else -> listOf(v(0), v(1), v(2), v(3))
            }
            argb[(height - 1 - row) * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return SgiPixels(width, height, argb)
    }

    /** [bytes] as PNG. */
    fun toPng(bytes: ByteArray): ByteArray = decode(bytes).toPng()
}
