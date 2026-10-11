/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLevel
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudMeta.CloudBand
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudType
import net.nevinsky.abyssus.lib.core.assets.sky.clouds.SunOcclusion
import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/** The cloud shadow map against the CPU model it must agree with. Opt-in: `-Dabyssus.glTests=true`. */
class CloudShadowMapGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private val sun = Vector3(0.3f, 0.85f, -0.4f).nor()
    private val planet = 6360e3f
    private val height = 100f

    private val weather = CloudMeta(
        low = CloudBand(CloudType.CUMULUS, CloudLevel.LOW, coverage = 0.5f),
        mid = CloudBand(CloudType.ALTOCUMULUS, CloudLevel.MID, coverage = 0.5f),
    )

    /** Runs [block] on the GL thread of a harness frame, where the context is current. */
    private fun inGl(block: () -> Unit) {
        val params = SceneRenderParams(Rgba(0f, 0f, 0f, 1f), null, null, CameraParams.DEFAULT)
        var error: Throwable? = null
        val result = GlHarness.render(params, 3) { _, frame ->
            if (frame == 2) try { block() } catch (e: Throwable) { error = e }
        }
        error?.let { throw it }
        assertNull(result.error)
    }

    private fun newMap(log: RecordingLogger = RecordingLogger()) =
        CloudShadowMap(ShaderStorage(), log)

    /** The map's value at the texel under the ground point ([x], [z]); the map must have been updated. */
    private fun read(map: CloudShadowMap, x: Float, z: Float): Float {
        val gl = Gdx.gl
        val texel = CLOUD_SHADOW_EXTENT / CLOUD_SHADOW_SIZE
        val tx = ((x - map.center[0]) / texel + CLOUD_SHADOW_SIZE / 2f).toInt().coerceIn(0, CLOUD_SHADOW_SIZE - 1)
        val tz = ((z - map.center[1]) / texel + CLOUD_SHADOW_SIZE / 2f).toInt().coerceIn(0, CLOUD_SHADOW_SIZE - 1)
        val previous = BufferUtils.newIntBuffer(16).also { gl.glGetIntegerv(0x8CA6, it) }.get(0)
        val fbo = gl.glGenFramebuffer()
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo)
        val texture: GLTexture = map.texture!!
        gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture.textureObjectHandle, 0)
        val pixel = BufferUtils.newFloatBuffer(4)
        gl.glReadPixels(tx, tz, 1, 1, GL20.GL_RGBA, GL20.GL_FLOAT, pixel)
        gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
        gl.glDeleteFramebuffer(fbo)
        return pixel.get(0)
    }

    /** The centre of the texel under ([x], [z]) in world metres, which is the ground point the map evaluated. */
    private fun texelCentre(map: CloudShadowMap, x: Float, z: Float): Pair<Float, Float> {
        val texel = CLOUD_SHADOW_EXTENT / CLOUD_SHADOW_SIZE
        val ix = Math.floor(((x - map.center[0]) / texel + CLOUD_SHADOW_SIZE / 2f).toDouble()).toFloat()
        val iz = Math.floor(((z - map.center[1]) / texel + CLOUD_SHADOW_SIZE / 2f).toDouble()).toFloat()
        return (ix + 0.5f - CLOUD_SHADOW_SIZE / 2f) * texel + map.center[0] to (iz + 0.5f - CLOUD_SHADOW_SIZE / 2f) * texel + map.center[1]
    }

    /** Samples a grid of ground points, comparing the map with [SunOcclusion.instant]; returns the largest difference and the range. */
    private fun compare(map: CloudShadowMap, clouds: CloudMeta, time: Double): Triple<Float, Float, Float> {
        var worst = 0f
        var low = 1f
        var high = 0f
        for (i in -8..8) for (j in -8..8) {
            val (gx, gz) = texelCentre(map, map.center[0] + i * 230f, map.center[1] + j * 230f)
            val expected = SunOcclusion().instant(clouds, sun, gx, gz, planet, height, time)
            val actual = read(map, gx, gz)
            worst = maxOf(worst, abs(actual - expected))
            low = minOf(low, expected)
            high = maxOf(high, expected)
        }
        return Triple(worst, low, high)
    }

    @Test
    fun theMapMatchesTheCpuSunOcclusion() = inGl {
        val map = newMap()
        try {
            assertTrue(map.update(weather, sun, 120f, -80f, planet, height, 0.0))
            val (worst, low, high) = compare(map, weather, 0.0)
            assertTrue("the weather casts shadows and leaves gaps: $low .. $high", low < 0.8f && high > 0.95f)
            assertTrue("largest difference $worst", worst <= 1e-2f)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun thePatternDriftsWithTheWindAndFollowsAReload() = inGl {
        val map = newMap()
        try {
            map.update(weather, sun, 0f, 0f, planet, height, 0.0)
            val before = read(map, 100f, 100f)
            val (w0, _, _) = compare(map, weather, 0.0)
            assertTrue(w0 <= 1e-2f)

            map.update(weather, sun, 0f, 0f, planet, height, 60.0)
            val (w60, low, high) = compare(map, weather, 60.0)
            assertTrue("matches at 60 s: $w60", w60 <= 1e-2f)
            assertTrue("the pattern moved (it varies $low..$high)", compare(map, weather, 0.0).first > 1e-2f)

            val reloaded = CloudMeta(low = CloudBand(CloudType.STRATUS, CloudLevel.LOW, coverage = 0.9f))
            map.update(reloaded, sun, 0f, 0f, planet, height, 60.0)
            val (wr, _, _) = compare(map, reloaded, 60.0)
            assertTrue("follows the new weather: $wr", wr <= 1e-2f)
            assertTrue(before >= 0f)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun worldPositionsSlideAlongTheSunToTheSameTexel() = inGl {
        val map = newMap()
        try {
            map.update(weather, sun, 300f, 200f, planet, height, 0.0)
            fun uv(p: Vector3): Pair<Float, Float> {
                val m = map.worldToMap.`val`
                val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
                val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
                return x to y
            }
            val ground = Vector3(450f, 0f, 120f)
            val (u0, v0) = uv(ground)
            assertTrue("inside the map: $u0, $v0", u0 in 0f..1f && v0 in 0f..1f)
            // the ground point is under the middle of the map when it is at the map's centre
            val (uc, vc) = uv(Vector3(map.center[0], 0f, map.center[1]))
            assertEquals(0.5f, uc, 1e-5f)
            assertEquals(0.5f, vc, 1e-5f)
            // a point 800 m up the sun ray lands where the ground point does
            val up = Vector3(ground).mulAdd(sun, 800f / sun.y)
            val (u1, v1) = uv(up)
            assertEquals(u0, u1, 1e-4f)
            assertEquals(v0, v1, 1e-4f)
            // 1 km further along the ground is a quarter of the map further
            val (u2, _) = uv(Vector3(ground).add(1000f, 0f, 0f))
            assertEquals(u0 + 0.25f, u2, 1e-5f)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun theMapFollowsTheTargetInWholeTexels() = inGl {
        val map = newMap()
        try {
            val texel = CLOUD_SHADOW_EXTENT / CLOUD_SHADOW_SIZE
            map.update(weather, sun, 3.3f * texel + 1f, -2.2f * texel, planet, height, 0.0)
            assertEquals(3f * texel, map.center[0], 1e-3f)
            assertEquals(-3f * texel, map.center[1], 1e-3f)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun anUnbuildableMapFailsOnceAndStaysOff() = inGl {
        val log = RecordingLogger()
        var attempts = 0
        val map = CloudShadowMap(ShaderStorage(), log, createTarget = { attempts++; throw IllegalStateException("no float targets") })
        try {
            assertFalse(map.update(weather, sun, 0f, 0f, planet, height, 0.0))
            assertFalse(map.update(weather, sun, 0f, 0f, planet, height, 1.0))
            assertNull(map.texture)
            assertTrue(map.unavailable)
            assertEquals(1, attempts)
            assertEquals(1, log.warnings.size)
            assertNotNull(Gdx.gl)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun updateRestoresTheFramebufferAndViewport() = inGl {
        val map = newMap()
        try {
            val gl = Gdx.gl
            gl.glViewport(0, 0, 77, 55)
            val before = BufferUtils.newIntBuffer(16).also { gl.glGetIntegerv(0x8CA6, it) }.get(0)
            map.update(weather, sun, 0f, 0f, planet, height, 0.0)
            val after = BufferUtils.newIntBuffer(16).also { gl.glGetIntegerv(0x8CA6, it) }.get(0)
            val view = BufferUtils.newIntBuffer(16).also { gl.glGetIntegerv(GL20.GL_VIEWPORT, it) }
            assertEquals(before, after)
            assertEquals(77, view.get(2))
            assertEquals(55, view.get(3))
        } finally {
            map.dispose()
        }
    }

    @Test
    fun theReadbackIsTheMapAsBytesAndCostsNothingUntilAsked() = inGl {
        val map = newMap()
        try {
            assertNull("no map yet", map.readVisibility())
            map.update(weather, sun, 0f, 0f, planet, height, 0.0)
            val bytes = map.readVisibility()!!
            assertEquals(CLOUD_SHADOW_SIZE * CLOUD_SHADOW_SIZE, bytes.size)
            val texel = CLOUD_SHADOW_EXTENT / CLOUD_SHADOW_SIZE
            var worst = 0
            for (i in -6..6) for (j in -6..6) {
                val (gx, gz) = texelCentre(map, map.center[0] + i * 300f, map.center[1] + j * 300f)
                val tx = ((gx - map.center[0]) / texel + CLOUD_SHADOW_SIZE / 2f).toInt()
                val tz = ((gz - map.center[1]) / texel + CLOUD_SHADOW_SIZE / 2f).toInt()
                val expected = SunOcclusion().instant(weather, sun, gx, gz, planet, height, 0.0)
                worst = maxOf(worst, abs((bytes[tz * CLOUD_SHADOW_SIZE + tx].toInt() and 255) - Math.round(expected * 255f)))
            }
            assertTrue("largest difference $worst of 255", worst <= 4)
        } finally {
            map.dispose()
        }
    }
}
