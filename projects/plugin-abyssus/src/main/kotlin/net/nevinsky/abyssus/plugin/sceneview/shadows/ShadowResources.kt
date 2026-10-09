/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation

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
        val gl = Gdx.gl
        val state = ShadowGlState()
        return try {
            runCatchingKeepingCancellation {
                fbo.bind()
                gl.glViewport(tile.x, tile.y, tile.size, tile.size)
                gl.glEnable(GL20.GL_SCISSOR_TEST); gl.glScissor(tile.x, tile.y, tile.size, tile.size)
                gl.glEnable(GL20.GL_DEPTH_TEST); gl.glDepthMask(true); gl.glDepthFunc(GL20.GL_LEQUAL)
                gl.glColorMask(true, true, true, true)
                gl.glDisable(GL20.GL_BLEND)
                // Packed depth is numerical data: color dithering/conversion must not alter its digits.
                gl.glDisable(GL20.GL_DITHER)
                gl.glDisable(0x8DB9) // GL_FRAMEBUFFER_SRGB (the canvas uses GL 3.2 core)
                gl.glClearColor(1f, 1f, 1f, 1f); gl.glClearDepthf(1f)
                gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                block()
                true
            }.getOrDefault(false)
        } finally { state.restore() }
    }

    /** Drop references after context loss; removing the managed-framebuffer entry makes no GL calls. */
    fun abandon() {
        com.badlogic.gdx.graphics.glutils.GLFrameBuffer.clearAllFrameBuffers(owner)
        framebuffer = null
    }

    fun attribute(records: List<ShadowLightRecord>): ShadowAtlasAttribute? = atlas?.let { ShadowAtlasAttribute(it, records) }

    override fun dispose() { framebuffer?.dispose(); framebuffer = null }

}

/** Snapshot of state a depth pass can change, including a non-default caller framebuffer. */
private class ShadowGlState {
    private val gl = Gdx.gl
    private fun ints(name: Int, count: Int = 1): IntArray {
        val buffer = BufferUtils.newIntBuffer(count); gl.glGetIntegerv(name, buffer)
        return IntArray(count) { buffer.get(it) }
    }
    private fun floats(name: Int, count: Int): FloatArray {
        val buffer = BufferUtils.newFloatBuffer(count); gl.glGetFloatv(name, buffer)
        return FloatArray(count) { buffer.get(it) }
    }
    private val framebuffer = ints(0x8CA6)[0]
    private val renderbuffer = ints(0x8CA7)[0]
    private val viewport = ints(GL20.GL_VIEWPORT, 4)
    private val scissor = ints(GL20.GL_SCISSOR_BOX, 4)
    private val enabled = listOf(GL20.GL_SCISSOR_TEST, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_BLEND,
        GL20.GL_DITHER, 0x8DB9).associateWith(gl::glIsEnabled)
    private val depthFunc = ints(GL20.GL_DEPTH_FUNC)[0]
    private val cullFace = ints(GL20.GL_CULL_FACE_MODE)[0]
    private val activeTexture = ints(GL20.GL_ACTIVE_TEXTURE)[0]
    private val blendSrc = ints(0x80C9)[0]; private val blendDst = ints(0x80C8)[0]
    private val blendSrcAlpha = ints(0x80CB)[0]; private val blendDstAlpha = ints(0x80CA)[0]
    private val clearColor = floats(GL20.GL_COLOR_CLEAR_VALUE, 4)
    private val depthRange = floats(GL20.GL_DEPTH_RANGE, 2)
    private val clearDepth = floats(GL20.GL_DEPTH_CLEAR_VALUE, 1)[0]
    private val colorMask = BufferUtils.newByteBuffer(4).also { gl.glGetBooleanv(GL20.GL_COLOR_WRITEMASK, it) }
    private val depthMask = BufferUtils.newByteBuffer(1).also { gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, it) }

    fun restore() {
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer); gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, renderbuffer)
        gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        gl.glScissor(scissor[0], scissor[1], scissor[2], scissor[3])
        enabled.forEach { (flag, wasEnabled) -> if (wasEnabled) gl.glEnable(flag) else gl.glDisable(flag) }
        gl.glDepthFunc(depthFunc); gl.glCullFace(cullFace)
        gl.glBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha)
        gl.glColorMask(colorMask.get(0).toInt() != 0, colorMask.get(1).toInt() != 0, colorMask.get(2).toInt() != 0, colorMask.get(3).toInt() != 0)
        gl.glDepthMask(depthMask.get(0).toInt() != 0)
        gl.glDepthRangef(depthRange[0], depthRange[1]); gl.glClearDepthf(clearDepth)
        gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
        gl.glActiveTexture(activeTexture)
    }
}
