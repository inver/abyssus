/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.shader.ShadowDepthPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** [ModelDepthShader] writes depth for plain, instanced, skinned and alpha-cutout meshes. Opt-in GL test. */
class ModelDepthGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val corners = floatArrayOf(-0.4f, -0.4f, 0f, 0.4f, -0.4f, 0f, 0f, 0.4f, 0f)

    private fun triangle(vararg extra: VertexAttribute, perVertex: FloatArray = FloatArray(0)): IntMesh {
        val mesh = IntMesh(true, 3, 3, VertexAttribute.Position(), *extra)
        val stride = perVertex.size
        val data = FloatArray(3 * (3 + stride))
        for (v in 0 until 3) {
            corners.copyInto(data, v * (3 + stride), v * 3, v * 3 + 3)
            perVertex.copyInto(data, v * (3 + stride) + 3)
        }
        mesh.setVertices(data)
        mesh.setIndices(intArrayOf(0, 1, 2))
        return mesh
    }

    /** True when the depth pass wrote something at the middle of the image offset by ([dx], [dy]) world units. */
    private fun covered(
        mesh: IntMesh, material: Material = Material(), bones: Array<Matrix4>? = null, dx: Float = 0f, dy: Float = 0f
    ): Boolean {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, true)
        val provider = ModelDepthShaderProvider(ShadowDepthPass())
        val renderable = Renderable().apply {
            meshPart.set("caster", mesh, 0, 3, GL20.GL_TRIANGLES)
            this.material = material
            this.bones = bones
        }
        val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU, 1))
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            val camera = OrthographicCamera(2f, 2f).apply { position.set(0f, 0f, 2f); lookAt(0f, 0f, 0f); near = 0.1f; far = 4f; update() }
            context.begin()
            val shader = provider.get(renderable)
            shader.begin(camera, context)
            shader.render(renderable)
            shader.end()
            context.end()
            val pixel = BufferUtils.newByteBuffer(4)
            val x = 16 + (dx * 16).toInt()
            val y = 16 + (dy * 16).toInt()
            Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel)
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            return !(0..3).all { pixel[it].toInt() and 255 == 255 }
        } finally {
            framebuffer.end(); provider.dispose(); framebuffer.dispose()
        }
    }

    @Test fun aPlainMeshWritesDepth() = TestGl.run {
        val mesh = triangle()
        try {
            assertTrue(covered(mesh))
            assertTrue("the triangle does not reach the corner", !covered(mesh, dx = 0.8f, dy = 0.8f))
        } finally {
            mesh.dispose()
        }
    }

    @Test fun anInstancedMeshWritesDepthAtEachInstanceTransform() = TestGl.run {
        val mesh = triangle().apply {
            enableInstancedRendering(true, 2, *InstanceAttributes.of())
            val data = FloatArray(2 * InstanceAttributes.FLOATS)
            InstanceAttributes.write(Matrix4().setToTranslation(-0.5f, 0f, 0f), data, 0)
            InstanceAttributes.write(Matrix4().setToTranslation(0.5f, 0f, 0f), data, InstanceAttributes.FLOATS)
            setInstanceData(data)
        }
        try {
            assertTrue("the left copy", covered(mesh, dx = -0.5f))
            assertTrue("the right copy", covered(mesh, dx = 0.5f))
            assertTrue("not at the untranslated place", !covered(mesh))
        } finally {
            mesh.dispose()
        }
    }

    @Test fun aSkinnedMeshWritesDepthWhereItsBonesPutIt() = TestGl.run {
        val mesh = triangle(VertexAttribute.BoneWeight(0), perVertex = floatArrayOf(0f, 1f))
        try {
            assertTrue(covered(mesh, bones = arrayOf(Matrix4())))
            assertTrue("moved away by the bone", !covered(mesh, bones = arrayOf(Matrix4().setToTranslation(3f, 0f, 0f))))
            assertTrue("moved to the right", covered(mesh, bones = arrayOf(Matrix4().setToTranslation(0.5f, 0f, 0f)), dx = 0.5f))
        } finally {
            mesh.dispose()
        }
    }

    @Test fun anAlphaCutoutWritesNothingWhereTheTextureHasHoles() = TestGl.run {
        val mesh = triangle(VertexAttribute.TexCoords(0), perVertex = floatArrayOf(0.5f, 0.5f))
        val hole = Pixmap(1, 1, Pixmap.Format.RGBA8888).also { it.setColor(1f, 1f, 1f, 0f); it.fill() }
        val solid = Pixmap(1, 1, Pixmap.Format.RGBA8888).also { it.setColor(1f, 1f, 1f, 1f); it.fill() }
        val holes = Texture(hole)
        val full = Texture(solid)
        try {
            assertTrue("a hole is discarded", !covered(mesh, Material(TextureAttribute.createDiffuse(holes), FloatAttribute.createAlphaTest(0.5f))))
            assertTrue("an opaque texel stays", covered(mesh, Material(TextureAttribute.createDiffuse(full), FloatAttribute.createAlphaTest(0.5f))))
        } finally {
            holes.dispose(); full.dispose(); hole.dispose(); solid.dispose(); mesh.dispose()
        }
    }
}
