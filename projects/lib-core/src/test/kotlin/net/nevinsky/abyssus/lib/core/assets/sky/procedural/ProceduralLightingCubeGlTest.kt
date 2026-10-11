/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudNoiseGenerator
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechnique
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/** The linear lighting cube of a procedural sky, read back. Opt-in: `-Dabyssus.glTests=true`. */
class ProceduralLightingCubeGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val noon = Vector3(0.3f, 0.85f, -0.4f).nor()
    private val params = AtmosphereParams()

    private fun overcast() = Clouds(
        "overcast",
        CloudMeta(CloudTechnique.SHELLS, low = CloudBand(CloudType.STRATUS, CloudLevel.LOW, coverage = 1f)),
        null,
    )

    /** The RGB of the centre texel of [face] at [level] of [texture], linear floats. */
    private fun centre(texture: GpuTexture, face: Int, level: Int, size: Int): FloatArray {
        val gl = Gdx.gl
        val fbo = gl.glGenFramebuffer()
        val previous = BufferUtils.newIntBuffer(16).also { gl.glGetIntegerv(0x8CA6, it) }.get(0)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
        gl.glFramebufferTexture2D(
            GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
            texture.textureObjectHandle, level
        )
        val pixel = BufferUtils.newFloatBuffer(4)
        gl.glReadPixels(size / 2, size / 2, 1, 1, GL20.GL_RGBA, GL20.GL_FLOAT, pixel)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
        gl.glDeleteFramebuffer(fbo)
        return floatArrayOf(pixel.get(0), pixel.get(1), pixel.get(2), pixel.get(3))
    }

    @Test
    fun clearZenithIsLinearBlueAndMatchesTheCpuTwin() {
        var zenith = floatArrayOf()
        inGl {
            val cube = ProceduralLightingCube(params, skyShaders())
            try {
                cube.render(noon, 0.0, null)
                zenith = centre(cube.texture, 2, 0, LIGHTING_CUBE_SIZE)
            } finally {
                cube.dispose()
            }
        }
        val expected = SkyAmbientEstimate(params).radiance(Vector3(0f, 1f, 0f), noon)
        assertTrue("blue sky ${zenith.toList()}", zenith[2] > zenith[1] && zenith[1] > zenith[0])
        for (c in 0..2) {
            // the centre texel looks a hair off the zenith, so allow a few percent over half-float precision
            assertTrue("channel $c: ${zenith[c]} vs ${expected[c]}", abs(zenith[c] - expected[c]) <= 0.04 * expected[c])
        }
        assertEquals(1f, zenith[3], 1e-3f)
    }

    @Test
    fun overcastIsGreyerThanClear() {
        var clear = floatArrayOf()
        var grey = floatArrayOf()
        inGl {
            val cube = ProceduralLightingCube(params, skyShaders())
            val clouds = overcast()
            try {
                cube.render(noon, 0.0, null)
                clear = centre(cube.texture, 2, 0, LIGHTING_CUBE_SIZE)
                cube.render(noon, 0.0, clouds)
                grey = centre(cube.texture, 2, 0, LIGHTING_CUBE_SIZE)
            } finally {
                clouds.dispose()
                cube.dispose()
            }
        }
        val clearBlue = clear[2] / clear[0]
        val greyBlue = grey[2] / grey[0]
        assertTrue("overcast is less blue: $greyBlue vs $clearBlue", greyBlue < clearBlue * 0.8f)
        assertTrue("overcast is lit ${grey.toList()}", grey[0] > 0.01f)
    }

    @Test
    fun theMipChainIsFilled() {
        var base = floatArrayOf()
        var top = floatArrayOf()
        inGl {
            val cube = ProceduralLightingCube(params, skyShaders())
            try {
                cube.render(noon, 0.0, null)
                base = centre(cube.texture, 2, 0, LIGHTING_CUBE_SIZE)
                top = centre(cube.texture, 2, LIGHTING_CUBE_LEVELS - 1, 1)
            } finally {
                cube.dispose()
            }
        }
        assertTrue("1x1 level ${top.toList()} vs ${base.toList()}", top[2] > 0f)
    }

    private fun inGl(block: () -> Unit) = TestGl.run(block)
}
