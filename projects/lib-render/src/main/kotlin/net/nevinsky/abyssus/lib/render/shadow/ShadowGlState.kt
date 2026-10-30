/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.shadow

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.util.GL_FRAMEBUFFER_SRGB

/** Snapshot of state a depth pass can change, including a non-default caller framebuffer. */
class ShadowGlState {
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
    private val enabled = listOf(
        GL20.GL_SCISSOR_TEST, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_BLEND,
        GL20.GL_DITHER, GL_FRAMEBUFFER_SRGB
    ).associateWith(gl::glIsEnabled)
    private val depthFunc = ints(GL20.GL_DEPTH_FUNC)[0]
    private val cullFace = ints(GL20.GL_CULL_FACE_MODE)[0]
    private val frontFace = ints(GL20.GL_FRONT_FACE)[0]
    private val activeTexture = ints(GL20.GL_ACTIVE_TEXTURE)[0]
    private val blendSrc = ints(0x80C9)[0];
    private val blendDst = ints(0x80C8)[0]
    private val blendSrcAlpha = ints(0x80CB)[0];
    private val blendDstAlpha = ints(0x80CA)[0]
    private val clearColor = floats(GL20.GL_COLOR_CLEAR_VALUE, 4)
    private val depthRange = floats(GL20.GL_DEPTH_RANGE, 2)
    private val clearDepth = floats(GL20.GL_DEPTH_CLEAR_VALUE, 1)[0]
    private val colorMask = BufferUtils.newByteBuffer(4).also { gl.glGetBooleanv(GL20.GL_COLOR_WRITEMASK, it) }
    private val depthMask = BufferUtils.newByteBuffer(1).also { gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, it) }

    fun restore() {
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer); gl.glBindRenderbuffer(
            GL20.GL_RENDERBUFFER,
            renderbuffer
        )
        gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        gl.glScissor(scissor[0], scissor[1], scissor[2], scissor[3])
        enabled.forEach { (flag, wasEnabled) -> if (wasEnabled) gl.glEnable(flag) else gl.glDisable(flag) }
        gl.glDepthFunc(depthFunc); gl.glCullFace(cullFace); gl.glFrontFace(frontFace)
        gl.glBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha)
        gl.glColorMask(
            colorMask.get(0).toInt() != 0,
            colorMask.get(1).toInt() != 0,
            colorMask.get(2).toInt() != 0,
            colorMask.get(3).toInt() != 0
        )
        gl.glDepthMask(depthMask.get(0).toInt() != 0)
        gl.glDepthRangef(depthRange[0], depthRange[1]); gl.glClearDepthf(clearDepth)
        gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
        gl.glActiveTexture(activeTexture)
    }
}
