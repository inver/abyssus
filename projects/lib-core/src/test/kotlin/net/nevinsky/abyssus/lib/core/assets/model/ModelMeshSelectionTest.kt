/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.assets.loading.exception.UnknownMeshException
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.twoQuadData
import net.nevinsky.abyssus.lib.core.assets.writeModelAsset
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelMeshSelectionTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun project(): File = Files.createTempDirectory("mesh-select").toFile().also(dirs::add).also {
        writeModelAsset(it, "duo", twoQuadData())
    }

    private fun loader(root: File): ModelLoader {
        GdxNativesLoader.load()
        val files = testFileLoader(root)
        return ModelLoader(testMetaLoader(root, fileLoader = files), AssimpModelLoader(), files, decodeTextures = false)
    }

    private fun prepared(root: File, name: String): ModelData = checkNotNull(loader(root).prepare(name)).staged.data

    private fun meshIds(data: ModelData) = data.meshes.map { it.id }

    @Test
    fun theWholeModelHoldsBothMeshes() {
        val data = prepared(project(), "duo")
        assertEquals(2, data.meshes.size)
        assertTrue(data.materials.size >= 2)
        assertEquals(2, nodeParts(data).size)
    }

    private fun nodeParts(data: ModelData): List<ModelNodePart> {
        fun walk(node: ModelNode): List<ModelNodePart> = node.parts.orEmpty().toList() + node.children.orEmpty().flatMap(::walk)
        return data.nodes.flatMap(::walk)
    }

    @Test
    fun aVariantKeepsOnlyItsMeshAndMaterials() {
        val root = project()
        val ids = meshIds(prepared(root, "duo"))
        for (mesh in ids) {
            val data = prepared(root, ModelKey("duo", mesh).encode())
            assertEquals(listOf(mesh), meshIds(data))
            assertEquals(1, data.materials.size)
            // The node tree is kept; only parts that pointed at dropped meshes are pruned.
            val parts = nodeParts(data)
            assertEquals(1, parts.size)
            assertEquals(data.meshes.single().parts.single().id, parts.single().meshPartId)
            assertEquals(data.materials.single().id, parts.single().materialId)
        }
    }

    @Test
    fun anUnknownMeshFailsWithTheCurrentMeshIds() {
        val root = project()
        val ids = meshIds(prepared(root, "duo"))
        val thrown = assertThrows(UnknownMeshException::class.java) { loader(root).prepare(ModelKey("duo", "nope").encode()) }
        assertEquals("nope", thrown.meshId)
        assertEquals(ids, thrown.available)
        assertTrue(thrown.message!!.contains(ids.first()!!))
        // The base model still loads.
        assertEquals(2, prepared(root, "duo").meshes.size)
    }

    private fun tree(node: ModelNode): String = node.id + node.children.orEmpty().joinToString(",", "(", ")") { tree(it) }

    @Test
    fun aSkinnedVariantKeepsTheNodeTreeBonesAndAnimations() {
        val root = Files.createTempDirectory("mesh-skin").toFile().also(dirs::add)
        val folder = File(root, "assets/skinned").also { it.mkdirs() }
        checkNotNull(javaClass.getResourceAsStream("/model/animated.gltf")).use { input ->
            File(folder, "model.gltf").outputStream().use { input.copyTo(it) }
        }
        File(folder, "meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1,"uuid":"29e9be61-6594-4f82-a6cf-44ccf09f71fb","type":"MODEL","additional":{"file":"model.gltf","format":"GLTF","binary":false,"materials":[]}}"""
        )
        val whole = prepared(root, "skinned")
        val mesh = whole.meshes.single().id!!
        val wholeTree = whole.nodes.map(::tree)
        val animations = whole.animations.map { it.id }
        val boneNodes = nodeParts(whole).flatMap { it.bones?.keys()?.toList().orEmpty() }
        assertTrue("fixture is skinned", boneNodes.isNotEmpty())

        val variant = prepared(root, ModelKey("skinned", mesh).encode())
        assertEquals(wholeTree, variant.nodes.map(::tree))
        assertEquals(animations, variant.animations.map { it.id })
        assertTrue(animations.isNotEmpty())
        assertEquals(boneNodes, nodeParts(variant).flatMap { it.bones?.keys()?.toList().orEmpty() })
        val nodeIds = variant.nodes.flatMap(::allNodes).map { it.id }.toSet()
        assertTrue(boneNodes.all { it in nodeIds })
        assertTrue(variant.animations.flatMap { it.nodeAnimations }.all { it.nodeId in nodeIds })
    }

    private fun allNodes(node: ModelNode): List<ModelNode> = listOf(node) + node.children.orEmpty().flatMap(::allNodes)
}
