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
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.Clouds
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class ProceduralLightingCubeGlTest {
    @Before
    fun requireGl() = assumeTrue(TestGl.enabled)

    @Test
    fun clearZenithIsBlueAndRadianceScalesWithoutToneMapping() = TestGl.run {
        val normal = ProceduralLightingCube(AtmosphereParams())
        val bright = ProceduralLightingCube(AtmosphereParams(sunIntensity = 200f))
        try {
            val sun = Vector3(0.6f, 0.8f, 0f)
            normal.render(sun, null, 0.0)
            bright.render(sun, null, 0.0)
            val a = centre(normal.texture, 2)
            val b = centre(bright.texture, 2)
            assertEquals(64, normal.texture.width)
            assertTrue("blue zenith ${a.toList()}", a[2] > a[0] && a[2] > a[1])
            for (c in 0..2) assertEquals(a[c] * 10f, b[c], b[c] * 0.005f)
            assertTrue("linear radiance exceeds display range", b.any { it > 1f })
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            normal.dispose(); bright.dispose()
        }
    }

    @Test
    fun overcastIsGreyerThanClearSkyAndWindChangesTheCube() = TestGl.run {
        val cube = ProceduralLightingCube(AtmosphereParams())
        val clouds = Clouds("overcast", CloudMeta(low = CloudMeta.CloudBand(CloudType.STRATUS, coverage = 1f)), null)
        try {
            val sun = Vector3(0.6f, 0.8f, 0f)
            cube.render(sun, null, 0.0)
            val clear = centre(cube.texture, 2)
            cube.render(sun, clouds, 0.0)
            val overcast = centre(cube.texture, 2)
            fun saturation(rgb: FloatArray) = (rgb.max() - rgb.min()) / rgb.max()
            assertTrue(
                "clear ${clear.toList()}, overcast ${overcast.toList()}",
                saturation(overcast) < saturation(clear)
            )
            val fair = Clouds("fair", CloudMeta(low = CloudMeta.CloudBand(CloudType.CUMULUS)), null)
            try {
                cube.render(sun, fair, 0.0)
                val before = face(cube.texture, 2)
                cube.render(sun, fair, 100.0)
                assertFalse(before.contentEquals(face(cube.texture, 2)))
            } finally {
                fair.dispose()
            }
        } finally {
            cube.dispose(); clouds.dispose()
        }
    }

    private fun centre(texture: GpuTexture, face: Int): FloatArray {
        val data = face(texture, face)
        val i = (texture.width / 2 * texture.width + texture.width / 2) * 4
        return FloatArray(3) { data[i + it] }
    }

    private fun face(texture: GpuTexture, face: Int): FloatArray {
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(1)
        gl.glGetIntegerv(0x8CA6, ints)
        val previous = ints.get(0)
        val fbo = gl.glGenFramebuffer()
        try {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
            gl.glFramebufferTexture2D(
                GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0,
                GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, texture.textureObjectHandle, 0
            )
            assertEquals(GL20.GL_FRAMEBUFFER_COMPLETE, gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER))
            val pixels = BufferUtils.newFloatBuffer(texture.width * texture.height * 4)
            gl.glReadPixels(0, 0, texture.width, texture.height, GL20.GL_RGBA, GL20.GL_FLOAT, pixels)
            return FloatArray(pixels.capacity()) { pixels.get(it) }
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous); gl.glDeleteFramebuffer(fbo)
        }
    }
}
