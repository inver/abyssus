/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
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
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import net.nevinsky.abyssus.plugin.sceneview.SceneRenderer
import net.nevinsky.abyssus.plugin.sceneview.TerrainEntity
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The terrain's sky light cross-fade, by image. Opt-in (`-Dabyssus.glTests=true`). */
class TerrainSkyFadeGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", GlHarness.enabled)

    private fun solid(r: Float, g: Float, b: Float): Cubemap {
        val pixmap = Pixmap(4, 4, Pixmap.Format.RGBA8888).also { it.setColor(r, g, b, 1f); it.fill() }
        return Cubemap(pixmap, pixmap, pixmap, pixmap, pixmap, pixmap).also { pixmap.dispose() }
    }

    @Test
    fun bothEndpointsAnIntermediateLightAndTheAmbientAreDrawn() {
        var checked = false
        val result = GlHarness.render(SceneRenderParams.DEFAULT, 2) { _, frame ->
            if (frame != 0) return@render
            val a = solid(0f, 0f, 0.4f)
            val b = solid(0.6f, 0f, 0f)
            val shader = TerrainShader(ShaderStorage().withResources("/shader/scene", SceneRenderer::class.java))
            val mesh = TerrainMesh(PreparedTerrain(TerrainData(2, FloatArray(4), 10, 1f), emptyMap()), BuiltAssets { null })
            val entity = TerrainEntity(AssetPlacement("1", "terrain", PlacementTransform.IDENTITY), mesh, Matrix4())
            val camera = PerspectiveCamera(60f, 32f, 32f).apply {
                position.set(5f, 8f, 5f); lookAt(5f, 0f, 5.001f); near = 0.1f; far = 50f; update()
            }
            val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
            try {
                fun draw(irradiance: Cubemap?, to: Cubemap?, weight: Float, ambient: Rgba? = null): IntArray {
                    framebuffer.begin()
                    try {
                        Gdx.gl.glViewport(0, 0, 32, 32)
                        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
                        shader.draw(camera, listOf(entity), ambient, null, NO_LIGHTS, irradiance, null, to, weight)
                        val pixel = BufferUtils.newByteBuffer(4)
                        Gdx.gl.glReadPixels(16, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel)
                        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                        return IntArray(3) { pixel[it].toInt() and 255 }
                    } finally {
                        framebuffer.end()
                    }
                }

                val onlyA = draw(a, null, 0f)
                val onlyB = draw(b, null, 0f)
                assertTrue("sky A is blue ${onlyA.toList()}, sky B red ${onlyB.toList()}", onlyA[2] > 20 && onlyA[0] < 5 && onlyB[0] > 20 && onlyB[2] < 5)
                assertArrayEquals("weight 0 is the first sky", onlyA, draw(a, b, 0f))
                assertArrayEquals("weight 1 is the second sky", onlyB, draw(a, b, 1f))
                val half = draw(a, b, 0.5f)
                assertEquals("red at 0.5 ${half.toList()}", onlyB[0] / 2f, half[0].toFloat(), 3f)
                assertEquals("blue at 0.5 ${half.toList()}", onlyA[2] / 2f, half[2].toFloat(), 3f)
                val grey = draw(null, null, 0f, Rgba(0.3f, 0.3f, 0.3f, 1f))
                assertTrue("ambient restored ${grey.toList()}", grey[0] > 20 && kotlin.math.abs(grey[0] - grey[1]) <= 2 && kotlin.math.abs(grey[0] - grey[2]) <= 2)
                assertArrayEquals("a weight without a target keeps the ambient", grey, draw(null, b, 1f, Rgba(0.3f, 0.3f, 0.3f, 1f)))
                checked = true
            } finally {
                framebuffer.dispose(); shader.dispose(); mesh.dispose(); a.dispose(); b.dispose()
            }
        }
        assertNull(result.error)
        assertTrue("the frame callback ran", checked)
    }
}
