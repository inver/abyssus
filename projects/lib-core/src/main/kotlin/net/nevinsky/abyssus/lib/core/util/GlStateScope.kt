/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.util

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.BufferUtils

/** The caps an offscreen pass turns off: the ones that would clip or hide what it renders. */
val OFFSCREEN_CAPS: List<Int> = listOf(GL20.GL_BLEND, GL20.GL_DEPTH_TEST, GL20.GL_CULL_FACE, GL20.GL_SCISSOR_TEST)

/**
 * A snapshot of the framebuffer binding, the viewport and whether each of [caps] is enabled (Memento): [restore] puts
 * them back. Passes that run inside a host's shared GL context take one before they change anything, so a pass cannot
 * leak state into the next one. Needs the GL context current.
 */
class GlStateScope(private val caps: List<Int> = emptyList()) {
    private val gl = Gdx.gl
    private val ints = BufferUtils.newIntBuffer(16)
    private val framebuffer = read(GL_FRAMEBUFFER_BINDING)
    private val viewport = IntArray(4).also { gl.glGetIntegerv(GL20.GL_VIEWPORT, ints); for (i in it.indices) it[i] = ints.get(i) }
    private val enabled = caps.map(gl::glIsEnabled)

    private fun read(name: Int): Int {
        ints.clear()
        gl.glGetIntegerv(name, ints)
        return ints.get(0)
    }

    /** Disables every cap of this scope. */
    fun disableCaps() = caps.forEach(gl::glDisable)

    /** Rebinds the framebuffer, resets the viewport and puts each cap back as it was. */
    fun restore() {
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer)
        gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        caps.zip(enabled).forEach { (cap, on) -> if (on) gl.glEnable(cap) else gl.glDisable(cap) }
    }
}

/** Runs [block], then restores the framebuffer, viewport and [caps] as they were before it. */
inline fun <R> withGlState(caps: List<Int> = emptyList(), block: (GlStateScope) -> R): R {
    val scope = GlStateScope(caps)
    try {
        return block(scope)
    } finally {
        scope.restore()
    }
}
