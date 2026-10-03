/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.model

import com.badlogic.gdx.files.FileHandle
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Parsing needs no GL context; this also proves the Assimp natives load. */
class AssimpLoadingTest {
    private fun copyOfResource(name: String): File {
        val dir = Files.createTempDirectory("abyssus_model").toFile()
        val target = File(dir, "model.gltf")
        javaClass.getResourceAsStream("/model/$name")!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    @Test
    fun parsesGltfIntoModelData() {
        val data = AssimpModelLoader().loadData(FileHandle(copyOfResource("animated.gltf")))
        assertFalse(data.meshes.isEmpty)
        assertTrue(data.nodes.size > 0)
        assertEquals(1, data.animations.size)
    }

    @Test
    fun corruptFileFailsWithAnExceptionNotACrash() {
        val file = File(Files.createTempDirectory("abyssus_bad").toFile(), "model.gltf").also { it.writeText("{ this is not a model") }
        try {
            AssimpModelLoader().loadData(FileHandle(file))
            fail("expected a failure")
        } catch (e: RuntimeException) {
            assertTrue(e.message != null || e.cause != null)
        }
    }

    /** libGDX's own mesh indices are 16-bit; this module keeps 32-bit indices, so the grid must not wrap at 65,535. */
    @Test
    fun keepsMoreVerticesThanA16BitIndexCanAddress() {
        val grid = GridModel(300)
        val data = AssimpModelLoader().loadData(FileHandle(grid.write()))

        assertEquals("the mesh must not be split", 1, data.meshes.size)
        val mesh = data.meshes.first()
        val floatsPerVertex = mesh.attributes.sumOf { it.numComponents }
        assertEquals(grid.vertexCount, mesh.vertices.size / floatsPerVertex)
        assertTrue(grid.vertexCount > 65_536)

        assertEquals(1, mesh.parts.size)
        val indices = mesh.parts.first().indices
        assertEquals(grid.indexCount, indices.size)
        assertEquals(grid.vertexCount - 1, indices.max())
    }
}
