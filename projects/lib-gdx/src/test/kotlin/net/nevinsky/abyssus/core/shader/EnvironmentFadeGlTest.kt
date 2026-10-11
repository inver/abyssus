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
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The cross-fade between two sky environments in the default and PBR shaders, by image. */
class EnvironmentFadeGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private fun solid(r: Float, g: Float, b: Float): Cubemap {
        val pixmap = Pixmap(4, 4, Pixmap.Format.RGBA8888).also { it.setColor(r, g, b, 1f); it.fill() }
        return Cubemap(pixmap, pixmap, pixmap, pixmap, pixmap, pixmap).also { pixmap.dispose() }
    }

    @Test
    fun weightsZeroHalfAndOneBlendBetweenTheEnvironmentsInBothShaders() = TestGl.run {
        val a = solid(0f, 0f, 0.4f)
        val b = solid(0.6f, 0f, 0f)
        val colorsA = FloatArray(18) { if (it % 3 == 2) 0.4f else 0f }
        val colorsB = FloatArray(18) { if (it % 3 == 0) 0.6f else 0f }
        val target = EnvironmentLightAttribute(b, b, 1, colorsB)
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0 })
        val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0f, 0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f))
            setIndices(intArrayOf(0, 1, 2))
        }
        val camera = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }
        try {
            for (pbr in listOf(false, true)) {
                fun draw(sky: EnvironmentLightAttribute): IntArray {
                    val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
                    if (pbr) material.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
                    val renderable = Renderable().apply {
                        meshPart.set("tri", mesh, 0, 3, GL20.GL_TRIANGLES)
                        this.material = material
                        environment = Environment().apply { set(sky) }
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

                fun faded(weight: Float) = draw(EnvironmentLightAttribute(a, a, 1, colorsA, target, weight))
                val onlyA = draw(EnvironmentLightAttribute(a, a, 1, colorsA))
                val onlyB = draw(target)
                val w0 = faded(0f)
                val half = faded(0.5f)
                val w1 = faded(1f)
                val again = draw(EnvironmentLightAttribute(a, a, 1, colorsA))
                assertTrue("environments differ (pbr=$pbr): ${onlyA.toList()} / ${onlyB.toList()}", onlyA[2] > 20 && onlyB[0] > 20)
                assertArrayEquals("weight 0 is the first environment (pbr=$pbr)", onlyA, w0)
                assertArrayEquals("weight 1 is the second environment (pbr=$pbr)", onlyB, w1)
                assertArrayEquals("the non-fading path is unchanged after a fade (pbr=$pbr)", onlyA, again)
                for (c in 0..2) {
                    val mean = (onlyA[c] + onlyB[c]) / 2f
                    assertEquals("channel $c at weight 0.5 (pbr=$pbr): ${half.toList()}", mean, half[c].toFloat(), 3f)
                }
            }
        } finally {
            provider.dispose(); framebuffer.dispose(); mesh.dispose(); a.dispose(); b.dispose()
        }
    }
}
