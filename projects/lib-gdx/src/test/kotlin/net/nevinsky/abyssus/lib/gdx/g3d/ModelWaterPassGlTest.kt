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
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.RenderableProvider
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.graphics.g3d.Renderable
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import net.nevinsky.abyssus.lib.gdx.shader.ClipPlaneAttribute
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasView
import net.nevinsky.abyssus.lib.gdx.shader.ShadowDepthPass
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightKind
import net.nevinsky.abyssus.lib.gdx.shader.ShadowLightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

private const val SIZE = 32

/**
 * The optional world clip plane and the linear depth pass the water renderer builds on: models clip below a plane in
 * the color and the depth shaders (32-bit indices, skinned meshes and alpha-cutout holes included), and a scene that
 * sets nothing renders exactly as before.
 */
class ModelWaterPassGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    /** A quad (two triangles, 32-bit indices) in the z = 0 plane covering x and y from -0.8 to 0.8. */
    private fun quad(extraAttributes: List<VertexAttribute> = emptyList(), boneIndex: Float? = null): IntMesh {
        val attributes = listOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)) + extraAttributes
        val corners = listOf(-0.8f to -0.8f, 0.8f to -0.8f, 0.8f to 0.8f, -0.8f to 0.8f)
        val uv = listOf(0f to 1f, 1f to 1f, 1f to 0f, 0f to 0f)
        val mesh = IntMesh(true, 4, 6, *attributes.toTypedArray())
        val data = ArrayList<Float>()
        for ((i, c) in corners.withIndex()) {
            data += listOf(c.first, c.second, 0f, 0f, 0f, 1f, uv[i].first, uv[i].second)
            if (boneIndex != null) data += listOf(boneIndex, 1f)
        }
        mesh.setVertices(data.toFloatArray())
        mesh.setIndices(intArrayOf(0, 1, 2, 0, 2, 3))
        return mesh
    }

    private fun renderable(mesh: IntMesh, material: Material, environment: Environment?, bones: Array<Matrix4>? = null): Renderable =
        Renderable().apply {
            meshPart.set("quad", mesh, 0, 6, GL20.GL_TRIANGLES)
            this.material = material
            this.environment = environment
            this.bones = bones
            worldTransform.idt()
        }

    private fun lit(): Environment = Environment().apply {
        add(DirectionalLight().set(Color(0.8f, 0.8f, 0.8f, 1f), 0f, 0f, -1f))
        set(ColorAttribute.createAmbientLight(0.4f, 0.4f, 0.4f, 1f))
    }

    private fun material(pbr: Boolean = false): Material {
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
        if (pbr) material.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
        return material
    }

    private fun orthographic() = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }

    private fun perspective() = PerspectiveCamera(60f, SIZE.toFloat(), SIZE.toFloat()).apply {
        position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update()
    }

    private fun <T> offscreen(clear: Color = Color.BLACK, block: () -> T): T {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true)
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(clear.r, clear.g, clear.b, clear.a)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            Gdx.gl.glDisable(GL20.GL_DITHER)
            return block()
        } finally {
            framebuffer.end()
            framebuffer.dispose()
        }
    }

    private fun readPixels(): ByteArray {
        val buffer = BufferUtils.newByteBuffer(SIZE * SIZE * 4)
        Gdx.gl.glReadPixels(0, 0, SIZE, SIZE, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        return ByteArray(SIZE * SIZE * 4) { buffer.get(it) }
    }

    /** Whether row [y] (0 = bottom of the image) has a lit pixel at column [x]. */
    private fun ByteArray.red(x: Int, y: Int) = this[(y * SIZE + x) * 4].toInt() and 255

    private fun colorPass(renderable: Renderable, provider: ModelShaderProvider): ByteArray = offscreen {
        val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
        context.begin()
        val shader = provider.getShader(renderable)
        shader.begin(orthographic(), context)
        shader.render(renderable)
        shader.end()
        context.end()
        readPixels()
    }

    private fun provider() = ModelShaderProvider(DefaultShader.Config().apply { numPointLights = 0; numSpotLights = 0 })

    @Test fun theClipPlaneRemovesGeometryBelowItInTheColorShaders() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val mesh = quad()
            val provider = provider()
            try {
                val environment = lit().apply { set(ClipPlaneAttribute(0f, 1f, 0f, 0f)) } // keep y >= 0
                val pixels = colorPass(renderable(mesh, material(pbr), environment), provider)
                assertTrue("upper half is drawn (pbr=$pbr)", pixels.red(SIZE / 2, SIZE * 3 / 4) > 20)
                assertEquals("lower half is clipped (pbr=$pbr)", 0, pixels.red(SIZE / 2, SIZE / 4))
            } finally {
                provider.dispose(); mesh.dispose()
            }
        }
    }

    @Test fun theClipPlaneBiasKeepsGeometryJustBelowTheLevel() = TestGl.run {
        val mesh = quad()
        val provider = provider()
        try {
            // keep y >= -0.3: the lower half is still cut, a band below the level stays
            val environment = lit().apply { set(ClipPlaneAttribute(0f, 1f, 0f, 0.3f)) }
            val pixels = colorPass(renderable(mesh, material(), environment), provider)
            assertTrue("inside the band", pixels.red(SIZE / 2, SIZE * 7 / 16) > 20)
            assertEquals("below the band", 0, pixels.red(SIZE / 2, SIZE / 8))
        } finally {
            provider.dispose(); mesh.dispose()
        }
    }

    @Test fun withoutTheAttributeNothingIsClippedAndTheShaderHasNoClipTest() = TestGl.run {
        val mesh = quad()
        val provider = provider()
        try {
            val plain = renderable(mesh, material(), lit())
            val pixels = colorPass(plain, provider)
            assertTrue(pixels.red(SIZE / 2, SIZE * 3 / 4) > 20)
            assertTrue("the lower half renders as before", pixels.red(SIZE / 2, SIZE / 4) > 20)
            val shader = provider.getShader(plain) as DefaultShader
            assertFalse(shader.program.fragmentShaderSource.contains("#define clipPlaneFlag"))
            val clipped = renderable(mesh, material(), lit().apply { set(ClipPlaneAttribute(0f, 1f, 0f, 0f)) })
            assertTrue("a different variant is compiled for the clipped pass", provider.getShader(clipped) !== shader)
            assertTrue("the plain variant still renders plain renderables", provider.getShader(plain) === shader)
        } finally {
            provider.dispose(); mesh.dispose()
        }
    }

    @Test fun anUnlitRenderableWithoutAnEnvironmentClipsThroughItsMaterial() = TestGl.run {
        val mesh = quad()
        val provider = provider()
        try {
            val material = material().apply { set(ClipPlaneAttribute(0f, 1f, 0f, 0f)) }
            val pixels = colorPass(renderable(mesh, material, null), provider)
            assertTrue(pixels.red(SIZE / 2, SIZE * 3 / 4) > 20)
            assertEquals(0, pixels.red(SIZE / 2, SIZE / 4))
        } finally {
            provider.dispose(); mesh.dispose()
        }
    }

    /** Decodes the packed depth of the pixel ([x], [y]) the way the shaders do. */
    private fun ByteArray.depth(x: Int, y: Int): Float {
        val i = (y * SIZE + x) * 4
        fun c(k: Int) = (this[i + k].toInt() and 255) / 255f
        return c(0) / 16581375f + c(1) / 65025f + c(2) / 255f + c(3)
    }

    private fun depthPass(renderable: Renderable, pass: ShadowDepthPass, camera: com.badlogic.gdx.graphics.Camera): ByteArray =
        offscreen(Color.WHITE) {
            val provider = ModelDepthShaderProvider(pass)
            try {
                val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
                context.begin()
                val shader = provider.get(renderable)
                shader.begin(camera, context)
                shader.render(renderable)
                shader.end()
                context.end()
                readPixels()
            } finally {
                provider.dispose()
            }
        }

    @Test fun theDepthPassClipsAndWritesLinearViewDepth() = TestGl.run {
        val mesh = quad()
        try {
            val pass = ShadowDepthPass().apply { linearFar = 4f; clipPlane = floatArrayOf(0f, 1f, 0f, 0f) }
            val pixels = depthPass(renderable(mesh, Material(), null), pass, perspective())
            // the quad is 2 units from the camera, a half of the far distance
            assertEquals(0.5f, pixels.depth(SIZE / 2, SIZE * 3 / 4), 0.002f)
            assertTrue("clipped pixels keep the cleared (empty) depth", pixels.depth(SIZE / 2, SIZE / 4) >= 0.999f)
        } finally {
            mesh.dispose()
        }
    }

    @Test fun theDepthPassDefaultsStayAsTheShadowPassWrote() = TestGl.run {
        val mesh = quad()
        try {
            val pixels = depthPass(renderable(mesh, Material(), null), ShadowDepthPass(), orthographic())
            val covered = pixels.depth(SIZE / 2, SIZE / 4)
            assertTrue("both halves are drawn without a clip plane", covered < 0.99f && pixels.depth(SIZE / 2, SIZE * 3 / 4) < 0.99f)
            assertEquals("window depth of a plane 2 units from an ortho camera with 0.1 / 4", (2f - 0.1f) / 3.9f, covered, 0.01f)
        } finally {
            mesh.dispose()
        }
    }

    @Test fun alphaCutoutHolesStayHolesUnderTheClipPlane() = TestGl.run {
        val pixmap = Pixmap(2, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f); pixmap.drawPixel(0, 0)
        pixmap.setColor(1f, 1f, 1f, 0f); pixmap.drawPixel(1, 0)
        val texture = Texture(pixmap).also { it.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
        val mesh = quad()
        try {
            val material = Material(TextureAttribute.createDiffuse(texture), FloatAttribute.createAlphaTest(0.5f))
            val pass = ShadowDepthPass().apply { linearFar = 4f; clipPlane = floatArrayOf(0f, 1f, 0f, 0f) }
            val pixels = depthPass(renderable(mesh, material, null), pass, perspective())
            assertTrue("the opaque left half writes depth", pixels.depth(SIZE / 4, SIZE * 3 / 4) < 0.99f)
            assertTrue("the cutout right half is a hole", pixels.depth(SIZE * 3 / 4, SIZE * 3 / 4) >= 0.999f)
        } finally {
            mesh.dispose(); texture.dispose(); pixmap.dispose()
        }
    }

    @Test fun skinnedMeshesAreClippedAfterTheirBonesPoseThem() = TestGl.run {
        val bone = VertexAttribute(VertexAttributes.Usage.BoneWeight, 2, "a_boneWeight0", 0)
        val mesh = quad(listOf(bone), boneIndex = 0f)
        try {
            // the bone lifts the quad by 1.0, so what used to be below y = 0 (clipped) is now fully above the plane
            val lifted = arrayOf(Matrix4().setToTranslation(0f, 1.0f, 0f))
            val pass = ShadowDepthPass().apply { linearFar = 4f; clipPlane = floatArrayOf(0f, 1f, 0f, 0f) }
            val posed = depthPass(renderable(mesh, Material(), null, lifted), pass, perspective())
            assertTrue("the posed quad's lower corner is now above the plane and drawn", posed.depth(SIZE / 2, SIZE / 2 + SIZE / 8) < 0.99f)
            val rest = depthPass(renderable(mesh, Material(), null, arrayOf(Matrix4())), pass, perspective())
            assertTrue("unposed, the lower half is clipped", rest.depth(SIZE / 2, SIZE / 4) >= 0.999f)
        } finally {
            mesh.dispose()
        }
    }

    @Test fun theFilterDropsRefusedRenderables() = TestGl.run {
        val mesh = quad()
        val real = provider()
        val asked = mutableListOf<Renderable>()
        val counting = object : ShaderProvider by real {
            override fun getShader(renderable: Renderable): Shader = real.getShader(renderable).also { asked += renderable }
        }
        try {
            val batch = ModelBatch(counting)
            val first = renderable(mesh, material(), lit())
            val second = renderable(mesh, material(), lit())
            fun providerOf(r: Renderable) = RenderableProvider { renderables, _ -> renderables.add(r) }
            batch.begin(orthographic())
            batch.render(FilteredRenderables(providerOf(first)) { it === second }, lit())
            batch.render(FilteredRenderables(providerOf(second)) { it === second }, lit())
            batch.end()
            assertEquals("only the accepted renderable reached the shader provider", listOf(second), asked)
            asked.clear()
            batch.begin(orthographic())
            batch.render(providerOf(first), lit())
            batch.end()
            assertEquals("with no filter everything is drawn", listOf(first), asked)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            real.dispose(); mesh.dispose()
        }
    }
}
