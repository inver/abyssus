/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.terrain.PreparedTerrain
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.light.DirectionalInput
import net.nevinsky.abyssus.lib.render.light.RenderLights
import net.nevinsky.abyssus.lib.render.terrain.TerrainDraw
import net.nevinsky.abyssus.lib.render.terrain.TerrainLighting
import net.nevinsky.abyssus.lib.render.terrain.TerrainPass
import net.nevinsky.abyssus.lib.render.terrain.TerrainVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Opt-in (`-Dabyssus.glTests=true`): both terrain programs compile and draw, and with the same single sun they agree. */
class TerrainPassGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val size = 32

    private fun grey(value: Float): Texture {
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888).also { it.setColor(value, value, value, 1f); it.fill() }
        return Texture(pixmap).also { pixmap.dispose() }
    }

    private fun mesh(base: Texture) =
        TerrainMesh(PreparedTerrain(TerrainData(2, FloatArray(4), 10, 1f), mapOf("splatBase" to "base")), BuiltAssets { if (it == "base") base else null })

    private fun camera() = PerspectiveCamera(60f, size.toFloat(), size.toFloat()).apply {
        position.set(5f, 8f, 5f); lookAt(5f, 0f, 5.001f); near = 0.1f; far = 50f; update()
    }

    private fun frame(profile: net.nevinsky.abyssus.lib.render.light.LightProfile, camera: PerspectiveCamera, lights: RenderLights) =
        FrameInputs(camera.combined, camera.position, floatArrayOf(0.2f, 0.2f, 0.2f), null, lights, profile)

    private fun pixels(framebuffer: FrameBuffer, draw: () -> Unit): IntArray {
        framebuffer.begin()
        try {
            Gdx.gl.glViewport(0, 0, size, size)
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            draw()
            val buffer = BufferUtils.newByteBuffer(size * size * 4)
            Gdx.gl.glReadPixels(0, 0, size, size, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            return IntArray(size * size * 4) { buffer[it].toInt() and 255 }
        } finally {
            framebuffer.end()
        }
    }

    @Test
    fun bothVariantsDrawTheSameImageUnderTheSameSingleSunWithoutShadowOrFog() {
        TestGl.run {
            val base = grey(0.6f)
            val mesh = mesh(base)
            val camera = camera()
            val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
            val shaders = ShaderStorage()
            val editor = TerrainPass(shaders, TerrainVariant.EDITOR_MULTI_LIGHT)
            val game = TerrainPass(shaders, TerrainVariant.GAME_SINGLE_SUN)
            try {
                val towardSun = Vector3(0.3f, 0.8f, 0.4f).nor()
                val travel = Vector3(towardSun).scl(-1f)
                val lights = RenderLights(listOf(DirectionalInput("sun", travel, 0.7f, 0.6f, 0.5f)))
                val draws = listOf(TerrainDraw(mesh, Matrix4()))
                val a = pixels(framebuffer) {
                    editor.draw(frame(TerrainVariant.EDITOR_MULTI_LIGHT.profile, camera, lights), TerrainLighting.Editor(), draws)
                }
                val b = pixels(framebuffer) {
                    game.draw(frame(TerrainVariant.GAME_SINGLE_SUN.profile, camera, lights), TerrainLighting.Sun(towardSun, 0.7f, 0.6f, 0.5f), draws)
                }
                val centre = (size / 2 * size + size / 2) * 4
                assertTrue("something is lit ${a.copyOfRange(centre, centre + 3).toList()}", a[centre] > 30)
                var worst = 0
                for (i in a.indices) worst = maxOf(worst, kotlin.math.abs(a[i] - b[i]))
                assertTrue("the variants differ by $worst/255", worst <= 2)
            } finally {
                editor.dispose(); game.dispose(); framebuffer.dispose(); mesh.dispose(); base.dispose()
            }
        }
    }

    @Test
    fun aFrameOfTheOtherProfileOrLightingIsRefused() {
        TestGl.run {
            val camera = camera()
            val pass = TerrainPass(ShaderStorage(), TerrainVariant.GAME_SINGLE_SUN)
            val base = grey(0.5f)
            val mesh = mesh(base)
            try {
                val draws = listOf(TerrainDraw(mesh, Matrix4()))
                val editorFrame = frame(TerrainVariant.EDITOR_MULTI_LIGHT.profile, camera, RenderLights())
                val refused = runCatching { pass.draw(editorFrame, TerrainLighting.Editor(), draws) }.exceptionOrNull()
                assertTrue(refused is IllegalArgumentException)
            } finally {
                pass.dispose(); mesh.dispose(); base.dispose()
            }
        }
    }

    @Test
    fun aLentBlankTextureIsNotDisposedAndAMadeOneIs() {
        TestGl.run {
            var disposed = false
            val lent = object : Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888)) {
                override fun dispose() { disposed = true; super.dispose() }
            }
            val borrowing = TerrainPass(ShaderStorage(), TerrainVariant.EDITOR_MULTI_LIGHT, lent)
            val owning = TerrainPass(ShaderStorage(), TerrainVariant.GAME_SINGLE_SUN)
            assertFalse(borrowing.ownsBlank)
            assertTrue(owning.ownsBlank)
            borrowing.dispose()
            owning.dispose()
            assertFalse("the lent texture is still the host's", disposed)
            lent.dispose()
            assertTrue(disposed)
        }
    }
}
