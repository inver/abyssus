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
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The default and PBR shaders mixing two sky environments, read back. Opt-in: `-Dabyssus.glTests=true`. */
class EnvironmentFadeGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private fun cube(r: Float, g: Float, b: Float): Cubemap {
        val face = Pixmap(4, 4, Pixmap.Format.RGBA8888).also { it.setColor(r, g, b, 1f); it.fill() }
        return Cubemap(face, face, face, face, face, face)
    }

    private fun axes(r: Float, g: Float, b: Float) = FloatArray(18) { floatArrayOf(r, g, b)[it % 3] }

    /** The centre pixel of a white, unlit-but-for-the-sky triangle with [pbr] shading; [fade] 0, 0.5, 1, or null: no next environment. */
    private fun pixel(pbr: Boolean, fade: Float?): IntArray {
        val red = cube(0.5f, 0f, 0f)
        val blue = cube(0f, 0f, 0.5f)
        val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0f, 0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f))
            setIndices(intArrayOf(0, 1, 2))
        }
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0; numDirectionalLights = 0 })
        val camera = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }
        val next = fade?.let { EnvironmentLightAttribute.Next(blue, blue, axes(0f, 0f, 0.5f)) }
        val environment = Environment().apply {
            set(EnvironmentLightAttribute(red, red, 1, axes(0.5f, 0f, 0f), next, fade ?: 0f))
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
            return IntArray(3) { px[it].toInt() and 255 }
        } finally {
            framebuffer.end(); provider.dispose(); framebuffer.dispose(); red.dispose(); blue.dispose(); mesh.dispose()
        }
    }

    @Test
    fun bothShadersMixTheTwoEnvironmentsByTheWeight() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val start = pixel(pbr, 0f)
            val half = pixel(pbr, 0.5f)
            val end = pixel(pbr, 1f)
            assertTrue("weight 0 is the current (red) sky, pbr=$pbr: ${start.toList()}", start[0] > 20 && start[2] <= 2)
            assertTrue("weight 1 is the next (blue) sky, pbr=$pbr: ${end.toList()}", end[2] > 20 && end[0] <= 2)
            assertTrue("weight 0.5 has both, pbr=$pbr: ${half.toList()}", half[0] > 8 && half[2] > 8)
            assertTrue("weight 0.5 is about half of each, pbr=$pbr: ${half.toList()} of ${start.toList()}", kotlin.math.abs(half[0] * 2 - start[0]) <= start[0] * 0.3 + 3)
        }
    }

    @Test
    fun withoutANextEnvironmentNothingIsMixed() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val plain = pixel(pbr, null)
            val start = pixel(pbr, 0f)
            assertTrue("pbr=$pbr: ${plain.toList()} vs ${start.toList()}", plain[0] > 20 && plain[2] <= 2)
            assertEquals(plain.toList(), start.toList())
        }
    }
}
