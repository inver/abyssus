/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.foliage

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider
import net.nevinsky.abyssus.lib.core.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh
import net.nevinsky.abyssus.lib.gdx.model.Model
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The source an instanced foliage copy reads from (design decision 5 and its risk "Reading mesh data back from
 * `VertexBufferObject`"): every MODEL asset of `Untitled` keeps its vertices and indices in the mesh's CPU-side
 * buffers, those bytes are the model's own, and an instanced copy built from them reads them back unchanged. If a
 * model had no CPU-side data the drawable would have to fall back to the prepared `ModelData`.
 *
 * A `Mesh` allocates its GPU buffers on construction, so this needs a real context: opt-in `-Dabyssus.glTests=true`.
 */
class FoliageMeshSourceTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val untitled = testProject("Untitled")

    /** The MODEL assets of `Untitled`; `model_extra500` has no `meta.json`, so it is not an asset. */
    private val modelNames = listOf(
        "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb",
        "model_828d51e4-8427-4769-bcb6-13f8f21f23e9",
        "model_900f6f61-6384-434a-be81-56ce303fbb56",
        "model_fc33e1f1-015b-4524-9b10-aa417acd273c",
    )

    /** What a mesh gives back through [Mesh.getVertices] and [Mesh.getIndices]. */
    private class ReadBack(
        val vertices: FloatArray,
        val indices: IntArray,
        val stride: Int,
        val positionOffset: Int,
        val positionComponents: Int,
    )

    @Test
    fun everyModelKeepsTheCpuSideDataAnInstancedCopyReads() {
        modelNames.forEach(::checkModel)
    }

    /** One model per context, so each run stays inside the harness' window timeout. */
    private fun checkModel(name: String) = TestGl.run {
        val meta = checkNotNull(testMetaLoader(untitled).loadBaseMeta(name)) { "$name has a meta" }
        val file = FileHandle(File(untitled, "assets/$name/${checkNotNull(meta.typedAdditional<ModelMeta>().file)}"))
        val assimp = AssimpModelLoader()
        val data = assimp.loadData(file)
        val model = Model(data, StubTextures())
        try {
            val meshes = model.meshes
            assertTrue("$name keeps meshes", meshes.isNotEmpty())
            var copied = false
            for (mesh in meshes) {
                val read = readBack(name, mesh)
                assertIsTheModelsOwnGeometry(name, mesh, data, read)
                if (!copied) {
                    assertAnInstancedCopyReadsItBack(name, mesh, read)
                    copied = true
                }
            }
        } finally {
            model.dispose()
        }
    }

    /** Reads every vertex and index of [mesh] back from the mesh itself, not from the model data. */
    private fun readBack(name: String, mesh: Mesh): ReadBack {
        assertTrue("$name has CPU-side vertices", mesh.numVertices > 0)
        val position = checkNotNull(mesh.vertexAttributes.findByUsage(VertexAttributes.Usage.Position)) {
            "$name has a position attribute"
        }
        val stride = mesh.vertexSize / 4
        val vertices = FloatArray(mesh.numVertices * stride)
        mesh.getVertices(vertices)
        val indices = IntArray(mesh.numIndices)
        if (mesh.numIndices > 0) {
            mesh.getIndices(indices)
        }
        return ReadBack(vertices, indices, stride, position.offset / 4, position.numComponents)
    }

    /** The read-back bytes are the model's own geometry, with finite positions and indices inside the mesh. */
    private fun assertIsTheModelsOwnGeometry(name: String, mesh: Mesh, data: ModelData, read: ReadBack) {
        for (vertex in 0 until mesh.numVertices) {
            for (component in 0 until read.positionComponents) {
                val value = read.vertices[vertex * read.stride + read.positionOffset + component]
                assertTrue("$name vertex $vertex has a finite position", value.isFinite())
            }
        }
        val source = data.meshes.firstOrNull { it.vertices.contentEquals(read.vertices) }
        assertNotNull("$name keeps the model's own vertices", source)
        if (mesh.numIndices > 0) {
            for (index in read.indices) {
                assertTrue("$name index $index is one of its ${mesh.numVertices} vertices", index in 0 until mesh.numVertices)
            }
            assertArrayEquals(
                "$name keeps the model's own indices",
                source!!.ownIndices(),
                read.indices,
            )
        }
    }

    /** The copy an instanced drawable makes reads back exactly the bytes it was built from. */
    private fun assertAnInstancedCopyReadsItBack(name: String, mesh: Mesh, read: ReadBack) {
        val copy = Mesh(true, mesh.numVertices, mesh.numIndices, mesh.vertexAttributes)
        try {
            copy.setVertices(read.vertices)
            if (mesh.numIndices > 0) {
                copy.setIndices(read.indices)
            }
            copy.enableInstancedRendering(true, 2, *instanceAttributes())
            copy.setInstanceData(FloatArray(2 * 4 * 4).also { floats -> (0 until 2).forEach { floats[it * 16] = 1f } })
            assertTrue("$name copy is instanced", copy.isInstanced)

            val vertices = FloatArray(copy.numVertices * copy.vertexSize / 4)
            copy.getVertices(vertices)
            assertArrayEquals("$name copy reads the same vertices", read.vertices, vertices, 0f)
            if (copy.numIndices > 0) {
                val indices = IntArray(copy.numIndices)
                copy.getIndices(indices)
                assertArrayEquals("$name copy reads the same indices", read.indices, indices)
            }
            assertFalse("$name model mesh stays shared and un-instanced", mesh.isInstanced)
        } finally {
            copy.dispose()
        }
    }

    /** The four `vec4` attributes a `mat4` instance transform is made of. */
    private fun instanceAttributes(): Array<VertexAttribute> = (0 until 4).map {
        VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_instance$it")
    }.toTypedArray()

    private fun ModelMesh.ownIndices(): IntArray =
        parts.flatMap { it.indices.asList() }.toIntArray()

    /** Materials are not the subject here: one white texture per file keeps the model off the disk. */
    private class StubTextures : TextureProvider {
        private val byName = HashMap<String, Texture>()

        override fun load(fileName: String): Texture = byName.getOrPut(fileName) {
            val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
            pixmap.setColor(Color.WHITE)
            pixmap.fill()
            try {
                Texture(pixmap)
            } finally {
                pixmap.dispose()
            }
        }
    }
}
