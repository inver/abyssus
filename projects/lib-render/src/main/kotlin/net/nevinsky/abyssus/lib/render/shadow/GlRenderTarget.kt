/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.shadow

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.util.GL_FRAMEBUFFER_SRGB

/** Borrows a host's target for a packed-depth pass. Needs a current GL context; owns no resources. */
open class GlRenderTarget(private val target: FrameBuffer) {
    protected open fun viewport() {
        Gdx.gl.glViewport(0, 0, target.width, target.height)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
    }

    /** Restores caller state after success or failure; cancellation still propagates. */
    fun render(clear: Color, block: () -> Unit): Result<Unit> {
        val gl = Gdx.gl
        val state = ShadowGlState()
        return try {
            runCatchingKeepingCancellation {
                target.bind()
                viewport()
                gl.glEnable(GL20.GL_DEPTH_TEST)
                gl.glDepthMask(true)
                gl.glDepthFunc(GL20.GL_LEQUAL)
                gl.glColorMask(true, true, true, true)
                gl.glDisable(GL20.GL_BLEND)
                // Packed depth is numerical data; color conversion must not alter its digits.
                gl.glDisable(GL20.GL_DITHER)
                gl.glDisable(GL_FRAMEBUFFER_SRGB)
                gl.glClearColor(clear.r, clear.g, clear.b, clear.a)
                gl.glClearDepthf(1f)
                gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                block()
            }
        } finally {
            state.restore()
        }
    }
}
