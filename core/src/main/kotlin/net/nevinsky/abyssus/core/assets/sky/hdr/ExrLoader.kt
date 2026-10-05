/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets.sky.hdr

import net.nevinsky.abyssus.core.FileLoader
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.EXRVersion
import org.lwjgl.util.tinyexr.TinyEXR
import java.io.File
import java.nio.FloatBuffer

/** The largest finite half float. */
private const val MAX_HALF = 65504f

/**
 * Reads equirectangular OpenEXR images with tinyexr into an [HdrImage]. The native [EXRImage] is only held while
 * decoding: pixels are requested as 32-bit floats, copied (and reduced by averaging blocks when wider than the
 * requested width) into half floats, and every native struct is freed. No GL; runs on any thread.
 */
class ExrLoader(private val fileLoader: FileLoader) {

    /** The `.exr` [fileName] of asset [assetName], decoded at no more than [maxWidth] wide. */
    fun loadExr(assetName: String, fileName: String?, maxWidth: Int = MAX_HDR_WIDTH): HdrImage =
        decode(fileLoader.loadAssetFile(assetName, fileName), maxWidth)

    /**
     * Decodes [file], halving it until it is at most [maxWidth] wide. Scanline and tiled (one level or mipmapped)
     * files are read. Throws [IllegalStateException] ([ExrFormatException] for the image checks) for a file that is not a single-part OpenEXR image, has no colour
     * channels or only subsampled ones, is not 2:1 or is over [MAX_HDR_HEIGHT] tall.
     */
    fun decode(file: File, maxWidth: Int = MAX_HDR_WIDTH): HdrImage {
        val path = file.absolutePath
        MemoryStack.stackPush().use { stack ->
            val version = EXRVersion.malloc(stack)
            if (TinyEXR.ParseEXRVersionFromFile(version, path) != TinyEXR.TINYEXR_SUCCESS) {
                throw IllegalStateException("not an OpenEXR image: ${file.name}")
            }
            if (version.multipart()) throw IllegalStateException("multipart EXR files are not supported")

            val error = stack.mallocPointer(1)
            val header = EXRHeader.calloc(stack)
            TinyEXR.InitEXRHeader(header)
            val image = EXRImage.calloc(stack)
            TinyEXR.InitEXRImage(image)
            try {
                if (TinyEXR.ParseEXRHeaderFromFile(header, version, path, error) != TinyEXR.TINYEXR_SUCCESS) {
                    throw IllegalStateException("EXR header error: ${message(error)}")
                }
                if (header.tiled() && header.tile_level_mode() == TinyEXR.TINYEXR_TILE_RIPMAP_LEVELS) {
                    throw IllegalStateException("ripmapped EXR files are not supported")
                }
                // ask for floats whatever the file stores, so every channel is read the same way
                for (c in 0 until header.num_channels()) {
                    header.requested_pixel_types().put(c, TinyEXR.TINYEXR_PIXELTYPE_FLOAT)
                }
                val channels = colourChannels(header)
                if (TinyEXR.LoadEXRImageFromFile(image, header, path, error) != TinyEXR.TINYEXR_SUCCESS) {
                    throw IllegalStateException("EXR image error: ${message(error)}")
                }
                return convert(image, channels, maxWidth)
            } finally {
                TinyEXR.FreeEXRImage(image)
                TinyEXR.FreeEXRHeader(header)
            }
        }
    }

