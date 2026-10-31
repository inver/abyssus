/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.twoQuadData
import net.nevinsky.abyssus.lib.core.assets.writeModelAsset
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.ModelRaySnapshotLoader
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelSnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelSource
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RaySnapshotStore
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.core.testing.sceneWithEcs
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

/** A model drawn by one mesh is its own ray asset: geometry and materials of that mesh only. */
class RayMeshVariantTest {
    init {
        GdxNativesLoader.load()
    }

    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun project(): File = Files.createTempDirectory("ray-variant").toFile().also(dirs::add).also {
        writeModelAsset(it, "duo", twoQuadData())
    }

    private fun store(root: File): RaySnapshotStore<RayModelSnapshot, RayModelSource> {
        val files = testFileLoader(root)
        return RaySnapshotStore(Executor(Runnable::run), testMetaLoader(root, fileLoader = files), ModelRaySnapshotLoader(files, AssimpModelLoader()), "model")
    }

    private val first = ModelKey("duo", "mesh_0").encode()
    private val second = ModelKey("duo", "mesh_1").encode()

    @Test
    fun theStoreReadsOnlyTheNamedMeshAndItsMaterials() {
        val store = store(project())
        val whole = store.acquire("duo").snapshot!!
        val one = store.acquire(first).snapshot
        val other = store.acquire(second).snapshot
        assertEquals(2, whole.meshes.size)
        assertNotNull(one)
        assertEquals(listOf("mesh_0"), one!!.meshes.map { it.id })
        assertEquals(listOf("mesh_1"), other!!.meshes.map { it.id })
        assertEquals(listOf("skin"), one.materials.map { it.id })
        assertEquals(listOf("glass"), other.materials.map { it.id })
        // each mesh is at its own height, so the bounds of one are not the bounds of the other
        fun ys(model: RayModelSnapshot): Set<Float> {
            val mesh = model.meshes.single()
            val stride = mesh.vertexSizeBytes / 4
            val position = mesh.attributes.first { it.usage == com.badlogic.gdx.graphics.VertexAttributes.Usage.Position }.offsetBytes / 4
            val v = mesh.vertices()
            return (0 until v.size / stride).map { v[it * stride + position + 1] }.toSet()
        }
        assertEquals(setOf(0f), ys(one))
        assertEquals(setOf(1f), ys(other))
    }

    @Test
    fun anUnknownMeshFailsOnlyThatLease() {
        val store = store(project())
        val bad = store.acquire(ModelKey("duo", "nope").encode())
        assertNull(bad.snapshot)
        assertNotNull(bad.failure)
        assertNotNull(store.acquire(first).snapshot)
    }

    @Test
    fun theViewHoldsOneLeasePerVariantAndDropsThemWithTheView() {
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val model = rayTestModel()
        val assets = RaySceneAssets(
            { _, name -> opened += name; RayAssetLease({ model }, { null }) { closed += name } },
            { _, _ -> error("no terrain") },
        )
        val content = SceneContent(
            models = listOf(
                AssetPlacement("1", "duo", PlacementTransform.IDENTITY),
                AssetPlacement("2", "duo", PlacementTransform.IDENTITY, "mesh_0"),
                AssetPlacement("3", "duo", PlacementTransform.IDENTITY, "mesh_1"),
                AssetPlacement("4", "duo", PlacementTransform.IDENTITY, "mesh_1"),
            ),
        )
        assets.update(File("p"), content)
        assertEquals(listOf("duo", first, second), opened)
        val ready = assets.poll() as RaySceneAssetState.Ready
        assertEquals(setOf("duo", first, second), ready.models.keys)
        assets.invalidate()
        assertEquals(setOf("duo", first, second), closed.toSet())
    }

    @Test
    fun overridesForMaterialsTheMeshDroppedAreKeptButIgnored() {
        val camera = PerspectiveCamera(61f, 800f, 600f).apply {
            position.set(4f, 5f, 6f); direction.set(-1f, -1f, -1f).nor(); near = .2f; far = 500f; update()
        }
        val ecs = sceneWithEcs("""{"0":{"components":{"RenderComponent":{"rayTracingMaterials":{"dropped":{"transmission":1}}}}}}""").ecs
        fun capture(meshId: String?, key: String): RaySceneConversion {
            val content = SceneContent(models = listOf(AssetPlacement("0", "model", PlacementTransform.IDENTITY, meshId)))
            val params = SceneRenderParams.DEFAULT.copy(content = content, projectDir = File("p"), ecs = ecs)
            return RaySceneSnapshots().capture(params, camera, NO_LIGHTS, RaySceneAssetState.Ready(mapOf(key to rayTestModel(pbr = true)), emptyMap()))
        }
        // the whole model still refuses an override naming a material it does not have
        assertTrue(capture(null, "model") is RaySceneConversion.Fallback)
        val key = ModelKey("model", "mesh").encode()
        assertTrue(capture("mesh", key) is RaySceneConversion.Ready)
        // a scene asking for the mesh waits for the mesh's own asset, not the whole model's
        assertEquals(RaySceneConversion.Preparing(listOf("model:$key")), capture("mesh", "model"))
    }
}
