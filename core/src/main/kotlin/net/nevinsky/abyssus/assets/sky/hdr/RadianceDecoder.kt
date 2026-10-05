/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.hdr

import java.io.*

/** A Radiance file this view does not read, or a broken one; the message says which header line or why. */
class RadianceFormatException(message: String) : IOException(message)

/** The size a Radiance header declares, checked against what the view supports before any pixel is read. */
data class RadianceHeader(val width: Int, val height: Int)

/**
 * Decodes Radiance `.hdr` images: flat or new-style run-length encoded RGBE scanlines. An image wider than the
 * requested width is reduced while reading by averaging blocks of pixels, so only a few source rows are ever held.
 * No GL; runs on any thread.
 */
class RadianceDecoder {
    private val resolution = Regex("""-Y (\d+) \+X (\d+)""")

    /**
     * Reads the header lines of [input], leaving it at the first scanline. Throws [RadianceFormatException] for a
     * file that is not a Radiance image, an unsupported `FORMAT` or orientation, or a size that is not 2:1 or over
     * [MAX_HDR_HEIGHT]. `EXPOSURE` and every other header line are ignored.
     */
    fun header(input: InputStream): RadianceHeader {
        val magic = line(input)
        if (magic == null || !(magic.startsWith("#?RADIANCE") || magic.startsWith("#?RGBE"))) {
            throw RadianceFormatException("not a Radiance image")
        }
        while (true) {
            val l = line(input) ?: throw RadianceFormatException("truncated header")
            if (l.isEmpty()) break
            if (l.startsWith("FORMAT=") && l != "FORMAT=32-bit_rle_rgbe") throw RadianceFormatException("unsupported header line '$l'")
        }
        val res = line(input) ?: throw RadianceFormatException("truncated header")
        val m = resolution.matchEntire(res) ?: throw RadianceFormatException("unsupported header line '$res'")
        val height = m.groupValues[1].toIntOrNull() ?: throw RadianceFormatException("image too large: '$res'")
        val width = m.groupValues[2].toIntOrNull() ?: throw RadianceFormatException("image too large: '$res'")
        if (height < 1 || width.toLong() != height * 2L) throw RadianceFormatException("$width x $height is not a 2:1 equirectangular image")
        if (height > MAX_HDR_HEIGHT) throw RadianceFormatException("image too large: $width x $height, at most ${MAX_HDR_HEIGHT * 2} x $MAX_HDR_HEIGHT")
        return RadianceHeader(width, height)
    }

    /** One header line without its newline; null at the end of the input. Header lines are short. */
    private fun line(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (sb.length > 4096) throw RadianceFormatException("not a Radiance image")
            sb.append(c.toChar())
        }
    }

    fun read(file: File, maxWidth: Int = MAX_HDR_WIDTH): HdrImage =
        BufferedInputStream(file.inputStream(), 1 shl 16).use { read(it, maxWidth) }

    /** Reads a whole image from [input], halving it until it is at most [maxWidth] wide. */
    fun read(input: InputStream, maxWidth: Int = MAX_HDR_WIDTH): HdrImage {
        val header = header(input)
        var factor = 1
        while (header.width / factor > maxWidth && header.height / factor > 1) factor *= 2
        val outW = header.width / factor
        val outH = header.height / factor
        val out = ShortArray(outW * outH * 3)
        val row = ByteArray(header.width * 4)
        val sum = FloatArray(outW * 3)
        val weight = 1f / (factor * factor)
        for (y in 0 until outH * factor) {
            readScanline(input, row, header.width)
            for (x in 0 until outW * factor) {
                val i = x * 4
                val e = row[i + 3].toInt() and 0xff
                if (e == 0) continue
                val f = Math.scalb(1f, e - 136)
                val o = (x / factor) * 3
                sum[o] += (row[i].toInt() and 0xff) * f
                sum[o + 1] += (row[i + 1].toInt() and 0xff) * f
                sum[o + 2] += (row[i + 2].toInt() and 0xff) * f
            }
            if ((y + 1) % factor == 0) {
                val base = (y / factor) * outW * 3
                for (i in sum.indices) {
                    out[base + i] = java.lang.Float.floatToFloat16(minOf(sum[i] * weight, 65504f))
                    sum[i] = 0f
                }
            }
        }
        return HdrImage(outW, outH, out)
    }

    private fun readScanline(input: InputStream, row: ByteArray, width: Int) {
        readFully(input, row, 0, 4)
        val newRle = width in 8..32767 && row[0].toInt() == 2 && row[1].toInt() == 2 && (row[2].toInt() and 0x80) == 0
        if (!newRle) {
            if (row[0].toInt() == 1 && row[1].toInt() == 1 && row[2].toInt() == 1) {
                throw RadianceFormatException("old-style run-length encoding is not supported")
            }
            readFully(input, row, 4, row.size - 4)
            return
        }
        val encoded = ((row[2].toInt() and 0xff) shl 8) or (row[3].toInt() and 0xff)
        if (encoded != width) throw RadianceFormatException("scanline width $encoded does not match the image width $width")
        for (c in 0 until 4) {
            var x = 0
            while (x < width) {
                var count = next(input)
                if (count > 128) {
                    count -= 128
                    if (x + count > width) throw RadianceFormatException("bad run-length data")
                    val v = next(input).toByte()
                    repeat(count) { row[(x + it) * 4 + c] = v }
                } else {
                    if (count == 0 || x + count > width) throw RadianceFormatException("bad run-length data")
                    repeat(count) { row[(x + it) * 4 + c] = next(input).toByte() }
                }
                x += count
            }
        }
    }

    private fun next(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw RadianceFormatException("truncated image data")
        return b
    }

    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int) {
        try {
            var n = 0
            while (n < len) {
                val r = input.read(buf, off + n, len - n)
                if (r < 0) throw EOFException()
                n += r
            }
        } catch (_: EOFException) {
            throw RadianceFormatException("truncated image data")
        }
    }
}
