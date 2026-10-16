/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.g3d

import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.model.GridModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.badlogic.gdx.graphics.g3d.model.Node as StockNode
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart

/** [IntModel] against the model the fork builds from the same [ModelData]. Opt-in: `-Dabyssus.glTests=true`. */
class IntModelGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    /** Hands out a 2 x 2 texture per file name and counts how often each file was asked for. */
    private class CountingTextures : TextureProvider {
        val loads = HashMap<String, Int>()
        override fun load(fileName: String): Texture {
            loads.merge(fileName, 1, Int::plus)
            return Texture(Pixmap(2, 2, Pixmap.Format.RGBA8888))
        }
    }

    private fun resourceCopy(name: String): File {
        val target = File(Files.createTempDirectory("abyssus_int_model").toFile(), name)
        javaClass.getResourceAsStream("/model/$name")!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    @Test
    fun animatedGltfBuildsTheNodesAnimationsAndBonesOfItsData() = TestGl.run {
        val file = FileHandle(resourceCopy("animated.gltf"))
        val data = AssimpModelLoader().loadData(file)
        val model = IntModel(data, CountingTextures())
        try {
            assertSameAsData(data, model)
            assertEquals(1, model.animations.size)
            assertEquals(data.animations[0].id, model.animations[0].id)
            assertTrue("the fixture is skinned", model.maxBones > 0)
            val bound = data.nodes.flatMap { it.allParts() }.maxOf { it.bones?.size ?: 0 }
            assertEquals("the largest bone set of the data", bound, model.maxBones)
        } finally {
            model.dispose()
        }
    }

    @Test
    fun crateBuildsTheMaterialsOfItsDataAndLoadsEachTextureOnce() = TestGl.run {
        val file = FileHandle(resourceCopy("crate.dae"))
        val data = AssimpModelLoader().loadData(file)
        val textures = CountingTextures()
        val model = IntModel(data, textures)
        try {
            assertSameAsData(data, model)
            assertTrue("the crate has a texture", textures.loads.isNotEmpty())
            assertTrue("one load per file", textures.loads.values.all { it == 1 })
            assertEquals(data.materials.flatMap { it.textures?.map { t -> t.fileName } ?: emptyList() }.toSet(), textures.loads.keys)
            assertTrue("a textured material", model.materials.any { it.has(TextureAttribute.Diffuse) })
        } finally {
            model.dispose()
        }
    }

    @Test
    fun theGridKeepsAllVerticesInOneMesh() = TestGl.run {
        val grid = GridModel(300)
        val file = FileHandle(grid.write())
        val data = AssimpModelLoader().loadData(file)
        val model = AssimpModelLoader().buildIntModel(data, file, HashMap())
        try {
            assertSameAsData(data, model)
            val mesh = model.meshes.single()
            assertTrue(mesh is IntMesh)
            assertEquals(grid.vertexCount, mesh.numVertices)
            assertEquals(grid.indexCount, mesh.numIndices)
            assertEquals(1, model.meshParts.size)
            assertEquals("the part spans every index", grid.indexCount, model.meshParts[0].size)
            assertEquals((grid.size - 1).toFloat(), model.meshParts[0].center.x + model.meshParts[0].halfExtents.x, 1e-4f)
        } finally {
            model.dispose()
        }
    }

    private fun ModelNode.allParts(): List<ModelNodePart> =
        (parts?.toList() ?: emptyList()) + (children?.flatMap { it.allParts() } ?: emptyList())

    private fun ModelNode.ids(): List<String> = listOf(id) + (children?.flatMap { it.ids() } ?: emptyList())
    private fun StockNode.ids(): List<String> = listOf(id) + children.flatMap { it.ids() }

    /** The model is what its data describes: nodes, parts, materials, animations and 32-bit meshes. */
    private fun assertSameAsData(data: ModelData, model: IntModel) {
        assertEquals("node ids", data.nodes.flatMap { it.ids() }, model.nodes.flatMap { it.ids() })
        val partIds = data.nodes.flatMap { it.allParts() }.map { it.meshPartId }
        val builtParts = model.nodes.flatMap { collectParts(it) }.map { it.meshPart.id }
        assertEquals("part ids", partIds, builtParts)
        assertEquals("materials", data.materials.map { it.id }.toSet(), model.materials.map { it.id }.toSet())
        assertEquals("animations", data.animations.map { it.id }, model.animations.map { it.id })
        assertEquals("meshes", data.meshes.map { it.vertices.size / it.attributes.sumOf { a -> a.numComponents } }.sorted(),
            model.meshes.map { it.numVertices }.sorted())
        assertEquals("indices", data.meshes.sumOf { m -> m.parts.sumOf { it.indices.size } }, model.meshes.sumOf { it.numIndices })
        assertTrue("every mesh is 32-bit", model.meshes.all { it is IntMesh })
    }

    private fun collectParts(node: StockNode): List<NodePart> = node.parts.toList() + node.children.flatMap { collectParts(it) }
}
