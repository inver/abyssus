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
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.BufferUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.IntBuffer

/**
 * A 90,000-vertex [IntMesh] drawn through the stock `ModelInstance`, `ModelBatch` and `DefaultShader`. Opt-in:
 * `-Dabyssus.glTests=true`.
 */
class IntMeshGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val size = 300
    private val edge = (size - 1).toFloat()

    /** The grid seen from above, `x` and `z` in 0..299 filling a 128 x 128 image, `z` growing downwards. */
    private fun camera() = OrthographicCamera(size.toFloat(), size.toFloat()).apply {
        position.set(edge / 2, 100f, edge / 2)
        direction.set(0f, -1f, 0f)
        up.set(0f, 0f, -1f)
        near = 1f
        far = 200f
        update()
    }

    private fun stockShaders(): DefaultShaderProvider {
        // the stock sources come from the libGDX jar, so the test needs no Gdx.files backend
        fun source(name: String) = javaClass.getResource("/com/badlogic/gdx/graphics/g3d/shaders/$name")!!.readText()
        return DefaultShaderProvider(DefaultShader.Config(source("default.vertex.glsl"), source("default.fragment.glsl")))
    }

    /** Draws [mesh] through a stock batch and returns the 128 x 128 RGBA image. */
    private fun draw(mesh: IntMesh): ByteArray {
        val model = singleMeshModel(mesh)
        val shaders = stockShaders()
        val framebuffer = FrameBuffer(Pixmap.Format.RGBA8888, IMAGE, IMAGE, true)
        try {
            val batch = ModelBatch(shaders)
            framebuffer.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
            batch.begin(camera())
            batch.render(ModelInstance(model))
            batch.end()
            assertEquals("GL error after drawing", GL20.GL_NO_ERROR, Gdx.gl.glGetError())
            val buffer = BufferUtils.newByteBuffer(IMAGE * IMAGE * 4)
            Gdx.gl.glReadPixels(0, 0, IMAGE, IMAGE, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buffer)
            return ByteArray(buffer.capacity()) { buffer.get(it) }
        } finally {
            framebuffer.end()
            framebuffer.dispose()
            shaders.dispose()
            model.dispose()
        }
    }

    /** The red channel at the pixel under the grid point ([x], [z]). */
    private fun ByteArray.at(x: Float, z: Float): Int {
        val screen = camera().project(Vector3(x, 0f, z), 0f, 0f, IMAGE.toFloat(), IMAGE.toFloat())
        return this[((screen.y.toInt().coerceIn(0, IMAGE - 1)) * IMAGE + screen.x.toInt().coerceIn(0, IMAGE - 1)) * 4]
            .toInt() and 255
    }

    @Test
    fun drawsEveryRowOfAMeshWithMoreThan16BitVertices() = TestGl.run {
        val mesh = gridMesh(size)
        val image = draw(mesh)
        // 218 rows (65,535 / 300) fit a 16-bit index: the rows below that are the proof
        assertTrue("a row below index 65,535 must be drawn", image.at(150f, 100f) > 0)
        assertTrue("a row above index 65,535 must be drawn", image.at(150f, 280f) > 0)
        assertTrue("the last row must be drawn", image.at(150f, edge - 1f) > 0)
    }

    @Test
    fun drawingWithSixteenBitIndicesLosesTheRowsAbove65535() = TestGl.run {
        val image = draw(gridMesh(size) { vertices, indices, _, attributes ->
            SixteenBitDrawMesh(vertices, indices, attributes)
        })
        assertTrue("rows below index 65,535 survive the truncation", image.at(150f, 100f) > 0 || image.at(150f, 10f) > 0)
        assertEquals("rows above index 65,535 are gone with 16-bit indices", 0, image.at(150f, 280f))
        assertEquals(0, image.at(150f, edge - 1f))
    }

    @Test
    fun boundsOfTheWholeMeshAreReadThroughTheIntIndices() = TestGl.run {
        val mesh = gridMesh(size)
        val part = MeshPart("grid", mesh, 0, mesh.numIndices, GL20.GL_TRIANGLES)
        try {
            part.update()
            assertEquals(edge, part.center.x + part.halfExtents.x, 1e-4f)
            assertEquals(edge, part.center.z + part.halfExtents.z, 1e-4f)
            assertEquals(0f, part.center.x - part.halfExtents.x, 1e-4f)

            val box = mesh.extendBoundingBox(BoundingBox().inf(), 0, mesh.numIndices)
            assertEquals(edge, box.max.z, 1e-4f)
            // the radius also reads the 32-bit indices: the farthest corner is a vertex above index 65,535
            val radius = mesh.calculateRadius(Vector3(0f, 0f, 0f), 0, mesh.numIndices)
            assertEquals(edge * Math.sqrt(2.0).toFloat(), radius, 1e-2f)
        } finally {
            mesh.dispose()
        }
    }

    @Test
    fun theSixteenBitViewsThrow() = TestGl.run {
        val mesh = IntMesh(true, 4, 6, VertexAttribute.Position())
        try {
            val blocked = listOf<() -> Unit>(
                { mesh.getIndicesBuffer(false) },
                { @Suppress("DEPRECATION") mesh.getIndicesBuffer() },
                { mesh.setIndices(shortArrayOf(0, 1, 2)) },
                { mesh.setIndices(shortArrayOf(0, 1, 2), 0, 3) },
                { mesh.getIndices(ShortArray(3)) },
                { mesh.getIndices(ShortArray(3), 0) },
                { mesh.getIndices(0, ShortArray(3), 0) },
                { mesh.getIndices(0, 3, ShortArray(3), 0) },
                { mesh.copy(true) },
                { mesh.copy(true, false, null) }
            )
            for (call in blocked) {
                assertThrows(UnsupportedOperationException::class.java) { call() }
            }
            mesh.setIndices(intArrayOf(0, 1, 2, 70_000, 5, 6))
            val out = IntArray(3)
            mesh.getIndices(3, 3, out, 0)
            assertEquals(listOf(70_000, 5, 6), out.toList())
        } finally {
            mesh.dispose()
        }
    }

    /** The control: the same draw with `GL_UNSIGNED_SHORT`, which is what a stock mesh does. Indices are truncated. */
    private class SixteenBitDrawMesh(vertices: Int, indices: Int, attributes: Array<VertexAttribute>) :
        IntMesh(true, vertices, indices, VertexAttributes(*attributes)) {
        override fun render(shader: ShaderProgram?, primitiveType: Int, offset: Int, count: Int, autoBind: Boolean) {
            bind(shader)
            val ints: IntBuffer = intIndices(false)
            val shorts = BufferUtils.newShortBuffer(numIndices)
            for (i in 0 until numIndices) shorts.put(ints.get(i).toShort())
            shorts.flip()
            val handle = Gdx.gl20.glGenBuffer()
            Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, handle)
            Gdx.gl20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, numIndices * 2, shorts, GL20.GL_STATIC_DRAW)
            Gdx.gl20.glDrawElements(primitiveType, count, GL20.GL_UNSIGNED_SHORT, offset * 2)
            Gdx.gl20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0)
            Gdx.gl20.glDeleteBuffer(handle)
            unbind(shader)
        }
    }

    private companion object {
        const val IMAGE = 128
    }
}
