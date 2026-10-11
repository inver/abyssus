/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SunOcclusion
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.GpuTexture
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import net.nevinsky.abyssus.plugin.sceneview.SceneRenderer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The cloud shadow map against the CPU sun occlusion it must agree with. Opt-in (`-Dabyssus.glTests=true`). */
class CloudShadowMapGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val planet = 6360e3f
    private val fair = CloudMeta(
        low = CloudBand(CloudType.CUMULUS),
        mid = CloudBand(CloudType.ALTOCUMULUS),
        high = CloudBand(CloudType.CIRRUS),
    )
    private val sun = Vector3(0.3f, 0.7f, 0.2f).nor()

    private fun shaders() = ShaderStorage().withResources("/shader/scene", SceneRenderer::class.java)

    /** The whole map as floats, row by row from the corner. */
    private fun read(texture: GpuTexture): FloatArray {
        val gl = Gdx.gl
        val ints = BufferUtils.newIntBuffer(1)
        gl.glGetIntegerv(0x8CA6, ints)
        val previous = ints.get(0)
        val fbo = gl.glGenFramebuffer()
        try {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture.textureObjectHandle, 0)
            assertEquals(GL20.GL_FRAMEBUFFER_COMPLETE, gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER))
            val pixels = BufferUtils.newFloatBuffer(texture.width * texture.height * 4)
            gl.glReadPixels(0, 0, texture.width, texture.height, GL20.GL_RGBA, GL20.GL_FLOAT, pixels)
            return FloatArray(texture.width * texture.height) { pixels.get(it * 4) }
        } finally {
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
            gl.glDeleteFramebuffer(fbo)
        }
    }

    private fun run(test: () -> Unit) {
        var ran = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            test()
            ran = true
        }
        assertNull(result.error)
        assertTrue("the frame callback ran", ran)
    }

    @Test
    fun matchesTheCpuSunOcclusionAndTracksWindAndReload() = run {
        val map = CloudShadowMap(shaders())
        try {
            val texel = map.extent / map.size
            val occlusion = SunOcclusion()
            fun worldOf(i: Int, j: Int) = floatArrayOf(-map.extent / 2 + (i + 0.5f) * texel, -map.extent / 2 + (j + 0.5f) * texel)

            assertTrue(map.update(fair, sun, 0f, 0f, planet, 100f, 12.5))
            val first = read(checkNotNull(map.texture))
            var darkest = 1f
            var worst = 0f
            for (j in 0 until map.size step 37) for (i in 0 until map.size step 41) {
                val (x, z) = worldOf(i, j).let { it[0] to it[1] }
                val expected = occlusion.instant(fair, sun, x, z, planet, 100f, 12.5)
                val actual = first[j * map.size + i]
                worst = maxOf(worst, kotlin.math.abs(expected - actual))
                darkest = minOf(darkest, actual)
            }
            assertTrue("matches SunOcclusion.instant within 1e-2, worst $worst", worst < 1e-2f)
            assertTrue("there is real shade on the map (darkest $darkest)", darkest < 0.9f)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())

            assertTrue(map.update(fair, sun, 0f, 0f, planet, 100f, 312.5))
            val later = read(map.texture!!)
            assertFalse("wind moves the shade", first.contentEquals(later))
            val laterAt = occlusion.instant(fair, sun, worldOf(100, 200)[0], worldOf(100, 200)[1], planet, 100f, 312.5)
            assertEquals(laterAt, later[200 * map.size + 100], 1e-2f)

            val reloaded = fair.copy(low = CloudBand(CloudType.STRATUS, coverage = 1f, windX = 0f, windZ = 0f), mid = null, high = null)
            assertTrue(map.update(reloaded, sun, 0f, 0f, planet, 100f, 312.5))
            val overcast = read(map.texture!!)
            val expected = occlusion.instant(reloaded, sun, worldOf(10, 10)[0], worldOf(10, 10)[1], planet, 100f, 312.5)
            assertEquals(expected, overcast[10 * map.size + 10], 1e-2f)
            assertTrue("a full low overcast is uniformly dark", overcast.all { it < 0.9f })
        } finally {
            map.dispose()
        }
    }

    @Test
    fun followsTheCenterInWholeTexelsAndLooksUpAlongTheSun() = run {
        val map = CloudShadowMap(shaders())
        try {
            val texel = map.extent / map.size
            assertTrue(map.update(fair, sun, 1000f + texel * 0.3f, -500f, planet, 100f, 0.0))
            val m = map.worldToMap(sun, Matrix4())
            val v = m.`val`
            fun toMap(x: Float, y: Float, z: Float) = floatArrayOf(
                v[Matrix4.M00] * x + v[Matrix4.M01] * y + v[Matrix4.M02] * z + v[Matrix4.M03],
                v[Matrix4.M10] * x + v[Matrix4.M11] * y + v[Matrix4.M12] * z + v[Matrix4.M13],
            )
            // the center snaps to a texel, so the map stays within a texel of it
            val center = toMap(1000f, 0f, -500f)
            assertEquals(0.5f, center[0], 1.5f / map.size)
            assertEquals(0.5f, center[1], 1.5f / map.size)
            // a point up the sun ray from a ground point maps to the same texel as that ground point
            val ground = toMap(1100f, 0f, -420f)
            val up = toMap(1100f + sun.x * 80f, sun.y * 80f, -420f + sun.z * 80f)
            assertEquals(ground[0], up[0], 1e-4f)
            assertEquals(ground[1], up[1], 1e-4f)
            // the map covers 4 km: 1 km east of the center moves a quarter of the map
            assertEquals(center[0] + 0.25f, toMap(2000f, 0f, -500f)[0], 1.5f / map.size)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun anUnavailableMapFailsOnceLogsAndLeavesNothingBehind() = run {
        val log = RecordingLogger()
        val original = Gdx.gl
        val broken = object : GL20 by original {
            override fun glCheckFramebufferStatus(target: Int) = GL20.GL_FRAMEBUFFER_UNSUPPORTED
        }
        val map = CloudShadowMap(shaders(), log)
        try {
            Gdx.gl = broken
            assertFalse(map.update(fair, sun, 0f, 0f, planet, 100f, 0.0))
            Gdx.gl = original
            assertTrue(map.failed)
            assertNull(map.texture)
            assertFalse("no retry", map.update(fair, sun, 0f, 0f, planet, 100f, 1.0))
            assertEquals("logged once", 1, log.warnings.size)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            Gdx.gl = original
            map.dispose()
        }
    }
}
