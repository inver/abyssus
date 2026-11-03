/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import java.nio.ByteOrder
import java.nio.ByteBuffer

private const val RGBA = 4

/**
 * Copies the rectangle [x],[y] of [width] by [height] texels out of [pixels], a square map of [side] texels of RGBA bytes
 * (row after row), into [out] as one contiguous block of rows, which is what `glTexSubImage2D` reads (GLES2 has no
 * row-length unpack). [out] is cleared first and left ready to read from position 0 to the end of the block.
 */
fun packSplatRows(pixels: ByteArray, side: Int, x: Int, y: Int, width: Int, height: Int, out: ByteBuffer) {
    require(side > 0 && pixels.size == side * side * RGBA) { "a $side by $side map holds ${side * side * RGBA} bytes" }
    require(width > 0 && height > 0 && x >= 0 && y >= 0 && x + width <= side && y + height <= side) {
        "rectangle $x,$y ${width}x$height is outside a $side by $side map"
    }
    require(out.capacity() >= width * height * RGBA) { "the buffer holds ${out.capacity()} bytes" }
    out.clear()
    for (row in y until y + height) out.put(pixels, (row * side + x) * RGBA, width * RGBA)
    out.flip()
}

/**
 * Updates part of a built splat map [Texture] in place from the pixels of a draft, so a live stroke re-sends only the
 * texels it changed. Owns one direct buffer that grows by doubling and is reused from call to call. Needs a GL context.
 */
class SplatTexture {
    private var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)

    /** Re-sends the rectangle [x],[y] of [width] by [height] texels of [pixels] (a square map of RGBA bytes) into [texture]. */
    fun updateRegion(texture: Texture, pixels: ByteArray, x: Int, y: Int, width: Int, height: Int) {
        val side = texture.width
        require(texture.height == side) { "the splat texture is ${texture.width} by ${texture.height}" }
        val needed = width * height * RGBA
        if (buffer.capacity() < needed) buffer = ByteBuffer.allocateDirect(maxOf(needed, buffer.capacity() * 2)).order(ByteOrder.nativeOrder())
        packSplatRows(pixels, side, x, y, width, height, buffer)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        texture.bind()
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1)
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, x, y, width, height, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
    }
}
