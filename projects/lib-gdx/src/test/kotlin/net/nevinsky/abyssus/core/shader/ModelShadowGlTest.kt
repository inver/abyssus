/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Vector4
import net.nevinsky.abyssus.lib.gdx.model.PBRFloatAttribute
import com.badlogic.gdx.utils.BufferUtils
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class ModelShadowGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    @Test fun atlasShadowsOnlyTheirLightAndPreservesHdrAndEmissive() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val lit = colorWithAtlas(pbr, shadow = false, fill = true)
            val shadow = colorWithAtlas(pbr, shadow = true, fill = true)
            val noFill = colorWithAtlas(pbr, shadow = true, fill = false)
            assertTrue("red light was not shadowed ($pbr): ${lit.toList()} / ${shadow.toList()}", lit[0] > shadow[0] + 10)
            assertTrue("second light must remain independent", kotlin.math.abs(lit[1] - shadow[1]) <= 2)
            assertTrue("second light should fill the shadow", shadow[1] > noFill[1] + 10)
            assertTrue("HDR/emissive blue must remain visible", shadow[2] > 10)
            assertTrue("HDR/emissive must remain independent", kotlin.math.abs(lit[2] - shadow[2]) <= 2)
        }
    }

    @Test fun alphaCutoutsMatchDepthAndBothColorShadersWithoutBlending() = TestGl.run {
        for (pbr in listOf(false, true)) {
            assertArrayEquals(intArrayOf(0, 0, 0), colorWithAtlas(pbr, false, true, 0f))
            assertTrue("alpha equal to cutoff remains visible", colorWithAtlas(pbr, false, true, 0.5f).sum() > 10)
            assertTrue(colorWithAtlas(pbr, false, true, 1f).sum() > 10)
        }
    }

    @Test fun slopingReceiverDoesNotShadowItself() = TestGl.run {
        for (pbr in listOf(false, true)) {
            val lit = colorWithAtlas(pbr, false, false, selfDepth = true)
            val mapped = colorWithAtlas(pbr, true, false, selfDepth = true)
            assertTrue("self shadow on a plane: ${lit.toList()} / ${mapped.toList()}", kotlin.math.abs(lit[0] - mapped[0]) <= 3)
        }
    }

    private fun colorWithAtlas(pbr: Boolean, shadow: Boolean, fill: Boolean, cutoutAlpha: Float? = null, selfDepth: Boolean = false): IntArray {
        val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f,-0.8f,0f,0f,0f,1f,0.5f,0.5f, 0.8f,-0.8f,0f,0f,0f,1f,0.5f,0.5f, 0f,0.8f,0f,0f,0f,1f,0.5f,0.5f))
            setIndices(intArrayOf(0,1,2))
        }
        val map = Pixmap(32,32,Pixmap.Format.RGBA8888).also {
            if (cutoutAlpha == null) it.setColor(0f,0f,0f,0f) else it.setColor(1f,1f,1f,cutoutAlpha)
            it.fill()
        }
        val atlas = Texture(map)
        val skyMap = Pixmap(4,4,Pixmap.Format.RGBA8888).also { it.setColor(0f,0f,0.15f,1f); it.fill() }
        val sky = Cubemap(skyMap,skyMap,skyMap,skyMap,skyMap,skyMap)
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888,32,32,true)
        val depthBuffer = FrameBuffer(Pixmap.Format.RGBA8888,32,32,true)
        val provider = DefaultShaderProvider(ShaderConfig().apply { numPointLights = 0; numSpotLights = 0 })
        val camera = OrthographicCamera(2f,2f).apply { position.set(0f,0f,2f); lookAt(0f,0f,0f); near=0.1f; far=4f; update() }
        val lightCamera = OrthographicCamera(2f,2f).apply { position.set(2f,0f,2f); lookAt(0f,0f,0f); near=0.1f; far=6f; update() }
        val red = DirectionalLight().set(Color(0.8f,0f,0f,1f), if (selfDepth) -0.7071f else 0f,0f,if (selfDepth) -0.7071f else -1f)
        val environment = Environment().apply {
            add(red)
            if (fill) add(DirectionalLight().set(Color(0f,0.8f,0f,1f),0f,0f,-1f))
            set(EnvironmentLightAttribute(sky,sky,1,FloatArray(18) { if (it % 3 == 2) 0.15f else 0f }))
            val records = if (shadow) listOf(ShadowLightRecord("red",ShadowLightKind.DIRECTIONAL,red,
                listOf(ShadowAtlasView((if (selfDepth) lightCamera else camera).combined.cpy(),Vector4(0f,0f,1f,1f))),Vector3(),4f, if (selfDepth) 0.000001f else 0.0008f)) else emptyList()
            set(ShadowAtlasAttribute(if (selfDepth) depthBuffer.colorBufferTexture else atlas,records))
        }
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createEmissive(0f,0f,0.05f,1f))
        if (cutoutAlpha != null) material.set(TextureAttribute.createDiffuse(atlas), FloatAttribute.createAlphaTest(0.49f))
        if (pbr) material.set(PBRFloatAttribute.createMetallic(0f),PBRFloatAttribute.createRoughness(1f))
        val renderable = Renderable().apply { meshPart.set("receiver",mesh,0,3,GL20.GL_TRIANGLES); this.material=material; this.environment=environment }
        val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU,1))
        if (selfDepth) {
            val depthProvider = ModelDepthShaderProvider(ShadowDepthPass())
            depthBuffer.begin()
            try {
                Gdx.gl.glClearColor(1f,1f,1f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
                context.begin()
                val depthShader = depthProvider.get(renderable)!!
                depthShader.begin(lightCamera,context); depthShader.render(renderable); depthShader.end(); context.end()
            } finally { depthBuffer.end(); depthProvider.dispose() }
        }
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(0f,0f,0f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            context.begin()
            val shader = provider.get(renderable)!!
            shader.begin(camera,context); shader.render(renderable); shader.end(); context.end()
            val pixel = BufferUtils.newByteBuffer(4)
            Gdx.gl.glReadPixels(16,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel)
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError())
            return IntArray(3) { pixel[it].toInt() and 255 }
        } finally { framebuffer.end(); provider.dispose(); framebuffer.dispose(); depthBuffer.dispose(); sky.dispose(); atlas.dispose(); map.dispose(); skyMap.dispose(); mesh.dispose() }
    }

    @Test fun depthDrawAddressesVerticesAbove65535() = TestGl.run {
        val vertices = FloatArray(70003 * 3)
        val triangle = floatArrayOf(-0.8f,-0.8f,0f, 0.8f,-0.8f,0f, 0f,0.8f,0f)
        triangle.copyInto(vertices, 70000 * 3)
        val mesh = Mesh(true, 70003, 3, VertexAttribute.Position()).apply {
            setVertices(vertices); setIndices(intArrayOf(70000,70001,70002))
        }
        try { assertTrue("indexed triangle must write packed depth", render(mesh, Material()) < 0.8f) }
        finally { mesh.dispose() }
    }

    @Test fun alphaTestUsesTextureHolesAndBlendedMaterialsDoNotCast() = TestGl.run {
        val mesh = Mesh(true, 3, 3, VertexAttribute.Position(), VertexAttribute.TexCoords(0)).apply {
            setVertices(floatArrayOf(-0.8f,-0.8f,0f,0.5f,0.5f, 0.8f,-0.8f,0f,0.5f,0.5f, 0f,0.8f,0f,0.5f,0.5f))
            setIndices(intArrayOf(0,1,2))
        }
        val pixmap = Pixmap(1,1,Pixmap.Format.RGBA8888)
        pixmap.setColor(1f,1f,1f,0f); pixmap.fill()
        val texture = Texture(pixmap)
        try {
            val cutout = Material(TextureAttribute.createDiffuse(texture), FloatAttribute.createAlphaTest(0.5f))
            assertEquals(1f, render(mesh, cutout), 0.01f)
            cutout.remove(TextureAttribute.Diffuse)
            assertTrue(render(mesh, cutout) < 0.8f)
            cutout.set(ColorAttribute.createDiffuse(1f, 1f, 1f, 0.25f))
            assertEquals(1f, render(mesh, cutout), 0.01f)
            cutout.set(ColorAttribute.createDiffuse(1f, 1f, 1f, 0.5f))
            assertTrue("alpha equal to cutoff must cast", render(mesh, cutout) < 0.8f)
            assertEquals(1f, render(mesh, Material(BlendingAttribute(true, 0.5f))), 0.01f)
        } finally { texture.dispose(); pixmap.dispose(); mesh.dispose() }
    }

    @Test fun posedBonesMoveTheShadowWithTheDisplayedGeometry() = TestGl.run {
        val mesh = Mesh(true,3,3,VertexAttribute.Position(),VertexAttribute.BoneWeight(0)).apply {
            setVertices(floatArrayOf(-0.8f,-0.8f,0f,0f,1f, 0.8f,-0.8f,0f,0f,1f, 0f,0.8f,0f,0f,1f))
            setIndices(intArrayOf(0,1,2))
        }
        try {
            assertTrue(render(mesh, Material(), arrayOf(Matrix4())) < 0.8f)
            assertEquals(1f, render(mesh, Material(), arrayOf(Matrix4().setToTranslation(3f,0f,0f))), 0.01f)
        } finally { mesh.dispose() }
    }

    @Test fun pointDepthIsNormalizedWorldSpaceDistance() = TestGl.run {
        val mesh = Mesh(true,3,3,VertexAttribute.Position()).apply {
            setVertices(floatArrayOf(-0.8f,-0.8f,0f, 0.8f,-0.8f,0f, 0f,0.8f,0f)); setIndices(intArrayOf(0,1,2))
        }
        val pass = ShadowDepthPass().apply { radialDepth = true; lightPosition.set(0f,0f,2f); far = 8f }
        try { assertEquals(0.25f, render(mesh, Material(), pass = pass), 0.02f) }
        finally { mesh.dispose() }
    }

    @Test fun packedDepthSurvivesRgba8Quantization() = TestGl.run {
        val mesh = Mesh(true,3,3,VertexAttribute.Position()).apply {
            setVertices(floatArrayOf(-0.8f,-0.8f,0f, 0.8f,-0.8f,0f, 0f,0.8f,0f)); setIndices(intArrayOf(0,1,2))
        }
        try {
            // The sampled pixel is off center by 1/32 world units in both axes.
            val distance = kotlin.math.sqrt(4f + 2f / (32f * 32f))
            for (depth in listOf(0.1f, 0.25f, 0.501f, 0.78f, 0.99f)) {
                val pass = ShadowDepthPass().apply { radialDepth = true; lightPosition.set(0f,0f,2f); far = distance / depth }
                assertEquals("depth $depth must survive RGBA8 storage", depth, render(mesh, Material(), pass = pass), 0.00001f)
            }
        } finally { mesh.dispose() }
    }

    private fun render(mesh: Mesh, material: Material, bones: Array<Matrix4>? = null,
                       pass: ShadowDepthPass = ShadowDepthPass()): Float {
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888,32,32,true)
        val provider = ModelDepthShaderProvider(pass)
        val renderable = Renderable().apply {
            meshPart.set("caster",mesh,0,3,GL20.GL_TRIANGLES); this.material = material; this.bones = bones
        }
        val context = RenderContext(DefaultTextureBinder(DefaultTextureBinder.LRU))
        framebuffer.begin()
        try {
            Gdx.gl.glClearColor(1f,1f,1f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            val camera = OrthographicCamera(2f,2f).apply { position.set(0f,0f,2f); lookAt(0f,0f,0f); near=0.1f; far=4f; update() }
            context.begin()
            val shader = provider.get(renderable)!!
            shader.begin(camera,context); shader.render(renderable); shader.end(); context.end()
            val pixel = BufferUtils.newByteBuffer(4)
            Gdx.gl.glReadPixels(16,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel)
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError())
            // Packed depth uses conventional RGBA weights. An untouched white pixel denotes fully lit.
            if ((0..3).all { pixel[it].toInt() and 255 == 255 }) return 1f
            return (pixel[0].toInt() and 255)/255f/16581375f + (pixel[1].toInt() and 255)/255f/65025f +
                (pixel[2].toInt() and 255)/255f/255f + (pixel[3].toInt() and 255)/255f
        } finally { framebuffer.end(); provider.dispose(); framebuffer.dispose() }
    }
}
