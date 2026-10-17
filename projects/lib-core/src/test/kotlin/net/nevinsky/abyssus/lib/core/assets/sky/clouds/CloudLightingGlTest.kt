/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.core.util.GeometryUtils.Companion.createFullscreenTriangle
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

private const val SAMPLES = 129
private const val GL_RGBA32F = 0x8814

/**
 * The pure lighting functions of `clouds_light.glsl` evaluated into a float target: transmittance in tau, powder in
 * density and phase in mu, each sampled at [SAMPLES] points of its range. Opt-in: `-Dabyssus.glTests=true`.
 */
class CloudLightingGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val vertex = """
        attribute vec2 a_position;
        void main() { gl_Position = vec4(a_position, 0.0, 1.0); }
    """.trimIndent()

    private val fragment = """
        void main() {
            float f = floor(gl_FragCoord.x) / ${SAMPLES - 1}.0;
            gl_FragColor = vec4(cloudSunTransmittance(f * 12.0), cloudPowder(f), cloudPhase(f * 2.0 - 1.0), 0.0);
        }
    """.trimIndent()

    /** The (transmittance, powder, phase) values at the sample [i] of every function's range. GL thread. */
    private fun samples(): List<FloatArray> {
        val values = ArrayList<FloatArray>()
        TestGl.run {
            val gl = Gdx.gl
            val program = ShaderProgram(vertex, skyShaders().fragment("clouds_common.glsl", "clouds_light.glsl") + "\n" + fragment)
            check(program.isCompiled) { program.log }
            val mesh = createFullscreenTriangle()
            val texture = gl.glGenTexture()
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture)
            gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL_RGBA32F, SAMPLES, 1, 0, GL20.GL_RGBA, GL20.GL_FLOAT, null)
            val fbo = gl.glGenFramebuffer()
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture, 0)
            check(gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER) == GL20.GL_FRAMEBUFFER_COMPLETE)
            gl.glViewport(0, 0, SAMPLES, 1)
            gl.glDisable(GL20.GL_BLEND)
            try {
                program.bind()
                mesh.render(program, GL20.GL_TRIANGLES)
                val pixels = BufferUtils.newFloatBuffer(SAMPLES * 4)
                gl.glReadPixels(0, 0, SAMPLES, 1, GL20.GL_RGBA, GL20.GL_FLOAT, pixels)
                for (i in 0 until SAMPLES) {
                    values.add(floatArrayOf(pixels.get(i * 4), pixels.get(i * 4 + 1), pixels.get(i * 4 + 2)))
                }
            } finally {
                gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
                gl.glDeleteFramebuffer(fbo)
                gl.glDeleteTexture(texture)
                mesh.dispose()
                program.dispose()
            }
        }
        return values
    }

    @Test
    fun transmittanceFallsOffSmoothly() {
        val values = samples().map { it[0] }
        for (i in 1 until values.size) {
            assertTrue("transmittance falls from ${values[i - 1]} to ${values[i]} at tau ${i * 12f / (SAMPLES - 1)}",
                values[i] < values[i - 1])
        }
        assertTrue("T(0) = ${values.first()}", values.first() >= 1.5f)
        assertTrue("T(12) = ${values.last()}", values.last() >= 0.01f)
    }

    @Test
    fun powderRisesWithDensity() {
        val values = samples().map { it[1] }
        for (i in 1 until values.size) {
            assertTrue("powder rises from ${values[i - 1]} to ${values[i]} at density ${i.toFloat() / (SAMPLES - 1)}",
                values[i] > values[i - 1])
        }
        assertTrue("powder(0) = ${values.first()}", values.first() < 0.5f)
        assertTrue("powder(1) = ${values.last()}", values.last() > 0.7f)
    }

    @Test
    fun phasePeaksTowardTheSun() {
        val values = samples().map { it[2] }
        val forward = values.last()
        val sideways = values[(SAMPLES - 1) / 2]
        val backward = values.first()
        assertTrue("phase(1) = $forward, phase(0) = $sideways", forward > 3f * sideways)
        assertTrue("phase(0) = $sideways, phase(-1) = $backward", 3f * sideways > backward)
        assertTrue("phase(-1) = $backward", backward > 0f)
    }
}
