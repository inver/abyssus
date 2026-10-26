/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SPLAT_MAX_SIDE
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SPLAT_MIN_SIDE
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatImageCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatImageException
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatImageProblem
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatPixels
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Reads and writes the splat map as an 8-bit RGBA PNG whose colour values are the weights, never premultiplied by the
 * alpha (the A weight). Strict on purpose: an image of another kind is reported rather than converted, since a
 * conversion would change what the shader reads. Pure CPU work, safe on any thread.
 */
class ImageIoSplatCodec : SplatImageCodec {
    /** Decodes [bytes] of the file [fileName]; throws [SplatImageException] with the reason when it cannot be painted. */
    override fun decode(fileName: String, bytes: ByteArray): SplatPixels {
        if (!fileName.endsWith(".png", ignoreCase = true)) throw SplatImageException(SplatImageProblem.NOT_PNG, fileName)
        val image = runCatchingKeepingCancellation { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw SplatImageException(SplatImageProblem.UNREADABLE)
        val side = image.width
        // ImageIO gives 8-bit non-premultiplied RGBA PNGs as TYPE_4BYTE_ABGR; RGB, grey, palette and 16-bit are other types.
        val eightBitRgba = image.type == BufferedImage.TYPE_4BYTE_ABGR
        if (!eightBitRgba) throw SplatImageException(SplatImageProblem.NOT_RGBA8)
        if (image.width != image.height) throw SplatImageException(SplatImageProblem.NOT_SQUARE, "${image.width}x${image.height}")
        if (side < SPLAT_MIN_SIDE) throw SplatImageException(SplatImageProblem.TOO_SMALL, "$side")
        if (side > SPLAT_MAX_SIDE) throw SplatImageException(SplatImageProblem.TOO_LARGE, "$side")
        val out = ByteArray(side * side * 4)
        val row = IntArray(side * 4)
        val raster = image.raster
        for (y in 0 until side) {
            raster.getPixels(0, y, side, 1, row)
            val base = y * side * 4
            for (x in 0 until side) {
                out[base + x * 4] = row[x * 4].toByte()
                out[base + x * 4 + 1] = row[x * 4 + 1].toByte()
                out[base + x * 4 + 2] = row[x * 4 + 2].toByte()
                out[base + x * 4 + 3] = row[x * 4 + 3].toByte()
            }
        }
        return SplatPixels(side, out)
    }

    /** Encodes [pixels] as an 8-bit RGBA PNG; the colour values are written as they are. */
    override fun encode(pixels: SplatPixels): ByteArray {
        val side = pixels.side
        val image = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
        val raster = image.raster
        val row = IntArray(side * 4)
        for (y in 0 until side) {
            val base = y * side * 4
            for (i in 0 until side * 4) row[i] = pixels.rgba[base + i].toInt() and 0xFF
            raster.setPixels(0, y, side, 1, row)
        }
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, "png", out)) { "no PNG writer" }
        return out.toByteArray()
    }
}
