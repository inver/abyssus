/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray.snapshot

import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RayModelPivotTest {
    private val dirs = mutableListOf<File>()

    @After fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun flatten(nodes: List<ModelNode>): List<ModelNode> =
        nodes.flatMap { listOf(it) + flatten(it.children.orEmpty().toList()) }

    private fun flattenRay(nodes: List<RayModelNode>): List<RayModelNode> =
        nodes.flatMap { listOf(it) + flattenRay(it.children) }

    @Test
    fun aRePivotedNodeHasTheSameTransformInTheSnapshotAndTheRasterModel() {
        GdxNativesLoader.load()
        val source =
            File(
                System.getProperty("abyssus.testData"),
                "project/Untitled/assets/model_29e9be61-6594-4f82-a6cf-44ccf09f71fb",
            )
        val root = Files.createTempDirectory("ray-pivot").toFile().also(dirs::add)
        assertTrue(source.copyRecursively(File(root, "assets/model")))
        val files = FileLoader(root)
        val assimp = AssimpModelLoader()
        val plain = assimp.loadData(com.badlogic.gdx.files.FileHandle(File(root, "assets/model/model.gltf")))
        val target = flatten(plain.nodes.toList()).first { it.parts?.isNotEmpty() == true }.id!!
        val meta = File(root, "assets/model/meta.json")
        meta.writeText(
            meta.readText().replace(
                Regex("\"materials\"\\s*:\\s*\\[\\s*]"),
                "\"materials\": [], \"parts\": {\"$target\": {\"pivot\": {\"position\": {\"x\": 0.5, \"y\": 0.25, \"z\": 0}," +
                    " \"rotation\": {\"x\": 0, \"y\": 0, \"z\": 0.7071068, \"w\": 0.7071068}}}}",
            )
        )
        val metas = testMetaLoader(root, fileLoader = files)

        val loader = ModelLoader(metas, assimp, files, null, false)
        val staged = checkNotNull(loader.prepare("model")).staged
        val raster = flatten(staged.data.nodes.toList()).first { it.id == target }
        loader.discardStaged(staged)

        val snapshot = ModelRaySnapshotLoader(files, assimp).load(checkNotNull(metas.loadBaseMeta("model")))
        val ray = flattenRay(snapshot.nodes).first { it.id == target }
        assertEquals(raster.translation.x, ray.translation.x, 0f)
        assertEquals(raster.translation.y, ray.translation.y, 0f)
        assertEquals(raster.translation.z, ray.translation.z, 0f)
        assertEquals(raster.rotation.z, ray.rotation.z, 0f)
        assertEquals(raster.rotation.w, ray.rotation.w, 0f)
        // the file's own rotation of that node is not what the snapshot carries
        val unpivoted = flatten(plain.nodes.toList()).first { it.id == target }
        assertNotEquals(unpivoted.translation?.x ?: 0f, ray.translation.x, 1e-6f)
    }
}
