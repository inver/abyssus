/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.terrain.PreparedTerrain
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.Rgba
import net.nevinsky.abyssus.lib.core.editor.scene.CameraParams
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.plugin.sceneview.terrain.TerrainShader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The terrain shader mixing two sky irradiance cubes, read back. Opt-in: `-Dabyssus.glTests=true`. */
class TerrainSkyFadeGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private fun cube(r: Float, g: Float, b: Float): Cubemap {
        val face = Pixmap(4, 4, Pixmap.Format.RGBA8888).also { it.setColor(r, g, b, 1f); it.fill() }
        return Cubemap(face, face, face, face, face, face)
    }

    private fun axes(r: Float, g: Float, b: Float) = FloatArray(18) { floatArrayOf(r, g, b)[it % 3] }

    private val ambient = Rgba(0f, 0.5f, 0f, 1f)

    /** The centre pixel of a flat terrain lit only by [sky] (null: the green ambient color), run inside the GL context. */
    private fun pixels(skies: (red: Cubemap, blue: Cubemap) -> List<EnvironmentLightAttribute?>): List<IntArray> {
        val out = ArrayList<IntArray>()
        val params = SceneRenderParams(Rgba(0f, 0f, 0f, 1f), null, null, CameraParams.DEFAULT)
        val result = GlHarness.render(params, 3) { _, frame ->
            if (frame != 2) return@render
            val shader = TerrainShader(ShaderStorage().withResources("/shader/scene", SceneRenderer::class.java))
            val mesh = TerrainMesh(PreparedTerrain(TerrainData(2, FloatArray(4), 10, 1f), emptyMap()), BuiltAssets { null })
            val entity = TerrainEntity(AssetPlacement("t", "t", PlacementTransform.IDENTITY), mesh, Matrix4())
            val camera = OrthographicCamera(12f, 12f).apply {
                position.set(5f, 10f, 5f); up.set(0f, 0f, -1f); lookAt(5f, 0f, 5f); near = 0.1f; far = 30f; update()
            }
            val buffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
            val red = cube(0.5f, 0f, 0f)
            val blue = cube(0f, 0f, 0.5f)
            try {
                for (sky in skies(red, blue)) {
                    buffer.begin()
                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                    shader.draw(camera, listOf(entity), ambient, null, NO_LIGHTS, sky, null)
                    val px = BufferUtils.newByteBuffer(4)
                    Gdx.gl.glReadPixels(16, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px)
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                    out += IntArray(3) { px[it].toInt() and 255 }
                    buffer.end()
                }
            } finally {
                buffer.dispose(); red.dispose(); blue.dispose(); mesh.dispose(); shader.dispose()
            }
        }
        assertNull(result.error)
        return out
    }

    @Test
    fun theTerrainMixesTheTwoIrradianceCubesByTheBlend() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val (start, half, end, plain, none) = pixels { red, blue ->
            fun sky(blend: Float?) = EnvironmentLightAttribute(
                red, red, 1, axes(0.5f, 0f, 0f),
                blend?.let { EnvironmentLightAttribute.Next(blue, blue, axes(0f, 0f, 0.5f)) }, blend ?: 0f,
            )
            listOf(sky(0f), sky(0.5f), sky(1f), sky(null), null)
        }
        assertTrue("blend 0 is the red sky: ${start.toList()}", start[0] > 30 && start[2] <= 2)
        assertTrue("blend 1 is the blue sky: ${end.toList()}", end[2] > 30 && end[0] <= 2)
        assertTrue("blend 0.5 has both: ${half.toList()}", half[0] > 12 && half[2] > 12)
        assertTrue("blend 0.5 is about half: ${half.toList()} of ${start.toList()}", kotlin.math.abs(half[0] * 2 - start[0]) <= start[0] * 0.2 + 3)
        assertEquals("no next environment mixes nothing", start.toList(), plain.toList())
        assertTrue("without a sky the green ambient light returns: ${none.toList()}", none[1] > 30 && none[0] <= 2 && none[2] <= 2)
    }

    private operator fun <T> List<T>.component4() = this[3]
    private operator fun <T> List<T>.component5() = this[4]
}
