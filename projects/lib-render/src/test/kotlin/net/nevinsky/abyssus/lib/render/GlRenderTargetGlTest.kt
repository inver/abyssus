/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.render.shadow.GlRenderTarget
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class GlRenderTargetGlTest {
    private fun value(name: Int): Int = BufferUtils.newIntBuffer(1).also { Gdx.gl.glGetIntegerv(name, it) }.get(0)

    @Test
    fun aFailedRenderRestoresTheCallerAndTheNextRenderStartsWithDepthDefaults() {
        assumeTrue("GL tests are opt-in", TestGl.enabled)
        TestGl.run {
            val caller = FrameBuffer(Pixmap.Format.RGBA8888, 16, 16, true)
            val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 8, 8, true)
            try {
                caller.bind()
                Gdx.gl.glFrontFace(GL20.GL_CW)
                Gdx.gl.glDepthFunc(GL20.GL_GREATER)
                Gdx.gl.glEnable(GL20.GL_BLEND)
                val binding = value(0x8CA6)
                val target = GlRenderTarget(framebuffer)
                assertTrue(target.render(Color.WHITE) {
                    Gdx.gl.glFrontFace(GL20.GL_CCW)
                    Gdx.gl.glDepthFunc(GL20.GL_NEVER)
                    Gdx.gl.glDepthMask(false)
                    error("failed pass")
                }.isFailure)
                assertEquals(binding, value(0x8CA6))
                assertEquals(GL20.GL_CW, value(0x0B46))
                assertEquals(GL20.GL_GREATER, value(GL20.GL_DEPTH_FUNC))
                assertTrue(Gdx.gl.glIsEnabled(GL20.GL_BLEND))
                assertTrue(target.render(Color.BLACK) {
                    assertEquals(GL20.GL_LEQUAL, value(GL20.GL_DEPTH_FUNC))
                    val mask = BufferUtils.newByteBuffer(1)
                    Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, mask)
                    assertEquals(1, mask.get(0).toInt())
                    assertFalse(Gdx.gl.glIsEnabled(GL20.GL_BLEND))
                    assertFalse(Gdx.gl.glIsEnabled(GL20.GL_DITHER))
                }.isSuccess)
                assertEquals(binding, value(0x8CA6))
                assertEquals(GL20.GL_CW, value(0x0B46))
                assertEquals(GL20.GL_GREATER, value(GL20.GL_DEPTH_FUNC))
            } finally {
                Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
                Gdx.gl.glFrontFace(GL20.GL_CCW)
                framebuffer.dispose()
                caller.dispose()
            }
        }
    }
}
