/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.water

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

private const val W = 96
private const val H = 72

/** The water shader and mesh in a real GL context: both presets compile, draw water and move. Opt-in GL checks. */
class WaterShaderGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val presets = WaterPresets()

    private fun camera() = PerspectiveCamera(60f, W.toFloat(), H.toFloat()).also {
        it.position.set(0f, 14f, 14f); it.lookAt(0f, 0f, 0f); it.near = 0.1f; it.far = 500f; it.update()
    }

    private fun frame(seconds: Double) = WaterFrame(
        camera(), W, H, seconds,
        ambient = floatArrayOf(0.3f, 0.3f, 0.3f), skyColor = floatArrayOf(0.5f, 0.6f, 0.8f), fogColor = floatArrayOf(0f, 0f, 0f), fogK = 0f,
        directionalCount = 1,
        directionalDirections = FloatArray(MAX_DIRECTIONAL * 3).also { it[0] = 0.3f; it[1] = -0.8f; it[2] = -0.5f },
        directionalColors = FloatArray(MAX_DIRECTIONAL * 3).also { it[0] = 4f; it[1] = 4f; it[2] = 4f },
    )

    private fun draw(drawable: WaterDrawable, surface: WaterSettings, seconds: Double): ByteArray {
        val target = FrameBuffer(Pixmap.Format.RGBA8888, W, H, true)
        target.begin()
        try {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glViewport(0, 0, W, H)
            drawable.draw(surface, frame(seconds), WaterTextures(null, null, null, null))
            val buffer = BufferUtils.newByteBuffer(W * H * 4)
            Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            return ByteArray(W * H * 4) { buffer.get(it) }
        } finally {
            target.end()
            target.dispose()
        }
    }

    private fun painted(pixels: ByteArray) = (0 until W * H).count { i ->
        (pixels[i * 4].toInt() and 255) + (pixels[i * 4 + 1].toInt() and 255) + (pixels[i * 4 + 2].toInt() and 255) > 12
    }

    private fun difference(a: ByteArray, b: ByteArray) = a.indices.sumOf { kotlin.math.abs((a[it].toInt() and 255) - (b[it].toInt() and 255)) }

    @Test fun theShaderCompilesAndBothPresetsDrawAnimatedWater() = TestGl.run {
        val drawable = WaterDrawable(ShaderStorage())
        try {
            for (preset in WaterPreset.entries) {
                val surface = presets.defaults(preset, "w", "w", 0.0, 0.0, 0.0).copy(width = 60.0, length = 60.0)
                val first = draw(drawable, surface, 0.0)
                val later = draw(drawable, surface, 1.7)
                assertTrue("$preset covers the view (${painted(first)} px)", painted(first) > W * H / 3)
                assertTrue("$preset moves with time", difference(first, later) > 0)
                assertEquals("the same time draws the same frame", 0, difference(first, draw(drawable, surface, 0.0)))
            }
        } finally {
            drawable.dispose()
        }
    }

    @Test fun flatWaterDoesNotMove() = TestGl.run {
        val drawable = WaterDrawable(ShaderStorage())
        try {
            val flat = presets.defaults(WaterPreset.LAKE, "w", "w", 0.0, 0.0, 0.0).copy(width = 60.0, length = 60.0, waveAmplitude = 0.0)
            assertEquals(0, difference(draw(drawable, flat, 0.0), draw(drawable, flat, 5.0)))
        } finally {
            drawable.dispose()
        }
    }

    @Test fun surfacesKeepTheirOwnTintAndLevel() = TestGl.run {
        val drawable = WaterDrawable(ShaderStorage())
        try {
            val green = presets.defaults(WaterPreset.LAKE, "a", "a", 0.0, 0.0, 0.0).copy(width = 60.0, length = 60.0, tintR = 0.0, tintG = 1.0, tintB = 0.0)
            val red = green.copy(tintR = 1.0, tintG = 0.0)
            val g = draw(drawable, green, 0.0)
            val r = draw(drawable, red, 0.0)
            val centre = (H / 2 * W + W / 2) * 4
            assertTrue("green water is greener than red", (g[centre + 1].toInt() and 255) > (g[centre].toInt() and 255))
            assertTrue("red water is redder than green", (r[centre].toInt() and 255) > (r[centre + 1].toInt() and 255))
        } finally {
            drawable.dispose()
        }
    }

    @Test fun meshesAreKeptForRecentSurfacesAndBounded() = TestGl.run {
        val drawable = WaterDrawable(ShaderStorage())
        try {
            for (i in 1..40) draw(drawable, presets.defaults(WaterPreset.LAKE, "w", "w", 0.0, 0.0, 0.0).copy(width = 10.0 + i, length = 10.0 + i), 0.0)
            assertTrue("no more meshes than the cache keeps (${drawable.meshCount})", drawable.meshCount <= 16)
        } finally {
            drawable.dispose()
        }
    }
}
