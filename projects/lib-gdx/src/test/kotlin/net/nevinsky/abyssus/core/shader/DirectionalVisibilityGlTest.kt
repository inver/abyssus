/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** One directional light's visibility map in the default and PBR shaders: only that light changes. */
class DirectionalVisibilityGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private class Scene(val pbr: Boolean) : AutoCloseable {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0 })
        val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0f, 0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f))
            setIndices(intArrayOf(0, 1, 2))
        }
        val camera = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }

        /** The pixel at the triangle's middle, lit by a red light (index 0) and a green one (index 1). */
        fun draw(visibility: DirectionalVisibilityAttribute?, atlas: Texture? = null, redShadowed: Boolean = false): IntArray {
            val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
            if (pbr) material.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
            val renderable = Renderable().apply {
                meshPart.set("tri", mesh, 0, 3, GL20.GL_TRIANGLES)
                this.material = material
                environment = Environment().apply {
                    val red = DirectionalLight().set(Color(0.8f, 0f, 0f, 1f), 0f, 0f, -1f)
                    add(red)
                    add(DirectionalLight().set(Color(0f, 0.8f, 0f, 1f), 0f, 0f, -1f))
                    if (atlas != null) {
                        val records = if (redShadowed) listOf(
                            ShadowLightRecord("red", ShadowLightKind.DIRECTIONAL, red,
                                listOf(ShadowAtlasView(camera.combined.cpy(), Vector4(0f, 0f, 1f, 1f))), Vector3(), 4f, 0.0008f)
                        ) else emptyList()
                        set(ShadowAtlasAttribute(atlas, records))
                    }
                    if (visibility != null) set(visibility)
                }
            }
            val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
            framebuffer.begin()
            try {
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                context.begin()
                val shader = provider.get(renderable)!!
                shader.begin(camera, context); shader.render(renderable); shader.end(); context.end()
                val pixel = BufferUtils.newByteBuffer(4)
                Gdx.gl.glReadPixels(16, 14, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel)
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
                return IntArray(3) { pixel[it].toInt() and 255 }
            } finally {
                framebuffer.end()
            }
        }

        override fun close() {
            provider.dispose(); framebuffer.dispose(); mesh.dispose()
        }
    }

    private fun map(value: Float): Texture {
        val pixmap = Pixmap(4, 4, Pixmap.Format.RGBA8888).also { it.setColor(value, 0f, 0f, 1f); it.fill() }
        return Texture(pixmap).also { pixmap.dispose() }
    }

    /** World (x, y) to map coordinates: the map covers 4 world units around the origin, shifted by [shift] units. */
    private fun worldToMap(shift: Float = 0f) = Matrix4().setToTranslation(0.5f - shift * 0.25f, 0.5f, 0f).scale(0.25f, 0.25f, 1f)

    @Test
    fun onlyTheIndexedLightIsMultipliedByTheMap() = TestGl.run {
        val dark = map(0f)
        val half = map(0.5f)
        try {
            for (pbr in listOf(false, true)) Scene(pbr).use { scene ->
                val lit = scene.draw(null)
                assertTrue("both lights lit (pbr=$pbr): ${lit.toList()}", lit[0] > 20 && lit[1] > 20)
                val greenBlocked = scene.draw(DirectionalVisibilityAttribute(dark, 1, worldToMap()))
                assertEquals("red untouched (pbr=$pbr)", lit[0].toFloat(), greenBlocked[0].toFloat(), 2f)
                assertTrue("green gone (pbr=$pbr): ${greenBlocked.toList()}", greenBlocked[1] <= 2)
                val redBlocked = scene.draw(DirectionalVisibilityAttribute(dark, 0, worldToMap()))
                assertEquals("green untouched (pbr=$pbr)", lit[1].toFloat(), redBlocked[1].toFloat(), 2f)
                assertTrue("red gone (pbr=$pbr): ${redBlocked.toList()}", redBlocked[0] <= 2)
                val halved = scene.draw(DirectionalVisibilityAttribute(half, 0, worldToMap()))
                assertEquals("red halved in linear light (pbr=$pbr): ${halved.toList()}", lit[0] * 0.5f, halved[0].toFloat(), 4f)
                assertArrayEquals("no attribute draws as before (pbr=$pbr)", lit, scene.draw(null))
            }
        } finally {
            dark.dispose(); half.dispose()
        }
    }

    @Test
    fun beyondTheMapTheOutsideValueAppliesWithASmoothEdge() = TestGl.run {
        val dark = map(0f)
        try {
            for (pbr in listOf(false, true)) Scene(pbr).use { scene ->
                val lit = scene.draw(null)
                // the origin lies 10 world units beyond the 4-unit map: only the outside value is left
                val outsideFull = scene.draw(DirectionalVisibilityAttribute(dark, 0, worldToMap(10f), 0.1f, 1f))
                assertEquals("outside 1 keeps the light (pbr=$pbr)", lit[0].toFloat(), outsideFull[0].toFloat(), 2f)
                val outsideHalf = scene.draw(DirectionalVisibilityAttribute(dark, 0, worldToMap(10f), 0.1f, 0.5f))
                assertEquals("outside 0.5 halves it (pbr=$pbr)", lit[0] * 0.5f, outsideHalf[0].toFloat(), 4f)
                // the origin sits 0.05 of the map from the edge, half way through a 0.1 fade
                val edge = scene.draw(DirectionalVisibilityAttribute(dark, 0, worldToMap(-1.8f), 0.1f, 1f))
                assertTrue("edge is between dark and lit (pbr=$pbr): ${edge.toList()}", edge[0] in 3 until lit[0] - 3)
            }
        } finally {
            dark.dispose()
        }
    }

    @Test
    fun theMapMultipliesALightsAtlasShadowAndALitLightKeepsOnlyTheMap() = TestGl.run {
        val half = map(0.5f)
        // an atlas whose every depth is 0: a light with a record is fully in shadow
        val pixmap = Pixmap(32, 32, Pixmap.Format.RGBA8888).also { it.setColor(0f, 0f, 0f, 0f); it.fill() }
        val atlas = Texture(pixmap)
        try {
            for (pbr in listOf(false, true)) Scene(pbr).use { scene ->
                val lit = scene.draw(null, atlas, redShadowed = false)
                assertTrue("red lit without a record (pbr=$pbr): ${lit.toList()}", lit[0] > 20)
                val shadowed = scene.draw(null, atlas, redShadowed = true)
                assertTrue("red in its atlas shadow (pbr=$pbr): ${shadowed.toList()}", shadowed[0] <= 2)
                assertEquals("green unaffected by red's shadow (pbr=$pbr)", lit[1].toFloat(), shadowed[1].toFloat(), 2f)
                val both = scene.draw(DirectionalVisibilityAttribute(half, 0, worldToMap()), atlas, redShadowed = true)
                assertTrue("shadow and map together are not brighter than the shadow (pbr=$pbr): ${both.toList()}", both[0] <= shadowed[0] + 1)
                assertEquals("green still untouched (pbr=$pbr)", lit[1].toFloat(), both[1].toFloat(), 2f)
                val mapOnly = scene.draw(DirectionalVisibilityAttribute(half, 0, worldToMap()), atlas, redShadowed = false)
                assertEquals("an unshadowed light keeps exactly the map's share (pbr=$pbr)", lit[0] * 0.5f, mapOnly[0].toFloat(), 4f)
            }
        } finally {
            half.dispose(); atlas.dispose(); pixmap.dispose()
        }
    }
}
