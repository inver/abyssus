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
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import net.nevinsky.abyssus.lib.gdx.shader.LOCAL_LIGHT_MAX
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasView
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightKind
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The full 256 point and 256 spot lights of a model shader, with the shadow data of the last light: the shader must
 * link where 512 uniform structs would not, and the last light of each kind must still light and shadow the receiver.
 */
class ManyLightsGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val last = LOCAL_LIGHT_MAX - 1

    @Test
    fun theLastPointAndSpotLightsOfTwoHundredFiftySixLightUpTheReceiver() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val dark = render(pbr, pointOn = false, spotOn = false, shadowPoint = false)
            assertTrue("no light is on ($pbr): ${dark.toList()}", dark[0] < 3 && dark[1] < 3)
            val lit = render(pbr, pointOn = true, spotOn = true, shadowPoint = false)
            assertTrue("the last point light is red ($pbr): ${lit.toList()}", lit[0] > 10)
            assertTrue("the last spot light is green ($pbr): ${lit.toList()}", lit[1] > 10)
        }
    }

    @Test
    fun theLastPointLightCanHaveAShadowWhileTheSpotLightDoesNot() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val lit = render(pbr, pointOn = true, spotOn = true, shadowPoint = false)
            val shadowed = render(pbr, pointOn = true, spotOn = true, shadowPoint = true)
            assertTrue("the point light lost its light ($pbr): ${lit.toList()} / ${shadowed.toList()}", shadowed[0] + 10 < lit[0])
            assertTrue("the spot light is unaffected ($pbr)", kotlin.math.abs(lit[1] - shadowed[1]) <= 2)
        }
    }

    private fun render(pbr: Boolean, pointOn: Boolean, spotOn: Boolean, shadowPoint: Boolean): IntArray {
        val mesh =
            IntMesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0))
                .apply {
                    setVertices(
                        floatArrayOf(
                            -0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f,
                            0.8f, -0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f,
                            0f, 0.8f, 0f, 0f, 0f, 1f, 0.5f, 0.5f,
                        )
                    )
                    setIndices(intArrayOf(0, 1, 2))
                }
        val camera =
            OrthographicCamera(2f, 2f).apply {
                position.set(0f, 0f, 2f)
                lookAt(0f, 0f, 0f)
                near = 0.1f
                far = 4f
                update()
            }
        val map = Pixmap(32, 32, Pixmap.Format.RGBA8888).also {
            it.setColor(0f, 0f, 0f, 0f)
            it.fill()
        }
        val atlasTexture = Texture(map)
        val pointLights = List(LOCAL_LIGHT_MAX) { index ->
            // only the last light has a range; the rest are skipped by the shader
            PointLight().set(
                Color.RED,
                Vector3(0f, 0f, 1f),
                if (index == last && pointOn) 10f else 0f,
            )
        }
        val spotLights = List(LOCAL_LIGHT_MAX) { index ->
            SpotLight().set(
                Color.GREEN,
                Vector3(0f, 0f, 1f),
                Vector3(0f, 0f, -1f),
                if (index == last && spotOn) 10f else 0f,
                45f,
                0.2f,
            )
        }
        val environment =
            Environment().apply {
                pointLights.forEach { add(it) }
                spotLights.forEach { add(it) }
                if (shadowPoint) {
                    val view = ShadowAtlasView(camera.combined.cpy(), Vector4(0f, 0f, 1f, 1f))
                    set(
                        ShadowAtlasAttribute(
                            atlasTexture,
                            listOf(
                                ShadowLightRecord(
                                    "last-point",
                                    ShadowLightKind.POINT,
                                    pointLights[last],
                                    List(6) { view },
                                    Vector3(0f, 0f, 1f),
                                    4f,
                                )
                            ),
                        )
                    )
                }
            }
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
        if (pbr) {
            material.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
        }
        val renderable =
            Renderable().apply {
                meshPart.set("receiver", mesh, 0, 3, GL20.GL_TRIANGLES)
                this.material = material
                this.environment = environment
            }
        val provider =
            ModelShaderProvider(
                DefaultShader.Config().apply {
                    numPointLights = LOCAL_LIGHT_MAX
                    numSpotLights = LOCAL_LIGHT_MAX
                }
            )
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            context.begin()
            val shader = provider.getShader(renderable)
            shader.begin(camera, context)
            shader.render(renderable)
            shader.end()
            context.end()
            val pixel = BufferUtils.newByteBuffer(4)
            Gdx.gl.glReadPixels(16, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            return IntArray(3) { pixel[it].toInt() and 255 }
        } finally {
            framebuffer.end()
            provider.dispose()
            framebuffer.dispose()
            atlasTexture.dispose()
            map.dispose()
            mesh.dispose()
        }
    }
}
