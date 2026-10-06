/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.loader

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import java.io.File
import kotlin.math.max

/**
 * Image decoding for previews. Not part of Mundus: the scene view shows many large textures at once and uploading a
 * 4096x4096 one stalls the render loop, so bigger images are halved until they fit [.MAX_SIZE].
 */
object Pixmaps {
    const val MAX_SIZE: Int = 2048

    @JvmStatic
    fun load(file: File): Pixmap {
        return load(FileHandle(file))
    }

    /** Decodes `file`; needs no OpenGL context. The caller owns the result.  */
    @JvmStatic
    fun load(file: FileHandle): Pixmap {
        val full = Pixmap(file)
        if (full.width <= MAX_SIZE && full.height <= MAX_SIZE) {
            return full
        }
        try {
            var shift = 0
            while ((full.width shr shift) > MAX_SIZE || (full.height shr shift) > MAX_SIZE) {
                shift++
            }
            val small = Pixmap(max(1, full.width shr shift), max(1, full.height shr shift), full.format)
            small.setFilter(Pixmap.Filter.BiLinear)
            small.drawPixmap(full, 0, 0, full.width, full.height, 0, 0, small.width, small.height)
            return small
        } finally {
            full.dispose()
        }
    }
}