    /**
     * The indices of the red, green and blue channels; a lone `Y` (or any single channel) is used for all three. EXR
     * stores channels alphabetically (A, B, G, R), so they are found by name, never by position.
     */
    private fun colourChannels(header: EXRHeader): IntArray {
        val infos = header.channels()
        val names = (0 until header.num_channels()).map { infos.get(it).nameString() }

        // channels in a layer are named "layer.R"; compare the last part
        fun find(wanted: String) = names.indexOfFirst { it.substringAfterLast('.').equals(wanted, true) }
        val r = find("R")
        val g = find("G")
        val b = find("B")
        val channels = if (r >= 0 && g >= 0 && b >= 0) {
            intArrayOf(r, g, b)
        } else {
            val gray = find("Y").takeIf { it >= 0 } ?: if (names.size == 1) 0 else -1
            if (gray < 0) throw IllegalStateException("EXR has no R, G and B channels (found ${names.joinToString()})")
            intArrayOf(gray, gray, gray)
        }
        for (c in channels) {
            if (infos.get(c).x_sampling() != 1 || infos.get(c).y_sampling() != 1) {
                throw IllegalStateException("subsampled channel '${names[c]}' is not supported")
            }
        }
        return channels
    }

    /**
     * Averages the pixels of [image] into blocks of the reduction factor, reading the scanline planes or the tiles
     * tinyexr produced straight into the small output, so the full-size image is never copied.
     */
    private fun convert(image: EXRImage, channels: IntArray, maxWidth: Int): HdrImage {
        val width = image.width()
        val height = image.height()
        if (height < 1 || width.toLong() != height * 2L) {
            throw IllegalStateException("$width x $height is not a 2:1 equirectangular image")
        }
        if (height > MAX_HDR_HEIGHT) {
            throw IllegalStateException("image too large: $width x $height, at most ${MAX_HDR_HEIGHT * 2} x $MAX_HDR_HEIGHT")
        }
        var factor = 1
        while (width / factor > maxWidth && height / factor > 1) factor *= 2
        val outW = width / factor
        val outH = height / factor
        val sum = FloatArray(outW * outH * 3)

        // adds the w x h float plane of channel slot [slot], whose top left pixel is at (x0, y0) of the image
        fun accumulate(plane: FloatBuffer, slot: Int, x0: Int, y0: Int, w: Int, h: Int) {
            for (y in 0 until h) {
                val oy = (y0 + y) / factor
                if (oy >= outH) break
                for (x in 0 until w) {
                    val ox = (x0 + x) / factor
                    if (ox >= outW) break
                    // NaN and negative radiance are noise; infinity saturates at the largest half float
                    val v = plane.get(y * w + x)
                    if (v > 0f) sum[(oy * outW + ox) * 3 + slot] += minOf(v, MAX_HALF)
                }
            }
        }

        if (image.num_tiles() > 0) {
            val tiles = image.tiles() ?: throw IllegalStateException("EXR has no tile data")
            for (i in 0 until image.num_tiles()) {
                tiles.position(i)
                // a mipmapped file lists every level; level 0 is the full-size image
                if (tiles.level_x() != 0 || tiles.level_y() != 0) continue
                val planes = tiles.images(image.num_channels())
                val w = tiles.width()
                val h = tiles.height()
                for (slot in 0 until 3) {
                    accumulate(
                        MemoryUtil.memFloatBuffer(planes.get(channels[slot]), w * h),
                        slot,
                        tiles.offset_x(),
                        tiles.offset_y(),
                        w,
                        h
                    )
                }
            }
        } else {
            val planes = image.images() ?: throw IllegalStateException("EXR has no image data")
            for (slot in 0 until 3) {
                accumulate(
                    MemoryUtil.memFloatBuffer(planes.get(channels[slot]), width * height),
                    slot,
                    0,
                    0,
                    width,
                    height
                )
            }
        }

        val weight = 1f / (factor * factor)
        val out = ShortArray(sum.size) { java.lang.Float.floatToFloat16(minOf(sum[it] * weight, MAX_HALF)) }
        return HdrImage(outW, outH, out)
    }

    private fun message(error: org.lwjgl.PointerBuffer): String {
        val ptr = error.get(0)
        if (ptr == 0L) return "unknown error"
        return try {
            MemoryUtil.memUTF8(ptr)
        } finally {
            TinyEXR.nFreeEXRErrorMessage(ptr)
        }
    }
}
