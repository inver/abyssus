/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.core.assets.loading.BuiltAssets
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.terrain.PreparedTerrain
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.gdx.g3d.ModelShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransform
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.gdx.shader.apply
import net.nevinsky.abyssus.lib.gdx.shader.srgbToLinear
import net.nevinsky.abyssus.lib.render.frame.FrameInputs
import net.nevinsky.abyssus.lib.render.light.DirectionalInput
import net.nevinsky.abyssus.lib.render.light.LightProfile
import net.nevinsky.abyssus.lib.render.light.RenderLights
import net.nevinsky.abyssus.lib.render.terrain.SunShadowInput
import net.nevinsky.abyssus.lib.render.terrain.TerrainDraw
import net.nevinsky.abyssus.lib.render.terrain.TerrainLighting
import net.nevinsky.abyssus.lib.render.terrain.TerrainPass
import net.nevinsky.abyssus.lib.render.terrain.TerrainVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The game terrain under the output transform (opt-in, `-Dabyssus.glTests=true`): a terrain and a model of equal albedo lit
 * the same way come out alike, a shadow darkens the terrain, a sky's irradiance replaces the flat ambient, and the layers
 * are decoded from sRGB.
 */
class TerrainOutputTransformGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val size = 32
    private val towardSun = Vector3(0.3f, 0.8f, 0.4f).nor()
    private val stored = 0.6f

    private fun solid(value: Float, r: Float = value, g: Float = value, b: Float = value): Texture {
        val pixmap = Pixmap(2, 2, Pixmap.Format.RGBA8888).also { it.setColor(r, g, b, 1f); it.fill() }
        return Texture(pixmap).also { pixmap.dispose() }
    }

    private fun mesh(base: Texture) =
        TerrainMesh(PreparedTerrain(TerrainData(2, FloatArray(4), 10, 1f), mapOf("splatBase" to "base")), BuiltAssets { if (it == "base") base else null })

    private fun camera() = PerspectiveCamera(60f, size.toFloat(), size.toFloat()).apply {
        position.set(5f, 8f, 5f); lookAt(5f, 0f, 5.001f); near = 0.1f; far = 50f; update()
    }

    private fun frame(camera: PerspectiveCamera, output: OutputTransform?, ambient: Float = 0.2f) = FrameInputs(
        camera.combined, camera.position, floatArrayOf(ambient, ambient, ambient), null,
        RenderLights(listOf(DirectionalInput("sun", Vector3(towardSun).scl(-1f), 0.7f, 0.6f, 0.5f))),
        LightProfile.GAME_SINGLE_SUN, output,
    )

    private fun centre(framebuffer: FrameBuffer, draw: () -> Unit): IntArray {
        framebuffer.begin()
        try {
            Gdx.gl.glViewport(0, 0, size, size)
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            draw()
            val pixel = BufferUtils.newByteBuffer(4)
            Gdx.gl.glReadPixels(size / 2, size / 2, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            return IntArray(3) { pixel[it].toInt() and 255 }
        } finally {
            framebuffer.end()
        }
    }

    /** The shader the models of a game use, on a flat quad over the terrain's extent, lit by the same sun and ambient. */
    private fun modelPixel(framebuffer: FrameBuffer, camera: PerspectiveCamera, albedo: Texture, exposure: Float): IntArray {
        val provider = ModelShaderProvider(DefaultShader.Config().apply { numPointLights = 0; numSpotLights = 0 })
        val quad = Mesh(true, 4, 6, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0.5f, 0.5f, 10f, 0f, 0f, 0f, 1f, 0f, 0.5f, 0.5f, 10f, 0f, 10f, 0f, 1f, 0f, 0.5f, 0.5f, 0f, 0f, 10f, 0f, 1f, 0f, 0.5f, 0.5f))
            setIndices(shortArrayOf(0, 1, 2, 0, 2, 3))
        }
        try {
            val renderable = Renderable().apply {
                meshPart.set("quad", quad, 0, 6, GL20.GL_TRIANGLES)
                material = Material(TextureAttribute.createDiffuse(albedo), IntAttribute.createCullFace(GL20.GL_NONE))
                environment = Environment().apply {
                    set(ColorAttribute(ColorAttribute.AmbientLight, 0.2f, 0.2f, 0.2f, 1f))
                    add(DirectionalLight().set(Color(0.7f, 0.6f, 0.5f, 1f), -towardSun.x, -towardSun.y, -towardSun.z))
                    set(OutputTransformAttribute(exposure))
                }
            }
            val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 4))
            return centre(framebuffer) {
                context.begin()
                val shader = provider.getShader(renderable)
                shader.begin(camera, context); shader.render(renderable); shader.end(); context.end()
            }
        } finally {
            quad.dispose(); provider.dispose()
        }
    }

    @Test
    fun aTerrainAndAModelOfEqualAlbedoLitTheSameWayComeOutWithinFivePercent() = TestGl.run {
        val base = solid(stored)
        val mesh = mesh(base)
        val camera = camera()
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
        val pass = TerrainPass(ShaderStorage(), TerrainVariant.GAME_SINGLE_SUN)
        try {
            val output = OutputTransform(1f)
            val terrain = centre(framebuffer) {
                pass.draw(frame(camera, output), TerrainLighting.Sun(towardSun, 0.7f, 0.6f, 0.5f), listOf(TerrainDraw(mesh, Matrix4())))
            }
            val model = modelPixel(framebuffer, camera, base, 1f)
            for (c in 0..2) {
                assertTrue("channel $c: terrain ${terrain.toList()} model ${model.toList()}", terrain[c] > 40)
                assertEquals("channel $c: terrain ${terrain.toList()} model ${model.toList()}", model[c].toFloat(), terrain[c].toFloat(), model[c] * 0.05f + 1f)
            }
        } finally {
            pass.dispose(); framebuffer.dispose(); mesh.dispose(); base.dispose()
        }
    }

    @Test
    fun theLayerIsDecodedAndTheLightEndsInTheTransform() = TestGl.run {
        val base = solid(stored)
        val mesh = mesh(base)
        val camera = camera()
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
        val pass = TerrainPass(ShaderStorage(), TerrainVariant.GAME_SINGLE_SUN)
        try {
            val sun = TerrainLighting.Sun(towardSun, 0.7f, 0.6f, 0.5f)
            val draws = listOf(TerrainDraw(mesh, Matrix4()))
            val got = centre(framebuffer) { pass.draw(frame(camera, OutputTransform(1f)), sun, draws) }
            // the terrain is flat, so its normal is up
            val albedo = srgbToLinear(Math.round(stored * 255f) / 255f)
            val n = towardSun.y
            val want = OutputTransform(1f).apply(floatArrayOf(albedo * (0.2f + 0.7f * n), albedo * (0.2f + 0.6f * n), albedo * (0.2f + 0.5f * n)))
            for (c in 0..2) assertEquals("channel $c", want[c] * 255f, got[c].toFloat(), 3f)
        } finally {
            pass.dispose(); framebuffer.dispose(); mesh.dispose(); base.dispose()
        }
    }

    @Test
    fun aShadowDarkensTheTerrain() = TestGl.run {
        val base = solid(stored)
        val mesh = mesh(base)
        val camera = camera()
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
        val pass = TerrainPass(ShaderStorage(), TerrainVariant.GAME_SINGLE_SUN)
        // packed depth: the alpha channel is the whole part, so 255 is "far" (the sun reaches) and 0 is "at the light" (shadowed)
        val far = Texture(Pixmap(2, 2, Pixmap.Format.RGBA8888).also { it.setColor(0f, 0f, 0f, 1f); it.fill() })
        val near = Texture(Pixmap(2, 2, Pixmap.Format.RGBA8888).also { it.setColor(0f, 0f, 0f, 0f); it.fill() })
        try {
            val shadowMatrix = Matrix4().setToScaling(0.05f, 0.05f, 0.05f)
            fun draw(map: Texture) = centre(framebuffer) {
                val sun = TerrainLighting.Sun(towardSun, 0.7f, 0.6f, 0.5f, SunShadowInput(map, shadowMatrix, 0.5f, 0.001f))
                pass.draw(frame(camera, OutputTransform(1f)), sun, listOf(TerrainDraw(mesh, Matrix4())))
            }
            val lit = draw(far)
            val shadowed = draw(near)
            assertTrue("lit ${lit.toList()} shadowed ${shadowed.toList()}", shadowed[0] < lit[0] - 20)
            assertTrue("a shadowed terrain still shows the ambient: ${shadowed.toList()}", shadowed[0] > 20)
        } finally {
            pass.dispose(); framebuffer.dispose(); mesh.dispose(); base.dispose(); far.dispose(); near.dispose()
        }
    }

    @Test
    fun aSkyIrradianceReplacesTheFlatAmbient() = TestGl.run {
        val base = solid(stored)
        val mesh = mesh(base)
        val camera = camera()
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, size, size, true)
        val pass = TerrainPass(ShaderStorage(), TerrainVariant.GAME_SINGLE_SUN)
        val faces = Array(6) { Pixmap(2, 2, Pixmap.Format.RGBA8888).also { p -> p.setColor(0.5f, 0.5f, 0.5f, 1f); p.fill() } }
        val cube = Cubemap(faces[0], faces[1], faces[2], faces[3], faces[4], faces[5])
        try {
            // no sun: only the ambient lights the terrain
            val dark = TerrainLighting.Sun(towardSun, 0f, 0f, 0f, irradiance = cube)
            val flat = TerrainLighting.Sun(towardSun, 0f, 0f, 0f)
            val draws = listOf(TerrainDraw(mesh, Matrix4()))
            val withSky = centre(framebuffer) { pass.draw(frame(camera, OutputTransform(1f), ambient = 0.1f), dark, draws) }
            val withoutSky = centre(framebuffer) { pass.draw(frame(camera, OutputTransform(1f), ambient = 0.1f), flat, draws) }
            val albedo = srgbToLinear(Math.round(stored * 255f) / 255f)
            val wantSky = OutputTransform(1f).apply(floatArrayOf(albedo * 0.5f, albedo * 0.5f, albedo * 0.5f))
            val wantFlat = OutputTransform(1f).apply(floatArrayOf(albedo * 0.1f, albedo * 0.1f, albedo * 0.1f))
            assertEquals(wantSky[0] * 255f, withSky[0].toFloat(), 4f)
            assertEquals(wantFlat[0] * 255f, withoutSky[0].toFloat(), 4f)
            assertTrue(withSky[0] > withoutSky[0] + 20)
        } finally {
            pass.dispose(); framebuffer.dispose(); mesh.dispose(); base.dispose(); cube.dispose(); faces.forEach(Pixmap::dispose)
        }
    }
}
