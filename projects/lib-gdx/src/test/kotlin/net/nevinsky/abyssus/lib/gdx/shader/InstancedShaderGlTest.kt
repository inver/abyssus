/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

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
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.mesh.InstanceAttributes
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * The `instancedFlag` shader variants take the world matrix from the mesh's instance attributes. One instanced draw of
 * several copies must therefore produce the same pixels as the same copies drawn one renderable at a time with their
 * own [Renderable.worldTransform].
 */
class InstancedShaderGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    @Test fun instancedDrawMatchesOneRenderablePerInstance() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val separate = render(pbr, instanced = false)
            val instanced = render(pbr, instanced = true)
            assertTrue("the comparison must see lit pixels (pbr=$pbr)", separate.any { (it.toInt() and 255) > 20 })
            assertPixelsClose("color pass (pbr=$pbr)", separate, instanced)
        }
    }

    @Test fun instancedDepthPassMatchesOneRenderablePerInstance() = TestGl.run {
        val separate = renderDepth(instanced = false)
        val instanced = renderDepth(instanced = true)
        assertTrue("the comparison must see written depth", separate.any { (it.toInt() and 255) in 1..254 })
        assertPixelsClose("depth pass", separate, instanced)
    }

    @Test fun theProviderPicksTheVariantMatchingTheMesh() = TestGl.run {
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0 })
        val plain = triangle()
        val instancedMesh = triangleInstanced()
        try {
            val plainShader = provider.get(renderable(plain, Matrix4(), material(false), environment())) as DefaultShader
            val instancedShader = provider.get(renderable(instancedMesh, Matrix4(), material(false), environment())) as DefaultShader
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

    private fun triangle(): Mesh =
        Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(
                floatArrayOf(
                    -0.4f, -0.4f, 0f, 0f, 0f, 1f, 0f, 0f,
                    0.4f, -0.4f, 0f, 0f, 0f, 1f, 1f, 0f,
                    0f, 0.4f, 0f, 0f, 0f, 1f, 0.5f, 1f
                )
            )
            setIndices(intArrayOf(0, 1, 2))
        }

    private fun triangleInstanced(): Mesh = triangle().also { mesh ->
        mesh.enableInstancedRendering(true, transforms.size, *InstanceAttributes.of())
        val data = FloatArray(transforms.size * InstanceAttributes.FLOATS)
        transforms.forEachIndexed { i, matrix -> InstanceAttributes.write(matrix, data, i * InstanceAttributes.FLOATS) }
        mesh.setInstanceData(data)
    }

    private fun renderable(mesh: Mesh, transform: Matrix4, material: Material, environment: Environment): Renderable =
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

    private fun environment(): Environment = Environment().apply {
        add(DirectionalLight().set(Color(0.8f, 0.8f, 0.8f, 1f), 0f, 0f, -1f))
        set(ColorAttribute.createAmbientLight(0.25f, 0.25f, 0.25f, 1f))
    }

    private fun camera(): OrthographicCamera =
        OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }

    private fun render(pbr: Boolean, instanced: Boolean): ByteArray {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0 })
        val material = material(pbr)
        val environment = environment()
        var mesh: Mesh? = null
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

    private fun draw(provider: DefaultShaderProvider, context: RenderContext, renderables: List<Renderable>) {
        val camera = camera()
        for (renderable in renderables) {
            val shader = provider.get(renderable)!!
            shader.begin(camera, context)
            shader.render(renderable)
            shader.end()
        }
    }

    private fun renderDepth(instanced: Boolean): ByteArray {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = ModelDepthShaderProvider(ShadowDepthPass())
        var mesh: Mesh? = null
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
            val shader = provider.get(renderable)!!
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
}
