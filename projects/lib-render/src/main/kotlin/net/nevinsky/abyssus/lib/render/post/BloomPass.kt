/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.post

import com.badlogic.gdx.utils.Disposable
import org.lwjgl.opengl.GL32C.*

/** How many levels the bloom chain has at most: from half the frame down to a 32nd of it. */
const val BLOOM_LEVELS = 5

/**
 * The sizes of the bloom chain of a [width] x [height] frame: half the frame, then halving (rounded up) for at most
 * [levels] levels, stopping before a level would be smaller than 2 texels either way. No GL.
 */
fun bloomPlan(width: Int, height: Int, levels: Int = BLOOM_LEVELS): List<Pair<Int, Int>> {
    require(width > 0 && height > 0) { "an empty frame: $width x $height" }
    val out = ArrayList<Pair<Int, Int>>()
    var w = (width + 1) / 2
    var h = (height + 1) / 2
    while (out.size < levels && w >= 2 && h >= 2) {
        out += w to h
        w = (w + 1) / 2
        h = (h + 1) / 2
    }
    return out
}

/**
 * Bloom without a threshold (OpenSpec `control-line-post-processing`, "Bright emissive blooms"): the frame's light is
 * filtered down a chain of half-size levels ([bloomPlan]; 13-tap filter, Karis average on the first level) and summed
 * back up with a tent filter, and the sum is divided by the number of levels, so the result holds the light of its
 * source spread out: only light far above the rest (a lamp, the sun's glint) shows once mixed in at a small strength.
 *
 * [render] returns the bloom texture (half the frame's size, RGBA16F), which this pass owns, as it owns its levels and
 * programs. It restores the framebuffer, viewport, program and texture bindings it changes. GL thread only, OpenGL 3.2.
 */
class BloomPass : Disposable {
    private var down = IntArray(0)
    private var up = IntArray(0)
    private var downFramebuffers = IntArray(0)
    private var upFramebuffers = IntArray(0)
    private var sizes = emptyList<Pair<Int, Int>>()
    private var downProgram = 0
    private var upProgram = 0
    private var vao = 0

    /** The levels of the last [render], largest first; for tests. */
    val levels: List<Pair<Int, Int>>
        get() = sizes

    /**
     * Blooms [source] (a linear HDR texture of [width] x [height]) and returns the bloom texture, or 0 when the frame
     * is too small for a single level.
     */
    fun render(source: Int, width: Int, height: Int): Int {
        val plan = bloomPlan(width, height)
        if (plan.isEmpty()) {
            return 0
        }
        val oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val oldProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val oldActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val oldTextures = IntArray(2) { unit ->
            glActiveTexture(GL_TEXTURE0 + unit)
            glGetInteger(GL_TEXTURE_BINDING_2D)
        }
        val viewport = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it) }
        val flags = intArrayOf(GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST)
        val enabled = flags.map { glIsEnabled(it) }
        try {
            if (downProgram == 0) {
                create()
            }
            if (plan != sizes) {
                // the new levels are bound on unit 0, which is restored below
                glActiveTexture(GL_TEXTURE0)
                allocate(plan)
            }
            flags.forEach { glDisable(it) }
            glBindVertexArray(vao)

            glUseProgram(downProgram)
            glUniform1i(glGetUniformLocation(downProgram, "u_source"), 0)
            var sourceTexture = source
            var sourceWidth = width
            var sourceHeight = height
            for (i in sizes.indices) {
                val (w, h) = sizes[i]
                glBindFramebuffer(GL_FRAMEBUFFER, downFramebuffers[i])
                glViewport(0, 0, w, h)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, sourceTexture)
                glUniform2f(glGetUniformLocation(downProgram, "u_texel"), 1f / sourceWidth, 1f / sourceHeight)
                glUniform1i(glGetUniformLocation(downProgram, "u_karis"), if (i == 0) 1 else 0)
                glDrawArrays(GL_TRIANGLES, 0, 3)
                sourceTexture = down[i]
                sourceWidth = w
                sourceHeight = h
            }

            glUseProgram(upProgram)
            glUniform1i(glGetUniformLocation(upProgram, "u_down"), 0)
            glUniform1i(glGetUniformLocation(upProgram, "u_up"), 1)
            val last = sizes.size - 1
            // the smallest level is its own sum; each larger one adds its own light to the sum of those below
            var below = down[last]
            var belowSize = sizes[last]
            for (i in last - 1 downTo 0) {
                val (w, h) = sizes[i]
                glBindFramebuffer(GL_FRAMEBUFFER, upFramebuffers[i])
                glViewport(0, 0, w, h)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, down[i])
                glActiveTexture(GL_TEXTURE1)
                glBindTexture(GL_TEXTURE_2D, below)
                glUniform2f(
                    glGetUniformLocation(upProgram, "u_upTexel"),
                    1f / belowSize.first,
                    1f / belowSize.second,
                )
                glUniform1f(glGetUniformLocation(upProgram, "u_scale"), if (i == 0) 1f / sizes.size else 1f)
                glDrawArrays(GL_TRIANGLES, 0, 3)
                below = up[i]
                belowSize = sizes[i]
            }
            // a single level: the bloom is that level
            return if (last == 0) down[0] else up[0]
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer)
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            glUseProgram(oldProgram)
            glBindVertexArray(oldVao)
            oldTextures.forEachIndexed { unit, texture ->
                glActiveTexture(GL_TEXTURE0 + unit)
                glBindTexture(GL_TEXTURE_2D, texture)
            }
            glActiveTexture(oldActive)
            flags.forEachIndexed { index, flag ->
                if (enabled[index]) {
                    glEnable(flag)
                } else {
                    glDisable(flag)
                }
            }
        }
    }

    private fun allocate(plan: List<Pair<Int, Int>>) {
        releaseLevels()
        sizes = plan
        down = IntArray(plan.size) { level(plan[it]) }
        up = IntArray(plan.size) { level(plan[it]) }
        downFramebuffers = IntArray(plan.size) { framebuffer(down[it]) }
        upFramebuffers = IntArray(plan.size) { framebuffer(up[it]) }
    }

    private fun level(size: Pair<Int, Int>): Int {
        val handle = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, handle)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, size.first, size.second, 0, GL_RGBA, GL_HALF_FLOAT, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        return handle
    }

    private fun framebuffer(texture: Int): Int {
        val handle = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, handle)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
        return handle
    }

    private fun create() {
        val vertex = resource("/shader/render/post-fullscreen.vert")
        downProgram = linkProgram(vertex, resource("/shader/render/post-bloom-down.frag"))
        upProgram = linkProgram(vertex, resource("/shader/render/post-bloom-up.frag"))
        vao = glGenVertexArrays()
    }

    private fun resource(path: String) =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing post shader: $path" }
            .bufferedReader()
            .use { it.readText() }

    private fun releaseLevels() {
        downFramebuffers.forEach(::glDeleteFramebuffers)
        upFramebuffers.forEach(::glDeleteFramebuffers)
        down.forEach(::glDeleteTextures)
        up.forEach(::glDeleteTextures)
        down = IntArray(0)
        up = IntArray(0)
        downFramebuffers = IntArray(0)
        upFramebuffers = IntArray(0)
        sizes = emptyList()
    }

    override fun dispose() {
        releaseLevels()
        if (downProgram != 0) {
            glDeleteProgram(downProgram)
            glDeleteProgram(upProgram)
            glDeleteVertexArrays(vao)
        }
        downProgram = 0
        upProgram = 0
        vao = 0
    }
}
