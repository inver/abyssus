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
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/** A directional light's visibility map in the default and PBR shaders, read back. Opt-in: `-Dabyssus.glTests=true`. */
class DirectionalVisibilityGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    /** The map covers world x and y from -1 to 1 (uv = (p + 1) / 2) unless [shiftedBy] moves it away. */
    private fun worldToMap(shiftedBy: Float = 0f) = Matrix4().setToTranslationAndScaling(0.5f + shiftedBy, 0.5f, 0f, 0.5f, 0.5f, 1f)

    /**
     * The centre pixel (red, green) of a white triangle lit by a red directional light 0 and a green one 1, with the map
     * [attribute] (null: none). [mapValue] is the map's red channel.
     */
    private fun pixel(pbr: Boolean, mapValue: Float, attribute: ((GLTexture) -> DirectionalVisibilityAttribute)?): IntArray {
        val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0f, 0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f))
            setIndices(intArrayOf(0, 1, 2))
        }
        val mapPixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888).also { it.setColor(mapValue, 0f, 0f, 1f); it.fill() }
        val map = Texture(mapPixmap).also {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            it.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0; numDirectionalLights = 2 })
        val camera = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }
        val environment = Environment().apply {
            add(DirectionalLight().set(Color(0.8f, 0f, 0f, 1f), 0f, 0f, -1f))
            add(DirectionalLight().set(Color(0f, 0.8f, 0f, 1f), 0f, 0f, -1f))
            attribute?.let { set(it(map)) }
        }
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
        if (pbr) material.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
        val renderable = Renderable().apply {
            meshPart.set("receiver", mesh, 0, 3, GL20.GL_TRIANGLES); this.material = material; this.environment = environment
        }
        val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            context.begin()
            val shader = provider.get(renderable)!!
            shader.begin(camera, context); shader.render(renderable); shader.end(); context.end()
            val px = BufferUtils.newByteBuffer(4)
            Gdx.gl.glReadPixels(16, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            return intArrayOf(px[0].toInt() and 255, px[1].toInt() and 255)
        } finally {
            framebuffer.end(); provider.dispose(); framebuffer.dispose(); map.dispose(); mapPixmap.dispose(); mesh.dispose()
        }
    }

    @Test
    fun onlyTheIndexedLightIsMultipliedByTheMap() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val plain = pixel(pbr, 0f, null)
            assertTrue("both lights light the surface (pbr=$pbr): ${plain.toList()}", plain[0] > 20 && plain[1] > 20)

            val greenDark = pixel(pbr, 0f) { DirectionalVisibilityAttribute(it, 1, worldToMap()) }
            assertTrue("light 1 is dark (pbr=$pbr): ${greenDark.toList()}", greenDark[1] <= 2)
            assertTrue("light 0 is unchanged (pbr=$pbr): ${greenDark.toList()} vs ${plain.toList()}", abs(greenDark[0] - plain[0]) <= 2)

            val redDark = pixel(pbr, 0f) { DirectionalVisibilityAttribute(it, 0, worldToMap()) }
            assertTrue("light 0 is dark (pbr=$pbr): ${redDark.toList()}", redDark[0] <= 2)
            assertTrue("light 1 is unchanged (pbr=$pbr): ${redDark.toList()} vs ${plain.toList()}", abs(redDark[1] - plain[1]) <= 2)

            val half = pixel(pbr, 0.5f) { DirectionalVisibilityAttribute(it, 1, worldToMap()) }
            assertTrue("a half map halves light 1 (pbr=$pbr): ${half.toList()} of ${plain.toList()}", abs(half[1] * 2 - plain[1]) <= plain[1] * 0.15 + 3)
            assertTrue("light 0 is unchanged by it (pbr=$pbr)", abs(half[0] - plain[0]) <= 2)
        }
    }

    @Test
    fun outsideTheMapTheFallbackVisibilityApplies() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val plain = pixel(pbr, 0f, null)
            // moved a whole map width away, the fragment is outside the map, which would be dark: the fallback shows instead
            val outside = pixel(pbr, 0f) { DirectionalVisibilityAttribute(it, 1, worldToMap(3f), 0.1f, 0.25f) }
            assertTrue("outside is a quarter (pbr=$pbr): ${outside.toList()} of ${plain.toList()}", abs(outside[1] * 4 - plain[1]) <= plain[1] * 0.2 + 4)
            val lit = pixel(pbr, 0f) { DirectionalVisibilityAttribute(it, 1, worldToMap(3f)) }
            assertTrue("the default fallback is fully lit (pbr=$pbr)", abs(lit[1] - plain[1]) <= 2)
        }
    }

    @Test
    fun theMapFadesIntoTheFallbackAtItsEdge() = TestGl.run {
        // the fragment sits at uv x = 0.5; moving the map by 0.45 puts it 0.05 from the edge, half way through a 0.1 fade
        for (pbr in listOf(false, true)) {
            val plain = pixel(pbr, 0f, null)
            val edge = pixel(pbr, 0f) { DirectionalVisibilityAttribute(it, 1, worldToMap(-0.45f), 0.1f, 1f) }
            assertTrue("part way between dark map and lit outside (pbr=$pbr): ${edge.toList()} of ${plain.toList()}", edge[1] > plain[1] * 0.2 && edge[1] < plain[1] * 0.8)
        }
    }

    @Test
    fun theAttributeComparesAndCopiesByContent() {
        val texture = object : GLTexture(GL20.GL_TEXTURE_2D, 5) {
            override fun getWidth() = 1
            override fun getHeight() = 1
            override fun getDepth() = 0
            override fun isManaged() = false
            override fun reload() = Unit
        }
        val a = DirectionalVisibilityAttribute(texture, 1, worldToMap(), 0.1f, 0.5f)
        assertEquals(a, a.copy())
        assertEquals(0, a.compareTo(a.copy()))
        assertTrue(a != DirectionalVisibilityAttribute(texture, 0, worldToMap(), 0.1f, 0.5f))
        assertTrue(a != DirectionalVisibilityAttribute(texture, 1, worldToMap(), 0.1f, 0.6f))
        // a visibility map is its own shader variant, so models with and without it never share a program
        assertTrue(Environment().getMask() != Environment().apply { set(a) }.getMask())
    }
}
