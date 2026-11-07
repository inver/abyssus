/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.post

import com.badlogic.gdx.utils.Disposable
import org.lwjgl.opengl.GL32C.*

/**
 * A host frame in linear HDR (OpenSpec `control-line-post-processing`, "Frames are finished by one post pass"): a
 * multisampled framebuffer with an RGBA16F colour and a 24-bit depth (with stencil) renderbuffer that the lit surfaces
 * are drawn into, resolved by [resolve] into a single-sampled RGBA16F [colorTexture] and a matching [depthTexture] that
 * the post passes read. The colour is cleared to transparent black by the host, so its alpha is the coverage of the
 * surfaces drawn (a host's display-referred sky stays outside it).
 *
 * The target owns and disposes what it creates. GL thread only (the game's render thread), on a context of OpenGL 3.2
 * or newer; [ensure] reports a context that cannot make it.
 */
class HdrFrameTarget(private val wantedSamples: Int = 4) : Disposable {
    var width = 0
        private set

    var height = 0
        private set

    /** The samples the multisampled buffers have (at most the wanted count and the context's maximum). */
    var samples = 0
        private set

    private var drawFramebuffer = 0
    private var colorBuffer = 0
    private var depthBuffer = 0
    private var resolveFramebuffer = 0

    /** The resolved linear colour (RGBA16F, alpha the coverage), valid after [resolve]. */
    var colorTexture = 0
        private set

    /** The resolved depth (window depth, `DEPTH24_STENCIL8`), valid after [resolve]. */
    var depthTexture = 0
        private set

    /**
     * Makes the buffers for a [frameWidth] x [frameHeight] frame, or makes them again at a new size. False, with
     * everything this target made released, when the context cannot make a complete framebuffer of them.
     */
    fun ensure(frameWidth: Int, frameHeight: Int): Boolean {
        require(frameWidth > 0 && frameHeight > 0) { "an empty frame: $frameWidth x $frameHeight" }
        if (drawFramebuffer != 0 && frameWidth == width && frameHeight == height) {
            return true
        }
        release()
        val oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val oldRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING)
        val oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        try {
            samples = wantedSamples.coerceIn(0, glGetInteger(GL_MAX_SAMPLES))
            colorBuffer = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, colorBuffer)
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA16F, frameWidth, frameHeight)
            depthBuffer = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer)
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, frameWidth, frameHeight)
            drawFramebuffer = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, drawFramebuffer)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBuffer)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBuffer)
            val drawn = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE

            colorTexture = texture(GL_RGBA16F, frameWidth, frameHeight, GL_RGBA, GL_HALF_FLOAT, GL_LINEAR)
            depthTexture =
                texture(GL_DEPTH24_STENCIL8, frameWidth, frameHeight, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, GL_NEAREST)
            resolveFramebuffer = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0)
            val resolved = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE
            if (!drawn || !resolved || glGetError() != GL_NO_ERROR) {
                release()
                return false
            }
            width = frameWidth
            height = frameHeight
            return true
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer)
            glBindRenderbuffer(GL_RENDERBUFFER, oldRenderbuffer)
            glBindTexture(GL_TEXTURE_2D, oldTexture)
        }
    }

    private fun texture(internal: Int, w: Int, h: Int, format: Int, type: Int, filter: Int): Int {
        val handle = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, handle)
        glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        return handle
    }

    /** Binds the multisampled buffers as the framebuffer and sets the viewport to the whole frame. */
    fun bind() {
        check(drawFramebuffer != 0) { "ensure the target first" }
        glBindFramebuffer(GL_FRAMEBUFFER, drawFramebuffer)
        glViewport(0, 0, width, height)
    }

    /**
     * Resolves the multisampled colour and depth into [colorTexture] and [depthTexture], then binds [framebuffer] (the
     * host's, which the post pass draws into).
     */
    fun resolve(framebuffer: Int) {
        val scissor = glIsEnabled(GL_SCISSOR_TEST)
        glDisable(GL_SCISSOR_TEST)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, drawFramebuffer)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFramebuffer)
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST)
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        if (scissor) {
            glEnable(GL_SCISSOR_TEST)
        }
    }

    private fun release() {
        // deleting the name 0 is ignored by GL
        glDeleteFramebuffers(drawFramebuffer)
        glDeleteFramebuffers(resolveFramebuffer)
        glDeleteRenderbuffers(colorBuffer)
        glDeleteRenderbuffers(depthBuffer)
        glDeleteTextures(colorTexture)
        glDeleteTextures(depthTexture)
        drawFramebuffer = 0
        resolveFramebuffer = 0
        colorBuffer = 0
        depthBuffer = 0
        colorTexture = 0
        depthTexture = 0
        width = 0
        height = 0
    }

    override fun dispose() = release()
}
