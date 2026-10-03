/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.loader

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import kotlin.math.max

/**
 * Image decoding for previews. Not part of Mundus: the scene view shows many large textures at once and uploading a
 * 4096x4096 one stalls the render loop, so bigger images are halved until they fit [.MAX_SIZE].
 */
object Pixmaps {
    const val MAX_SIZE: Int = 2048

    /** Decodes `file`; needs no OpenGL context. The caller owns the result.  */
    @JvmStatic
    fun load(file: FileHandle): Pixmap {
        val full = Pixmap(file)
        if (full.getWidth() <= MAX_SIZE && full.getHeight() <= MAX_SIZE) {
            return full
        }
        try {
            var shift = 0
            while ((full.getWidth() shr shift) > MAX_SIZE || (full.getHeight() shr shift) > MAX_SIZE) {
                shift++
            }
            val small = Pixmap(max(1, full.getWidth() shr shift), max(1, full.getHeight() shr shift), full.getFormat())
            small.setFilter(Pixmap.Filter.BiLinear)
            small.drawPixmap(full, 0, 0, full.getWidth(), full.getHeight(), 0, 0, small.getWidth(), small.getHeight())
            return small
        } finally {
            full.dispose()
        }
    }
}
