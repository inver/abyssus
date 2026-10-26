/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.gdx.model.PBRTextureAttribute
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.gdx.shader.apply
import net.nevinsky.abyssus.lib.gdx.shader.srgbToLinear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The output transform on a real GL context (opt-in, `-Dabyssus.glTests=true`): without the attribute the default and PBR
 * shaders write what they wrote before the transform existed, and with it they write the Kotlin reference
 * ([OutputTransform.apply]) of the linear light, decoding colour textures but not data textures.
 */
class OutputTransformGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private class Scene : AutoCloseable {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = ModelShaderProvider(DefaultShader.Config().apply { numPointLights = 0; numSpotLights = 0 })
        val mesh = IntMesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 0f, 0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f))
            setIndices(intArrayOf(0, 1, 2))
        }
        val camera = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }
        private val textures = ArrayList<Texture>()

        fun grey(value: Int): Texture = texel(value, value, value)

        fun texel(r: Int, g: Int, b: Int): Texture {
            val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888).also { it.setColor(Color(r / 255f, g / 255f, b / 255f, 1f)); it.fill() }
            return Texture(pixmap).also { pixmap.dispose(); textures += it }
        }

        /** The pixel in the middle of a triangle lit by a white sun head on, no ambient, drawn with [material] and [exposure] (null: no transform). */
        fun draw(material: Material, exposure: Float?, sun: Float = 1f): IntArray {
            val renderable = Renderable().apply {
                meshPart.set("tri", mesh, 0, 3, GL20.GL_TRIANGLES)
                this.material = material
                environment = Environment().apply {
                    add(DirectionalLight().set(Color(sun, sun, sun, 1f), 0f, 0f, -1f))
                    if (exposure != null) set(OutputTransformAttribute(exposure))
                }
            }
            val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 4))
            framebuffer.begin()
            try {
                Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                context.begin()
                val shader = provider.getShader(renderable)
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
            provider.dispose(); framebuffer.dispose(); mesh.dispose(); textures.forEach(Texture::dispose)
        }
    }

    private fun material(scene: Scene, albedo: Int, pbr: Boolean): Material =
        Material(TextureAttribute.createDiffuse(scene.grey(albedo))).also {
            if (pbr) it.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
        }

    private fun expected(light: Float, exposure: Float): IntArray =
        OutputTransform(exposure).apply(floatArrayOf(light, light, light)).map { Math.round(it * 255f) }.toIntArray()

    /** What the shaders wrote before the transform existed: a white sun head on, textures of 255, 128 and 0. */
    @Test
    fun withoutTheAttributeTheShadersWriteWhatTheyAlwaysDid() = TestGl.run {
        Scene().use { scene ->
            for (pbr in listOf(false, true)) {
                val seen = listOf(255, 128, 0).map { scene.draw(material(scene, it, pbr), null)[0] }
                assertEquals("pbr=$pbr: $seen", REFERENCE_OFF.getValue(pbr), seen)
            }
        }
    }

    /** Albedo 255 and 0 decode to themselves, so the lit light of the flag-off draw is the linear light the transform gets. */
    @Test
    fun withTheAttributeTheShadersWriteTheReferenceOfTheLinearLight() = TestGl.run {
        Scene().use { scene ->
            for (pbr in listOf(false, true)) for (exposure in listOf(1f, 2f)) {
                val white = scene.draw(material(scene, 255, pbr), null)[0] / 255f
                val black = scene.draw(material(scene, 0, pbr), null)[0] / 255f
                // lit light is linear in albedo: the part that scales with it, and a constant (the specular of a non-metal)
                val albedo = srgbToLinear(128 / 255f)
                val light = albedo * (white - black) + black
                val want = expected(light, exposure)
                val got = scene.draw(material(scene, 128, pbr), exposure)
                for (c in 0..2) assertEquals("pbr=$pbr exposure=$exposure want=${want.toList()} got=${got.toList()}", want[c].toFloat(), got[c].toFloat(), 3f)
            }
        }
    }

    /** A white albedo decodes to itself, so only the transform differs from the flag-off draw. */
    @Test
    fun aWhiteSurfaceIsTheTransformOfTheUntransformedLight() = TestGl.run {
        Scene().use { scene ->
            for (pbr in listOf(false, true)) {
                val off = scene.draw(material(scene, 255, pbr), null)[0] / 255f
                val want = expected(off, 1f)
                val got = scene.draw(material(scene, 255, pbr), 1f)
                for (c in 0..2) assertEquals("pbr=$pbr channel $c", want[c].toFloat(), got[c].toFloat(), 2f)
            }
        }
    }

    /** A metallic-roughness texel of 0.5 stays 0.5 under the transform: decoding it would change the metal's diffuse a lot. */
    @Test
    fun dataTexturesAreNotDecoded() = TestGl.run {
        Scene().use { scene ->
            // roughness (G) stays 1 either way; metallic (B) is half stored, a fifth if it were decoded
            fun metal(blue: Int = 128): Material = Material(
                TextureAttribute.createDiffuse(scene.grey(255)), PBRFloatAttribute.createMetallic(1f), PBRFloatAttribute.createRoughness(1f),
                PBRTextureAttribute(PBRTextureAttribute.MetallicRoughnessTexture, TextureDescriptor(scene.texel(0, 255, blue))),
            )
            val off = scene.draw(metal(), null, sun = 0.4f)[0] / 255f
            val want = expected(off, 1f)
            val got = scene.draw(metal(), 1f, sun = 0.4f)
            assertEquals("metal=${off}: want ${want.toList()} got ${got.toList()}", want[0].toFloat(), got[0].toFloat(), 2f)
            // had the texel been decoded (128 -> 55), the metal would light differently: the test can tell the two apart
            val decoded = expected(scene.draw(metal(55), null, sun = 0.4f)[0] / 255f, 1f)
            assertTrue("decoded and stored texels light alike: ${decoded.toList()} ${want.toList()}", Math.abs(decoded[0] - want[0]) > 6)
        }
    }

    private companion object {
        /** The red channel of the white, grey (128) and black albedo draws, recorded from the shaders before this change. */
        val REFERENCE_OFF: Map<Boolean, List<Int>> = mapOf(false to listOf(255, 128, 0), true to listOf(247, 125, 2))
    }
}
