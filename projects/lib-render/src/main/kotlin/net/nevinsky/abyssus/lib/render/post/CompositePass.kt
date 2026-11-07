/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.render.post

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import org.lwjgl.opengl.GL32C.*

/**
 * A grade applied after the tone map, per channel: `pow(c * gain + lift * (1 - c), 1 / gamma)`. [NEUTRAL] changes
 * nothing.
 */
data class ColorGrade(
    val lift: FloatArray = floatArrayOf(0f, 0f, 0f),
    val gamma: FloatArray = floatArrayOf(1f, 1f, 1f),
    val gain: FloatArray = floatArrayOf(1f, 1f, 1f),
) {
    init {
        require(lift.size == 3 && gamma.size == 3 && gain.size == 3) { "lift, gamma and gain are RGB" }
        require(gamma.all { it > 0f }) { "gamma must be above 0" }
    }

    override fun equals(other: Any?): Boolean =
        other is ColorGrade &&
            lift.contentEquals(other.lift) &&
            gamma.contentEquals(other.gamma) &&
            gain.contentEquals(other.gain)

    override fun hashCode(): Int =
        31 * (31 * lift.contentHashCode() + gamma.contentHashCode()) + gain.contentHashCode()
}

/** The grade that changes nothing. */
val NEUTRAL_GRADE = ColorGrade()

/**
 * What the final pass does with one frame: the [output] transform (exposure, tone map, encode), the [bloom] texture
 * mixed in at [bloomStrength] (0: none), the [ambientOcclusion] texture applied at [aoStrength], a [vignette] (0..1, 0
 * none) and a [grade]. Textures are GL names the caller owns; 0 for none.
 */
class CompositeInputs(
    val output: OutputTransform,
    val bloom: Int = 0,
    val bloomStrength: Float = 0f,
    val ambientOcclusion: Int = 0,
    val aoStrength: Float = 0f,
    val vignette: Float = 0f,
    val grade: ColorGrade = NEUTRAL_GRADE,
)

/**
 * The final pass of a host frame drawn in linear HDR ([HdrFrameTarget]): it applies bloom and ambient occlusion in
 * linear light, then the shared output transform with vignette and grade, exactly once per pixel, and blends the result
 * over what the host's framebuffer already holds (a display-referred sky) by the frame's coverage. With no bloom, no
 * ambient occlusion, no vignette and the neutral grade, a covered pixel is the transform of its light, as a shader
 * ending in the transform writes it.
 *
 * It makes and disposes its program; the textures stay the caller's. It restores the GL state it changes. GL thread
 * only, on an OpenGL 3.2 context.
 */
class CompositePass : Disposable {
    private var program = 0
    private var vao = 0

