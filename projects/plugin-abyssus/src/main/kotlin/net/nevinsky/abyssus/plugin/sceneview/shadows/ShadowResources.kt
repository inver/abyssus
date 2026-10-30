/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import net.nevinsky.abyssus.lib.render.shadow.GlRenderTarget
import net.nevinsky.abyssus.lib.render.shadow.ShadowGlState
import com.badlogic.gdx.graphics.Color

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/** GL resources belong to one active canvas context. Construct, render, and dispose only on its safe GL thread. */
class ShadowResources(private val size: Int = ShadowLayout.ATLAS_SIZE) : Disposable {
    private var framebuffer: FrameBuffer?
    private val owner = Gdx.app
    val available: Boolean get() = framebuffer != null
    val atlas get() = framebuffer?.colorBufferTexture

    init {
        val maxTexture = BufferUtils.newIntBuffer(1)
        val maxSamplers = BufferUtils.newIntBuffer(1)
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, maxTexture)
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, maxSamplers)
        val state = ShadowGlState()
        framebuffer = try {
            if (size in 1..maxTexture.get(0) && maxSamplers.get(0) >= 12) runCatchingKeepingCancellation {
                FrameBuffer(Pixmap.Format.RGBA8888, size, size, true).also {
                    it.colorBufferTexture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest, com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest)
                    it.colorBufferTexture.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge, com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge)
                }
            }.getOrNull() else null
        } finally { state.restore() }
    }

    fun render(tile: ShadowTile, block: () -> Unit): Boolean {
        val fbo = framebuffer ?: return false
        require(tile.x >= 0 && tile.y >= 0 && tile.x + tile.size <= size && tile.y + tile.size <= size)
        return object : GlRenderTarget(fbo) {
            override fun viewport() {
                Gdx.gl.glViewport(tile.x, tile.y, tile.size, tile.size)
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
                Gdx.gl.glScissor(tile.x, tile.y, tile.size, tile.size)
            }
        }.render(Color.WHITE, block).isSuccess
    }

    /** Drop references after context loss; removing the managed-framebuffer entry makes no GL calls. */
    fun abandon() {
        com.badlogic.gdx.graphics.glutils.GLFrameBuffer.clearAllFrameBuffers(owner)
        framebuffer = null
    }

    fun attribute(records: List<ShadowLightRecord>): ShadowAtlasAttribute? = atlas?.let { ShadowAtlasAttribute(it, records) }

    override fun dispose() { framebuffer?.dispose(); framebuffer = null }

}
