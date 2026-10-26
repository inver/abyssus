/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import net.nevinsky.abyssus.lib.gdx.shader.OutputTransformAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowDepthPass
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * The instanced variants take the world matrix from the mesh's `mat4 a_instance` data (four attribute locations bound
 * by the stock instance buffer). One instanced draw of several copies must produce the same pixels as the same copies
 * drawn one renderable at a time with their own `worldTransform`.
 */
class InstancedModelShaderGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    @Test fun instancedDrawMatchesOneRenderablePerInstance() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val separate = render(pbr, instanced = false)
            val instanced = render(pbr, instanced = true)
            assertTrue("the comparison must see lit pixels (pbr=$pbr)", separate.any { (it.toInt() and 255) > 20 })
            assertPixelsClose("color pass (pbr=$pbr)", separate, instanced)
        }
    }

    /** The instanced programs compile the output transform too: foliage and instanced models are drawn through them. */
    @Test fun instancedDrawMatchesOneRenderablePerInstanceUnderTheOutputTransform() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val separate = render(pbr, instanced = false, exposure = 1.3f)
            val instanced = render(pbr, instanced = true, exposure = 1.3f)
            val untransformed = render(pbr, instanced = true)
            assertTrue("the comparison must see lit pixels (pbr=$pbr)", separate.any { (it.toInt() and 255) > 20 })
            assertPixelsClose("output transform (pbr=$pbr)", separate, instanced)
            assertTrue("the transform must change the image (pbr=$pbr)", !untransformed.contentEquals(instanced))
        }
    }

    @Test fun instancedDepthPassMatchesOneRenderablePerInstance() = TestGl.run {
        val separate = renderDepth(instanced = false)
        val instanced = renderDepth(instanced = true)
        assertTrue("the comparison must see written depth", separate.any { (it.toInt() and 255) in 1..254 })
        assertPixelsClose("depth pass", separate, instanced)
    }

    @Test fun theProviderPicksTheVariantMatchingTheMesh() = TestGl.run {
        val provider = ModelShaderProvider(DefaultShader.Config().apply { numPointLights = 0; numSpotLights = 0 })
        val plain = triangle()
        val instancedMesh = triangleInstanced()
        try {
            val plainShader = provider.getShader(renderable(plain, Matrix4(), material(false), environment())) as ModelShader
            val instancedShader = provider.getShader(renderable(instancedMesh, Matrix4(), material(false), environment())) as ModelShader
            assertFalse("a plain mesh keeps the uniform variant", plainShader.instanced)
            assertTrue("an instanced mesh gets the instanced variant", instancedShader.instanced)
            assertTrue(instancedShader.canRender(renderable(instancedMesh, Matrix4(), material(false), environment())))
            assertFalse("the instanced variant must not claim a plain mesh",
                instancedShader.canRender(renderable(plain, Matrix4(), material(false), environment())))
            assertFalse(plainShader.canRender(renderable(instancedMesh, Matrix4(), material(false), environment())))
        } finally {
            provider.dispose(); plain.dispose(); instancedMesh.dispose()
        }
    }

    /** The copies: translations plus one rotated and non-uniformly scaled copy, which pins the normal matrix down. */
    private val transforms: List<Matrix4> = listOf(
        Matrix4().setToTranslation(-0.55f, -0.35f, 0f),
        Matrix4().setToTranslation(0.45f, -0.3f, 0.2f)
            .mul(Matrix4().setToRotation(Vector3.Z, 45f)).mul(Matrix4().setToScaling(0.8f, 1.2f, 1f)),
        Matrix4().setToTranslation(0f, 0.55f, -0.3f)
    )

    private fun triangle(): IntMesh =
        IntMesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(
                floatArrayOf(
                    -0.4f, -0.4f, 0f, 0f, 0f, 1f, 0f, 0f,
                    0.4f, -0.4f, 0f, 0f, 0f, 1f, 1f, 0f,
                    0f, 0.4f, 0f, 0f, 0f, 1f, 0.5f, 1f
                )
            )
            setIndices(intArrayOf(0, 1, 2))
        }

    private fun triangleInstanced(): IntMesh = triangle().also { mesh ->
        mesh.enableInstancedRendering(true, transforms.size, *InstanceAttributes.of())
        val data = FloatArray(transforms.size * InstanceAttributes.FLOATS)
        transforms.forEachIndexed { i, matrix -> InstanceAttributes.write(matrix, data, i * InstanceAttributes.FLOATS) }
        mesh.setInstanceData(data)
    }

    private fun renderable(mesh: IntMesh, transform: Matrix4, material: Material, environment: Environment): Renderable =
        Renderable().apply {
            meshPart.set("copy", mesh, 0, 3, GL20.GL_TRIANGLES)
            this.material = material
            this.environment = environment
            worldTransform.set(transform)
        }

    private fun material(pbr: Boolean): Material {
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE))
        if (pbr) material.set(PBRFloatAttribute.createMetallic(0f), PBRFloatAttribute.createRoughness(1f))
        return material
    }

    private fun environment(exposure: Float? = null): Environment = Environment().apply {
        add(DirectionalLight().set(Color(0.8f, 0.8f, 0.8f, 1f), 0f, 0f, -1f))
        set(ColorAttribute.createAmbientLight(0.25f, 0.25f, 0.25f, 1f))
        if (exposure != null) set(OutputTransformAttribute(exposure))
    }

    private fun camera(): OrthographicCamera =
        OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }

    private fun render(pbr: Boolean, instanced: Boolean, exposure: Float? = null): ByteArray {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = ModelShaderProvider(DefaultShader.Config().apply { numPointLights = 0; numSpotLights = 0 })
        val material = material(pbr)
        val environment = environment(exposure)
        var mesh: IntMesh? = null
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 16))
            context.begin()
            if (instanced) {
                val instancedMesh = triangleInstanced().also { mesh = it }
                draw(provider, context, listOf(renderable(instancedMesh, Matrix4(), material, environment)))
            } else {
                val plain = triangle().also { mesh = it }
                draw(provider, context, transforms.map { renderable(plain, it, material, environment) })
            }
            context.end()
            return readPixels()
        } finally {
            framebuffer.end()
            provider.dispose()
            framebuffer.dispose()
            mesh?.dispose()
        }
    }

    private fun draw(provider: ModelShaderProvider, context: RenderContext, renderables: List<Renderable>) {
        val camera = camera()
        for (renderable in renderables) {
            val shader = provider.getShader(renderable)
            shader.begin(camera, context)
            shader.render(renderable)
            shader.end()
        }
    }

    private fun renderDepth(instanced: Boolean): ByteArray {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = ModelDepthShaderProvider(ShadowDepthPass())
        var mesh: IntMesh? = null
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 16))
            context.begin()
            if (instanced) {
                val instancedMesh = triangleInstanced().also { mesh = it }
                drawDepth(provider, context, listOf(renderable(instancedMesh, Matrix4(), Material(), environment())))
            } else {
                val plain = triangle().also { mesh = it }
                drawDepth(provider, context, transforms.map { renderable(plain, it, Material(), environment()) })
            }
            context.end()
            return readPixels()
        } finally {
            framebuffer.end()
            provider.dispose()
            framebuffer.dispose()
            mesh?.dispose()
        }
    }

    private fun drawDepth(provider: ModelDepthShaderProvider, context: RenderContext, renderables: List<Renderable>) {
        val camera = camera()
        for (renderable in renderables) {
            val shader = provider.get(renderable)
            shader.begin(camera, context)
            shader.render(renderable)
            shader.end()
        }
    }

    private fun readPixels(): ByteArray {
        val buffer = BufferUtils.newByteBuffer(32 * 32 * 4)
        Gdx.gl.glReadPixels(0, 0, 32, 32, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        return ByteArray(32 * 32 * 4) { buffer.get(it) }
    }

    private fun assertPixelsClose(label: String, expected: ByteArray, actual: ByteArray) {
        assertEquals("$label: pixel counts differ", expected.size, actual.size)
        var worst = 0
        for (i in expected.indices) {
            worst = maxOf(worst, abs((expected[i].toInt() and 255) - (actual[i].toInt() and 255)))
        }
        assertTrue("$label: the instanced draw differed from one renderable per instance by $worst/255", worst <= 1)
    }

    private fun gridWithInstances(spacing: Float): IntMesh = gridMesh(300).also { mesh ->
        mesh.enableInstancedRendering(true, 2, *InstanceAttributes.of())
        val data = FloatArray(2 * InstanceAttributes.FLOATS)
        InstanceAttributes.write(Matrix4(), data, 0)
        InstanceAttributes.write(Matrix4().setToTranslation(spacing, 0f, 0f), data, InstanceAttributes.FLOATS)
        mesh.setInstanceData(data)
    }

    /** The red channel under the grid point ([x], [z]) of a 128 x 128 image of two grids side by side from above. */
    private fun drawGridsFromAbove(provider: ModelShaderProvider, mesh: IntMesh, spacing: Float): (Float, Float) -> Int {
        val width = spacing + 299f
        val camera = OrthographicCamera(width, width).apply {
            position.set(width / 2, 100f, width / 2)
            direction.set(0f, -1f, 0f)
            up.set(0f, 0f, -1f)
            near = 1f
            far = 200f
            update()
        }
        val model = singleMeshModel(mesh)
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 128, 128, true)
        try {
            val batch = ModelBatch(provider)
            framebuffer.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            batch.begin(camera)
            batch.render(ModelInstance(model))
            batch.end()
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            val buffer = BufferUtils.newByteBuffer(128 * 128 * 4)
            Gdx.gl.glReadPixels(0, 0, 128, 128, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
            val pixels = ByteArray(buffer.capacity()) { buffer.get(it) }
            return { x, z ->
                val screen = camera.project(Vector3(x, 0f, z), 0f, 0f, 128f, 128f)
                pixels[(screen.y.toInt().coerceIn(0, 127) * 128 + screen.x.toInt().coerceIn(0, 127)) * 4].toInt() and 255
            }
        } finally {
            framebuffer.end()
            framebuffer.dispose()
            model.dispose()
        }
    }

    @Test fun twoInstancesOfA90000VertexMeshDrawWithOneInstancedVariant() = TestGl.run {
        val provider = ModelShaderProvider()
        val spacing = 320f
        try {
            val pixel = drawGridsFromAbove(provider, gridWithInstances(spacing), spacing)
            // the high rows of both copies are drawn, the gap between them is empty
            assertTrue("the first copy's far rows", pixel(150f, 280f) > 0)
            assertTrue("the second copy's far rows", pixel(spacing + 150f, 280f) > 0)
            assertEquals("nothing between the copies", 0, pixel(310f, 150f))
            assertEquals("one variant for the instanced mesh", 1, provider.shaderCount)
        } finally {
            provider.dispose()
        }
    }

    @Test fun anInstancedAndAPlainMeshCreateExactlyTwoVariants() = TestGl.run {
        val provider = ModelShaderProvider()
        try {
            drawGridsFromAbove(provider, gridWithInstances(320f), 320f)
            drawGridsFromAbove(provider, gridMesh(300), 320f)
            drawGridsFromAbove(provider, gridWithInstances(320f), 320f)
            assertEquals("plain and instanced, however often they are drawn", 2, provider.shaderCount)
        } finally {
            provider.dispose()
        }
    }

    @Test fun disablingInstancingAfterTheFirstDrawSwitchesToThePlainVariant() = TestGl.run {
        val provider = ModelShaderProvider()
        val spacing = 320f
        val mesh = gridWithInstances(spacing)
        val model = singleMeshModel(mesh)
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 64, 64, true)
        try {
            val batch = ModelBatch(provider)
            val instance = ModelInstance(model)
            val camera = OrthographicCamera(spacing + 299f, spacing + 299f).apply {
                position.set((spacing + 299f) / 2, 100f, (spacing + 299f) / 2)
                direction.set(0f, -1f, 0f); up.set(0f, 0f, -1f); near = 1f; far = 200f; update()
            }
            framebuffer.begin()
            batch.begin(camera); batch.render(instance); batch.end()
            assertEquals(1, provider.shaderCount)
            mesh.disableInstancedRendering()
            batch.begin(camera); batch.render(instance); batch.end()
            assertEquals("the plain variant was created for the same mesh", 2, provider.shaderCount)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
        } finally {
            framebuffer.end(); framebuffer.dispose(); provider.dispose(); model.dispose()
        }
    }
}
