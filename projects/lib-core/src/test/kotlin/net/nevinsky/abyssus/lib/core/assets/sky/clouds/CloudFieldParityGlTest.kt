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
import net.nevinsky.abyssus.lib.core.io.GeometryUtils.Companion.createFullscreenTriangle
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

private const val GRID = 32
private const val GL_RGBA32F = 0x8814

/**
 * `clouds_common.glsl` against [CloudField]: the shader evaluates a grid of points into a float target and every value
 * must match the Kotlin one within 1e-3. Opt-in: `-Dabyssus.glTests=true`.
 */
class CloudFieldParityGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val field = CloudField()

    private val vertex = """
        attribute vec2 a_position;
        void main() { gl_Position = vec4(a_position, 0.0, 1.0); }
    """.trimIndent()

    private val fragment = """
        uniform vec2 u_scale;
        uniform vec2 u_offset;
        uniform int u_seed;
        uniform int u_octaves;
        uniform float u_coverage;
        uniform int u_profile;
        void main() {
            vec2 cell = floor(gl_FragCoord.xy);
            vec2 xz = vec2((cell.x - 16.0) * 1737.3 + 11.1, (cell.y - 16.0) * 1291.7 - 3.3);
            float coverage = cloudCoverage(xz, u_scale, u_offset, u_seed, u_octaves, u_coverage);
            float u = xz.x / u_scale.x + u_offset.x;
            float v = xz.y / u_scale.y + u_offset.y;
            gl_FragColor = vec4(coverage, cloudFbm(u, v, u_seed, u_octaves), cloudNoise(u, v, 3, CLOUD_NOISE_PERIOD),
                cloudShape(u_profile, coverage, cell.y / ${GRID - 1}.0 * 1.2 - 0.1));
        }
    """.trimIndent()

    /** The four values the shader writes at grid cell ([i], [j]) for [band] drifted by [offset]. */
    private fun expected(band: CloudBand, offset: FloatArray, i: Int, j: Int): FloatArray {
        val type = band.type
        val x = (i - 16f) * 1737.3f + 11.1f
        val z = (j - 16f) * 1291.7f - 3.3f
        val coverage = field.coverage(band, x, z, offset)
        val u = x / (type.scale * type.stretch) + offset[0]
        val v = z / type.scale + offset[1]
        return floatArrayOf(
            coverage, field.fbm(u, v, type.ordinal, type.octaves), field.noise(u, v, 3, CLOUD_NOISE_PERIOD),
            field.shape(type, coverage, j / (GRID - 1f) * 1.2f - 0.1f),
        )
    }

    @Test
    fun shaderMatchesTheKotlinField() {
        val bands = CloudType.entries.map { CloudBand(it.level, it, coverage = 0.55f, windX = 7f, windZ = -3f) }
        val results = HashMap<CloudType, FloatArray>()
        TestGl.run {
            val gl = Gdx.gl
            val program = ShaderProgram(vertex, skyShaders().fragment("clouds_common.glsl") + "\n" + fragment)
            check(program.isCompiled) { program.log }
            val mesh = createFullscreenTriangle()
            val texture = gl.glGenTexture()
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture)
            gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL_RGBA32F, GRID, GRID, 0, GL20.GL_RGBA, GL20.GL_FLOAT, null)
            val fbo = gl.glGenFramebuffer()
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture, 0)
            check(gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER) == GL20.GL_FRAMEBUFFER_COMPLETE)
            gl.glViewport(0, 0, GRID, GRID)
            gl.glDisable(GL20.GL_BLEND)
            try {
                for (band in bands) {
                    val type = band.type
                    val offset = field.windOffset(band, 1234.5)
                    program.bind()
                    program.setUniformf("u_scale", type.scale * type.stretch, type.scale)
                    program.setUniformf("u_offset", offset[0], offset[1])
                    program.setUniformi("u_seed", type.ordinal)
                    program.setUniformi("u_octaves", type.octaves)
                    program.setUniformf("u_coverage", band.coverage)
                    program.setUniformi("u_profile", type.profile.ordinal)
                    mesh.render(program, GL20.GL_TRIANGLES)
                    val pixels = BufferUtils.newFloatBuffer(GRID * GRID * 4)
                    gl.glReadPixels(0, 0, GRID, GRID, GL20.GL_RGBA, GL20.GL_FLOAT, pixels)
                    results[type] = FloatArray(GRID * GRID * 4) { pixels.get(it) }
                }
            } finally {
                gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
                gl.glDeleteFramebuffer(fbo)
                gl.glDeleteTexture(texture)
                mesh.dispose()
                program.dispose()
            }
        }
        var worst = 0f
        var where = ""
        for (band in bands) {
            val offset = field.windOffset(band, 1234.5)
            val actual = results.getValue(band.type)
            for (j in 0 until GRID) for (i in 0 until GRID) {
                val want = expected(band, offset, i, j)
                for (c in 0..3) {
                    val error = abs(actual[(j * GRID + i) * 4 + c] - want[c])
                    if (error > worst) {
                        worst = error
                        where = "${band.type} cell ($i, $j) channel $c: ${actual[(j * GRID + i) * 4 + c]} vs ${want[c]}"
                    }
                }
            }
        }
        assertTrue("largest difference $worst at $where", worst <= 1e-3f)
    }
}
