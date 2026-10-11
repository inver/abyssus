/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.io.FileLoader
import java.io.File

/** A density mask that cannot be read: its file holds no `maskResolution²` bytes. [message] names the file. */
class FoliageMaskException(message: String) : RuntimeException(message)

/** The full-density mask value. A mask file that is missing means this value everywhere. */
const val FOLIAGE_MASK_MAX: Int = 255

/** The file holding the density mask of the layer [layerId], inside its foliage asset folder. */
fun foliageMaskFileName(layerId: Int): String = "layer-$layerId.mask"

/**
 * A layer's density mask: `maskResolution²` bytes from 0 through 255, row after row (z-major) over the terrain's
 * square, like `terrain.data`. A missing file is a full mask.
 */
class FoliageMaskFile(private val files: FileLoader) {

    /** The mask of layer [layerId] of asset [assetName]; a missing file means [FOLIAGE_MASK_MAX] everywhere. */
    fun read(assetName: String, layerId: Int, resolution: Int): ByteArray {
        val file = files.findAssetFile(assetName, foliageMaskFileName(layerId))
            ?: return fullMask(resolution)
        val bytes = runCatchingKeepingCancellation { file.readBytes() }
            .getOrElse { throw FoliageMaskException("${file.path}: ${it.message}") }
        requireSize(file, bytes.size, resolution)
        return bytes
    }

    /** Writes [bytes] as the mask of [resolution] to [file]. */
    fun write(file: File, bytes: ByteArray, resolution: Int) {
        requireSize(file, bytes.size, resolution)
        file.writeBytes(bytes)
    }

    private fun requireSize(file: File, size: Int, resolution: Int) {
        val expected = resolution * resolution
        if (size != expected) throw FoliageMaskException("${file.path}: expected $expected bytes, found $size")
    }
}

/** A full-density mask of [resolution] squared. */
fun fullMask(resolution: Int): ByteArray = ByteArray(resolution * resolution) { FOLIAGE_MASK_MAX.toByte() }