    /**
     * Draws [hdr] (the resolved linear colour, alpha the coverage) of a [width] x [height] frame with [inputs] into the
     * framebuffer bound now, over the whole viewport [width] x [height].
     */
    fun draw(hdr: Int, width: Int, height: Int, inputs: CompositeInputs) {
        val oldProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val oldActive = glGetInteger(GL_ACTIVE_TEXTURE)
        val oldTextures = IntArray(3) { unit ->
            glActiveTexture(GL_TEXTURE0 + unit)
            glGetInteger(GL_TEXTURE_BINDING_2D)
        }
        val flags = intArrayOf(GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST)
        val enabled = flags.map { glIsEnabled(it) }
        val blend = intArrayOf(
            glGetInteger(GL_BLEND_SRC_RGB),
            glGetInteger(GL_BLEND_DST_RGB),
            glGetInteger(GL_BLEND_SRC_ALPHA),
            glGetInteger(GL_BLEND_DST_ALPHA),
        )
        val depthMask = glGetBoolean(GL_DEPTH_WRITEMASK)
        try {
            if (program == 0) {
                create()
            }
            glUseProgram(program)
            bind(0, hdr, "u_hdr")
            bind(1, inputs.bloom, "u_bloom")
            bind(2, inputs.ambientOcclusion, "u_ao")
            uniform("u_hasBloom") { glUniform1i(it, if (inputs.bloom != 0 && inputs.bloomStrength > 0f) 1 else 0) }
            uniform("u_hasAo") { glUniform1i(it, if (inputs.ambientOcclusion != 0 && inputs.aoStrength > 0f) 1 else 0) }
            uniform("u_bloomStrength") { glUniform1f(it, inputs.bloomStrength) }
            uniform("u_aoStrength") { glUniform1f(it, inputs.aoStrength) }
            uniform("u_vignette") { glUniform1f(it, inputs.vignette.coerceIn(0f, 1f)) }
            uniform("u_exposure") { glUniform1f(it, inputs.output.exposure) }
            uniform("u_lift") { glUniform3f(it, inputs.grade.lift[0], inputs.grade.lift[1], inputs.grade.lift[2]) }
            uniform("u_gamma") { glUniform3f(it, inputs.grade.gamma[0], inputs.grade.gamma[1], inputs.grade.gamma[2]) }
            uniform("u_gain") { glUniform3f(it, inputs.grade.gain[0], inputs.grade.gain[1], inputs.grade.gain[2]) }
            val larger = maxOf(width, height).toFloat()
            uniform("u_aspect") { glUniform2f(it, width / larger, height / larger) }
            glBindVertexArray(vao)
            flags.forEach { glDisable(it) }
            glDepthMask(false)
            glEnable(GL_BLEND)
            glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            glViewport(0, 0, width, height)
            glDrawArrays(GL_TRIANGLES, 0, 3)
        } finally {
            glUseProgram(oldProgram)
            glBindVertexArray(oldVao)
            oldTextures.forEachIndexed { unit, texture ->
                glActiveTexture(GL_TEXTURE0 + unit)
                glBindTexture(GL_TEXTURE_2D, texture)
            }
            glActiveTexture(oldActive)
            glBlendFuncSeparate(blend[0], blend[1], blend[2], blend[3])
            glDepthMask(depthMask)
            flags.forEachIndexed { index, flag ->
                if (enabled[index]) {
                    glEnable(flag)
                } else {
                    glDisable(flag)
                }
            }
        }
    }

    private fun bind(unit: Int, texture: Int, name: String) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, texture)
        uniform(name) { glUniform1i(it, unit) }
    }

    private inline fun uniform(name: String, set: (Int) -> Unit) {
        val location = glGetUniformLocation(program, name)
        if (location >= 0) {
            set(location)
        }
    }

    private fun create() {
        program =
            linkProgram(
                resource("/shader/render/post-fullscreen.vert"),
                // GLSL 1.50 has `texture`; the shared include samples with `texture2D`
                "#version 150\n#define outputTransformFlag\n#define texture2D texture\n" +
                    resource("/shader/shared/output_transform.glsl") +
                    "\n" +
                    resource("/shader/render/post-composite.frag"),
            )
        vao = glGenVertexArrays()
    }

    private fun resource(path: String) =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing post shader: $path" }
            .bufferedReader()
            .use { it.readText() }

    override fun dispose() {
        if (program != 0) {
            glDeleteProgram(program)
            glDeleteVertexArrays(vao)
        }
        program = 0
        vao = 0
    }
}

/** Compiles and links [vertex] and [fragment] GLSL into a program; throws with the GL log when either fails. */
internal fun linkProgram(vertex: String, fragment: String): Int {
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
    val v = shader(GL_VERTEX_SHADER, vertex)
    val f =
        try {
            shader(GL_FRAGMENT_SHADER, fragment)
        } catch (failure: Throwable) {
            glDeleteShader(v)
            throw failure
        }
    val linked = glCreateProgram()
    try {
        glAttachShader(linked, v)
        glAttachShader(linked, f)
        glLinkProgram(linked)
        check(glGetProgrami(linked, GL_LINK_STATUS) != GL_FALSE) { glGetProgramInfoLog(linked) }
    } catch (failure: Throwable) {
        glDeleteProgram(linked)
        throw failure
    } finally {
        glDeleteShader(v)
        glDeleteShader(f)
    }
    return linked
}
