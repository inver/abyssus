/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

/** The smallest and the largest side of a splat map: 2048 is what the loader keeps, a larger image would be halved. */
const val SPLAT_MIN_SIDE: Int = 64
const val SPLAT_MAX_SIDE: Int = 2048

/** Why a splat image cannot be painted. */
enum class SplatImageProblem { NOT_PNG, UNREADABLE, NOT_RGBA8, NOT_SQUARE, TOO_SMALL, TOO_LARGE }

/** A splat image that cannot be painted, with what the reader found ([detail]: the sides, say). */
class SplatImageException(val problem: SplatImageProblem, val detail: String = "") :
    Exception("${problem.name}${if (detail.isEmpty()) "" else ": $detail"}")

/** The pixels of a splat map: [side] by [side] texels of non-premultiplied RGBA, row after row. */
class SplatPixels(val side: Int, val rgba: ByteArray) {
    init {
        require(rgba.size == side * side * 4) { "a $side by $side image holds ${side * side * 4} bytes" }
    }
}

/**
 * Reads and writes the splat map as an 8-bit RGBA PNG whose colour values are the weights, never premultiplied by the
 * alpha (the A weight). Strict on purpose: an image of another kind is reported rather than converted, since a
 * conversion would change what the shader reads. Pure CPU work, safe on any thread. The implementation (ImageIO) lives
 * with the host, because this module has no AWT.
 */
interface SplatImageCodec {
    /** Decodes [bytes] of the file [fileName]; throws [SplatImageException] with the reason when it cannot be painted. */
    fun decode(fileName: String, bytes: ByteArray): SplatPixels

    /** Encodes [pixels] as an 8-bit RGBA PNG; the colour values are written as they are. */
    fun encode(pixels: SplatPixels): ByteArray
}
