/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.post

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.lwjgl.opengl.GL32C.*

/** How many points the ambient occlusion tests per pixel. */
const val SSAO_SAMPLES = 12

/** How far around a point the ambient occlusion looks by default (metres). */
const val SSAO_RADIUS = 0.5f

/**
 * The [count] points the ambient occlusion tests, in the unit hemisphere above a surface (z up): spread over it by a
 * fixed sequence (the same every run) and drawn toward the surface, more of them near it. No GL.
 */
fun ssaoKernel(count: Int = SSAO_SAMPLES): List<Vector3> =
    List(count) { i ->
        // a golden-angle spiral over the hemisphere, its points pulled in toward the centre (lerp 0.1..1 by (i / n)^2);
        // none lies nearer the surface's plane than 15% of the way up, where the depth's own error would occlude it
        val z = 0.15f + 0.85f * (1f - (i + 0.5f) / count)
        val r = sqrt(1f - z * z)
        val phi = i * 2.3999632f
        val t = (i + 1f) / count
        val scale = 0.1f + 0.9f * t * t
        Vector3(r * cos(phi), r * sin(phi), z).scl(scale)
    }

/**
 * Screen-space ambient occlusion (OpenSpec `control-line-post-processing`, "Ambient occlusion darkens creases"): from
 * the frame's depth, at half the frame's size, how open each surface is (1 open, 0 enclosed), blurred without bleeding
 * across silhouettes. The final pass multiplies the light by it.
 *
 * [render] returns the openness texture (R channel), which this pass owns, as it owns its targets and programs. It
 * restores the framebuffer, viewport, program and texture bindings it changes. GL thread only, OpenGL 3.2.
 */
class SsaoPass(private val radius: Float = SSAO_RADIUS) : Disposable {
    private var raw = 0
    private var blurred = 0
    private var rawFramebuffer = 0
    private var blurFramebuffer = 0
    private var width = 0
    private var height = 0
    private var program = 0
    private var blurProgram = 0
    private var vao = 0
    private val kernel = ssaoKernel().flatMap { listOf(it.x, it.y, it.z) }.toFloatArray()
    private val inverse = Matrix4()

    /**
     * The openness of the frame whose depth is [depth] (window depth of a [frameWidth] x [frameHeight] frame), drawn
     * with the camera [projection] (view to clip).
     */
    fun render(depth: Int, projection: Matrix4, frameWidth: Int, frameHeight: Int): Int {
        val w = maxOf(1, frameWidth / 2)
        val h = maxOf(1, frameHeight / 2)
        val oldFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING)
        val oldProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val oldActive = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val viewport = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it) }
        val flags = intArrayOf(GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST)
        val enabled = flags.map { glIsEnabled(it) }
        try {
            if (program == 0) {
                create()
            }
            if (w != width || h != height) {
                allocate(w, h)
            }
            flags.forEach { glDisable(it) }
            glBindVertexArray(vao)
            glViewport(0, 0, w, h)

            glBindFramebuffer(GL_FRAMEBUFFER, rawFramebuffer)
            glUseProgram(program)
            glBindTexture(GL_TEXTURE_2D, depth)
            glUniform1i(glGetUniformLocation(program, "u_depth"), 0)
            glUniformMatrix4fv(glGetUniformLocation(program, "u_projection"), false, projection.`val`)
            glUniformMatrix4fv(glGetUniformLocation(program, "u_inverseProjection"), false, inverse.set(projection).inv().`val`)
            glUniform2f(glGetUniformLocation(program, "u_depthTexel"), 1f / frameWidth, 1f / frameHeight)
            glUniform1f(glGetUniformLocation(program, "u_radius"), radius)
            glUniform3fv(glGetUniformLocation(program, "u_kernel"), kernel)
            glDrawArrays(GL_TRIANGLES, 0, 3)

            glBindFramebuffer(GL_FRAMEBUFFER, blurFramebuffer)
            glUseProgram(blurProgram)
            glBindTexture(GL_TEXTURE_2D, raw)
            glUniform1i(glGetUniformLocation(blurProgram, "u_ao"), 0)
            glUniform2f(glGetUniformLocation(blurProgram, "u_texel"), 1f / w, 1f / h)
            glDrawArrays(GL_TRIANGLES, 0, 3)
            return blurred
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, oldFramebuffer)
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            glUseProgram(oldProgram)
            glBindVertexArray(oldVao)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, oldTexture)
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

    private fun allocate(w: Int, h: Int) {
        release()
        raw = target(GL_RG16F, GL_RG, w, h)
        blurred = target(GL_R8, GL_RED, w, h)
        rawFramebuffer = framebuffer(raw)
        blurFramebuffer = framebuffer(blurred)
        width = w
        height = h
    }

    private fun target(internal: Int, format: Int, w: Int, h: Int): Int {
        val handle = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, handle)
        glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, format, GL_FLOAT, 0L)
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
        program = linkProgram(vertex, resource("/shader/render/post-ssao.frag"))
        blurProgram = linkProgram(vertex, resource("/shader/render/post-ssao-blur.frag"))
        vao = glGenVertexArrays()
    }

    private fun resource(path: String) =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing post shader: $path" }
            .bufferedReader()
            .use { it.readText() }

    private fun release() {
        glDeleteFramebuffers(rawFramebuffer)
        glDeleteFramebuffers(blurFramebuffer)
        glDeleteTextures(raw)
        glDeleteTextures(blurred)
        raw = 0
        blurred = 0
        rawFramebuffer = 0
        blurFramebuffer = 0
        width = 0
        height = 0
    }

    override fun dispose() {
        release()
        if (program != 0) {
            glDeleteProgram(program)
            glDeleteProgram(blurProgram)
            glDeleteVertexArrays(vao)
        }
        program = 0
        blurProgram = 0
        vao = 0
    }
}
