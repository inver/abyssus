/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture

/** Uploads a solid pixel in the current GL context, releasing its CPU memory even when upload fails. */
fun newSolidTexture(color: Color = Color.WHITE): Texture = withSolidPixmap(color) { Texture(it, false) }

/** Six identical solid faces; the temporary pixel is borrowed by each synchronous face upload. */
fun newSolidCubemap(color: Color = Color.BLACK): Cubemap = withSolidPixmap(color) {
    Cubemap(it, it, it, it, it, it)
}

private inline fun <T> withSolidPixmap(color: Color, upload: (Pixmap) -> T): T {
    val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
    return try {
        pixmap.setColor(color)
        pixmap.fill()
        upload(pixmap)
    } finally {
        pixmap.dispose()
    }
}
