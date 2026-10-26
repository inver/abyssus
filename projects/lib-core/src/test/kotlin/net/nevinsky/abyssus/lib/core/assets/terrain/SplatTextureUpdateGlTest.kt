/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.badlogic.gdx.utils.BufferUtils

/** Opt-in (`-Dabyssus.glTests=true`): a region update leaves the texture equal to a freshly uploaded one. */
class SplatTextureUpdateGlTest {
    private fun upload(pixels: ByteArray, side: Int): Texture {
        val pixmap = Pixmap(side, side, Pixmap.Format.RGBA8888)
        pixmap.pixels.put(pixels).flip()
        return Texture(pixmap).also { pixmap.dispose() }
    }

    /** The texture's texels as drawn into a framebuffer and read back. */
    private fun readBack(texture: Texture, side: Int): ByteArray {
        val frame = FrameBuffer(Pixmap.Format.RGBA8888, side, side, false)
        try {
            frame.begin()
            val batch = com.badlogic.gdx.graphics.g2d.SpriteBatch()
            batch.projectionMatrix.setToOrtho2D(0f, 0f, side.toFloat(), side.toFloat())
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            batch.disableBlending()
            batch.begin()
            batch.draw(texture, 0f, 0f, side.toFloat(), side.toFloat(), 0, 0, side, side, false, true)
            batch.end()
            val out = BufferUtils.newByteBuffer(side * side * 4)
            Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1)
            Gdx.gl.glReadPixels(0, 0, side, side, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, out)
            frame.end()
            batch.dispose()
            return ByteArray(side * side * 4).also { out.get(it) }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun `updating a rectangle equals a fresh upload`() {
        assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)
        TestGl.run {
            val side = 64
            val before = ByteArray(side * side * 4) { (it * 31 % 251).toByte() }
            val after = before.copyOf()
            for (y in 10..17) for (x in 5..22) for (c in 0 until 4) after[(y * side + x) * 4 + c] = (200 + c).toByte()
            val texture = upload(before, side)
            val fresh = upload(after, side)
            try {
                SplatTexture().updateRegion(texture, after, 5, 10, 18, 8)
                assertArrayEquals(readBack(fresh, side), readBack(texture, side))
            } finally {
                texture.dispose()
                fresh.dispose()
            }
        }
    }
}
