/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.procedural

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.sky.SkyFrame
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Design decision D15 of `improve-control-line-performance-stability`: the bundled Control Line sky (which declares
 * `SKY_CACHEABLE`) drawn from its cached atmosphere matches the same sky drawn per pixel. Opt-in GL test.
 */
class AtmosphereCacheGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    // the shipped sky, read where the game bundles it (the test runs in projects/lib-core)
    private val skyDir = File("../app-game-control-line/project/ControlLine/assets/skybox_physical")
    private val noon = Vector3(0.3f, 0.85f, -0.4f).nor()
    private val lowSun = Vector3(0.1f, 0.06f, -1f).nor()

    private fun prepared(fragment: String = File(skyDir, "sky.frag").readText()) =
        PreparedProceduralSky(AtmosphereParams(), File(skyDir, "sky.vert").readText(), fragment, "skybox_physical")

    private fun sky(cached: Boolean, fragment: String = File(skyDir, "sky.frag").readText()) =
        ProceduralSky(prepared(fragment), shaders = ShaderStorage(), cacheAtmosphere = cached)

    /** A camera at the origin looking along [toward], [fov] degrees tall: 30 degrees over 512 pixels is the game's ~17 px a degree. */
    private fun camera(toward: Vector3, fov: Float = 30f) = PerspectiveCamera(fov, SIZE.toFloat(), SIZE.toFloat()).apply {
        near = 0.1f
        far = 100f
        direction.set(toward).nor()
        up.set(Vector3.Y)
        update()
    }

    private fun capture(draw: () -> Unit): ByteArray {
        val buffer = FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, false)
        try {
            buffer.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDisable(GL20.GL_BLEND)
            draw()
            val pixels = BufferUtils.newByteBuffer(SIZE * SIZE * 4)
            Gdx.gl.glReadPixels(0, 0, SIZE, SIZE, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels)
            buffer.end()
            return ByteArray(SIZE * SIZE * 4).also { pixels.get(it) }
        } finally {
            buffer.dispose()
        }
    }

    /** The largest channel difference and the share of pixels differing by more than 2/255. */
    private fun diff(a: ByteArray, b: ByteArray): Pair<Int, Double> {
        var most = 0
        var noticeable = 0
        for (p in 0 until SIZE * SIZE) {
            var pixel = 0
            for (c in 0..2) pixel = max(pixel, abs((a[p * 4 + c].toInt() and 255) - (b[p * 4 + c].toInt() and 255)))
            most = max(most, pixel)
            if (pixel > 2) noticeable++
        }
        return most to noticeable.toDouble() / (SIZE * SIZE)
    }

    @Test
    fun theCachedSkyMatchesTheSkyDrawnPerPixel() = TestGl.run(120) {
        assertTrue("the bundled sky opts in", prepared().cacheable)
        val direct = sky(cached = false)
        val cached = sky(cached = true)
        try {
            assertNull(direct.atmosphereBakes)
            val views = listOf(
                "noon, up" to (noon to Vector3(0.2f, 0.6f, -1f)),
                "noon, the horizon" to (noon to Vector3(0f, 0f, -1f)),
                "noon, the sun" to (noon to noon),
                "low sun, the sun and the horizon" to (lowSun to Vector3(lowSun.x, 0.02f, lowSun.z)),
                "low sun, away" to (lowSun to Vector3(0f, 0.1f, 1f)),
                "noon, the ground" to (noon to Vector3(0f, -0.4f, -1f)),
            )
            for ((name, view) in views) {
                val (sun, toward) = view
                val frame = SkyFrame(sun, 0.0, clouds = false)
                val want = capture { direct.draw(camera(toward), frame) }
                val got = capture { cached.draw(camera(toward), frame) }
                val (most, share) = diff(want, got)
                println("atmosphere cache, $name: largest channel difference $most, noticeable share $share")
                assertEquals("$name: GL error", GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                assertTrue("$name: largest channel difference $most", most <= MAX_CHANNEL)
                assertTrue("$name: $share of the pixels differ by more than 2/255", share <= MAX_NOTICEABLE_SHARE)
            }
        } finally {
            direct.dispose()
            cached.dispose()
        }
    }

    @Test
    fun theCubeIsRenderedOnlyWhenTheSunMoves() = TestGl.run(60) {
        val sky = sky(cached = true)
        try {
            repeat(10) { capture { sky.draw(camera(Vector3(0f, 0.3f, -1f)), SkyFrame(noon, it.toDouble(), clouds = false)) } }
            assertEquals("ten frames under one sun: one bake", 1, sky.atmosphereBakes)
            capture { sky.draw(camera(Vector3(0f, 0.3f, -1f)), SkyFrame(lowSun, 11.0, clouds = false)) }
            assertEquals("the sun moved", 2, sky.atmosphereBakes)
        } finally {
            sky.dispose()
        }
    }

    @Test
    fun aSkyThatDoesNotOptInIsDrawnPerPixel() = TestGl.run(60) {
        val plain = File(skyDir, "sky.frag").readText().replace("#define SKY_CACHEABLE", "")
        assertFalse(prepared(plain).cacheable)
        val sky = sky(cached = true, fragment = plain)
        try {
            assertNull(sky.atmosphereBakes)
        } finally {
            sky.dispose()
        }
    }

    private companion object {
        const val SIZE = 512
        // measured: at most 1/255 and no pixel over 2/255 (NVIDIA RTX 3070 Ti, Mesa llvmpipe); one step of margin
        const val MAX_CHANNEL = 2
        const val MAX_NOTICEABLE_SHARE = 0.001
    }
}
