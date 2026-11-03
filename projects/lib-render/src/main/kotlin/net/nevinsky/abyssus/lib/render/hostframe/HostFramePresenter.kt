/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.hostframe

import com.badlogic.gdx.utils.Disposable
import java.nio.FloatBuffer
import org.lwjgl.opengl.GL32C.*
import org.lwjgl.system.MemoryUtil

/**
 * Host-frame upload and GL window-depth presentation: a frame (RGBA floats and GL window depth, produced off-GL by a host such
 * as a ray tracer) goes into two float textures and is drawn as a fullscreen triangle that writes `gl_FragDepth`, so the
 * host's raster overlays depth-test against it. Knows no producer: the host passes sizes and fill callbacks.
 *
 * GL thread only: call [draw] and [dispose] inside the host's GL context (the editor's canvas `GdxRuntime` context, the game's
 * render thread), never from an arbitrary thread.
 */
class HostFramePresenter : Disposable {
    private var program = 0
    private var vao = 0
    private var color = 0
    private var depth = 0
    private var width = 0
    private var height = 0
    private var uploaded: Any? = null

    /**
     * Draws the frame identified by [frame]; it is uploaded only when [frame] is not the object drawn last or the size
     * changed. [copyColor] fills a buffer with `width * height * 4` linear RGBA floats, [copyDepth] with `width * height`
     * GL window depths.
     */
    fun draw(frame: Any, width: Int, height: Int, copyColor: (FloatBuffer) -> Unit, copyDepth: (FloatBuffer) -> Unit) {
        val oldProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val oldActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val oldUnpack = glGetInteger(GL_UNPACK_ALIGNMENT)
        val unpackFields = intArrayOf(GL_UNPACK_ROW_LENGTH, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SWAP_BYTES)
        val oldUnpackFields = unpackFields.map { glGetInteger(it) }
        val oldBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)
        val oldDepthFunction = glGetInteger(GL_DEPTH_FUNC)
        val oldDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        val flags = intArrayOf(GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST)
        val enabled = flags.map { glIsEnabled(it) }
        glActiveTexture(GL_TEXTURE0)
        val oldColor = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(GL_TEXTURE1)
        val oldDepth = glGetInteger(GL_TEXTURE_BINDING_2D)
        try {
            if (program == 0) create()
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            unpackFields.forEach { glPixelStorei(it, 0) }
            if (uploaded !== frame) {
                upload(width, height, copyColor, copyDepth)
                uploaded = frame
            } else {
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, color)
                glActiveTexture(GL_TEXTURE1)
                glBindTexture(GL_TEXTURE_2D, depth)
            }
            glUseProgram(program)
            glUniform1i(glGetUniformLocation(program, "u_color"), 0)
            glUniform1i(glGetUniformLocation(program, "u_depth"), 1)
            glBindVertexArray(vao)
            flags.forEach { glDisable(it) }
            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GL_ALWAYS)
            glDepthMask(true)
            glDrawArrays(GL_TRIANGLES, 0, 3)
        } finally {
            glUseProgram(oldProgram)
            glBindVertexArray(oldVao)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, oldColor)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, oldDepth)
            glActiveTexture(oldActive)
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, oldBuffer)
            glPixelStorei(GL_UNPACK_ALIGNMENT, oldUnpack)
            unpackFields.forEachIndexed { index, field -> glPixelStorei(field, oldUnpackFields[index]) }
            glDepthFunc(oldDepthFunction)
            glDepthMask(oldDepthMask)
            flags.forEachIndexed { index, flag -> if (enabled[index]) glEnable(flag) else glDisable(flag) }
        }
    }

    private fun create() {
        fun shader(type: Int, source: String): Int {
            val handle = glCreateShader(type)
            glShaderSource(handle, source)
            glCompileShader(handle)
            if (glGetShaderi(handle, GL_COMPILE_STATUS) == GL_FALSE) {
                val log = glGetShaderInfoLog(handle)
                glDeleteShader(handle)
                error(log)
            }
            return handle
        }
        val vertex = shader(GL_VERTEX_SHADER, resource("ray-frame.vert"))
        val fragment = try { shader(GL_FRAGMENT_SHADER, resource("ray-frame.frag")) }
        catch (failure: Throwable) { glDeleteShader(vertex); throw failure }
        val linked = glCreateProgram()
        try {
            glAttachShader(linked, vertex)
            glAttachShader(linked, fragment)
            glLinkProgram(linked)
            check(glGetProgrami(linked, GL_LINK_STATUS) != GL_FALSE) { glGetProgramInfoLog(linked) }
        } catch (failure: Throwable) {
            glDeleteProgram(linked)
            throw failure
        } finally {
            glDeleteShader(vertex)
            glDeleteShader(fragment)
        }
        program = linked
        vao = glGenVertexArrays()
        color = glGenTextures()
        depth = glGenTextures()
    }

    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/shader/render/$name")) {
        "Missing ray presentation shader: $name"
    }.bufferedReader().use { it.readText() }

    private fun upload(frameWidth: Int, frameHeight: Int, copyColor: (FloatBuffer) -> Unit, copyDepth: (FloatBuffer) -> Unit) {
        val resized = width != frameWidth || height != frameHeight
        fun texture(unit: Int, handle: Int, format: Int, channels: Int, size: Int, filter: Int, fill: (FloatBuffer) -> Unit) {
            glActiveTexture(unit)
            glBindTexture(GL_TEXTURE_2D, handle)
            val buffer = MemoryUtil.memAllocFloat(size)
            try {
                fill(buffer); buffer.flip()
                if (resized) {
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                    glTexImage2D(GL_TEXTURE_2D, 0, format, frameWidth, frameHeight, 0, channels, GL_FLOAT, buffer)
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frameWidth, frameHeight, channels, GL_FLOAT, buffer)
                }
            } finally { MemoryUtil.memFree(buffer) }
        }
        texture(GL_TEXTURE0, color, GL_RGBA32F, GL_RGBA, frameWidth * frameHeight * 4, GL_LINEAR, copyColor) // a frame below view size is smoothed when stretched
        texture(GL_TEXTURE1, depth, GL_R32F, GL_RED, frameWidth * frameHeight, GL_NEAREST, copyDepth) // depth is data: never blended
        width = frameWidth
        height = frameHeight
    }

    override fun dispose() {
        glDeleteTextures(color)
        glDeleteTextures(depth)
        glDeleteVertexArrays(vao)
        glDeleteProgram(program)
        program = 0
        vao = 0
        color = 0
        depth = 0
        width = 0
        height = 0
        uploaded = null
    }
}
