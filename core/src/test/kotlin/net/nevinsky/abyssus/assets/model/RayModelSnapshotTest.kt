/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.CancellationException

class RayModelSnapshotTest {
    @Test fun keepsThirtyTwoBitIndicesAndCopiesGeometryAndNodeMaterials() {
        val data = data(70002)
        val material = data.materials[0] as PbrModelMaterial
        val snapshot = RayModelSnapshotReader(AssimpModelLoader()).capture(data, emptyMap())
        val vertices = snapshot.meshes[0].vertices()
        assertArrayEquals(intArrayOf(0, 70000, 70001), snapshot.meshes[0].parts[0].indices())
        data.meshes[0].vertices[0] = 50f
        data.meshes[0].parts[0].indices[1] = 1
        material.baseColor!!.r = 0.25f
        vertices[0] = 60f
        assertEquals(0f, snapshot.meshes[0].vertices()[0], 0f)
        assertEquals(70000, snapshot.meshes[0].parts[0].indices()[1])
        assertEquals(1f, snapshot.materials[0].baseColor!!.r, 0f)
        assertEquals("part", snapshot.nodes[0].parts[0].meshPartId)
        assertEquals("material", snapshot.nodes[0].parts[0].materialId)
        assertEquals(12, snapshot.meshes[0].vertexSizeBytes)
        assertEquals(0, snapshot.meshes[0].attributes[0].offsetBytes)
    }

    @Test fun copiesDecodedRgbaAndRetainsRasterColorSpaceSamplerAndUvConventions() {
        GdxNativesLoader.load()
        val data = data()
        val material = data.materials[0] as PbrModelMaterial
        material.textures = com.badlogic.gdx.utils.Array<ModelTexture>().apply {
            add(ModelTexture().apply {
                fileName = "shared.png"; usage = ModelTexture.USAGE_DIFFUSE
                uvTranslation = Vector2(0.25f, 0.5f); uvScaling = Vector2(2f, 3f)
            })
            add(ModelTexture().apply { fileName = "shared.png"; usage = PbrModelMaterial.USAGE_METALLIC_ROUGHNESS })
        }
        val image = Pixmap(1, 2, Pixmap.Format.RGBA8888)
        val snapshot = try {
            image.setBlending(Pixmap.Blending.None)
            image.drawPixel(0, 0, 0x804020ff.toInt())
            image.drawPixel(0, 1, 0x10203040)
            RayModelSnapshotReader(AssimpModelLoader()).capture(data, mapOf("shared.png" to image))
        } finally { image.dispose() }
        assertEquals(1, snapshot.images.size)
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x40, 0x20, 0xff.toByte(), 0x10, 0x20, 0x30, 0x40), snapshot.images["shared.png"]!!.rgba())
        val binding = snapshot.materials[0].textures[0]
        assertEquals(RayTextureColorSpace.LINEAR, binding.colorSpace)
        assertEquals(RayTextureFilter.LINEAR, binding.sampler.minFilter)
        assertEquals(RayTextureWrap.REPEAT, binding.sampler.wrapU)
        assertFalse(binding.sampler.mipmaps)
        assertEquals(0.25f, binding.offsetU, 0f)
        assertEquals(3f, binding.scaleV, 0f)
        val bytes = snapshot.images["shared.png"]!!.rgba()
        bytes[0] = 0
        assertEquals(0x80.toByte(), snapshot.images["shared.png"]!!.rgba()[0])
        assertEquals(RayTextureColorSpace.LINEAR, snapshot.materials[0].textures[1].colorSpace)
    }

    @Test fun repeatedInstancesAndViewsShareOneOptionalSnapshotUntilTheLastLeaseCloses() {
        val queue = ArrayDeque<Runnable>()
        val snapshot = RayModelSnapshotReader(AssimpModelLoader()).capture(data(), emptyMap())
        var reads = 0
        val store = RayModelSnapshots(Executor { queue.add(it) }, { _, _ -> reads++; snapshot }, { _, _ -> snapshot })
        val files = files()
        val first = store.acquire(files, "model")
        val second = store.acquire(files, "model")
        assertNull(first.snapshot)
        queue.removeFirst().run()
        assertSame(first.snapshot, second.snapshot)
        assertEquals(1, reads)
        first.close(); first.close()
        assertNull(first.snapshot)
        assertSame(snapshot, second.snapshot)
        second.close()
        assertEquals(0L, store.retainedBytes)
        val next = store.acquire(files, "model")
        queue.removeFirst().run()
        assertEquals(2, reads)
        next.close()
    }

    @Test fun enablingAfterRasterPreparationAcquiresCpuDataWithoutReloadingOrDisposingGpuAssets() {
        val queue = ArrayDeque<Runnable>()
        var reads = 0
        var captures = 0
        val reader = RayModelSnapshotReader(AssimpModelLoader())
        val store = RayModelSnapshots(Executor { queue.add(it) }, { _, _ -> reads++; reader.capture(data(), emptyMap()) },
            { data, images -> captures++; reader.capture(data, images) })
        store.preparation(files(), "model")?.offer(data(), emptyMap()) // raster is already cached; there is no CPU interest yet
        assertEquals(0, captures)
        val lease = store.acquire(files(), "model")
        queue.removeFirst().run()
        assertNotNull(lease.snapshot)
        assertEquals(1, reads)
        lease.close()
    }

    @Test fun demandedCpuDataIsCapturedBeforePreparationImagesCanBeDisposed() {
        val queue = ArrayDeque<Runnable>()
        val reader = RayModelSnapshotReader(AssimpModelLoader())
        var reads = 0
        val store = RayModelSnapshots(Executor { queue.add(it) }, { _, _ -> reads++; error("A captured snapshot must be reused") }, reader::capture)
        val lease = store.acquire(files(), "model")
        store.preparation(files(), "model")!!.offer(data(), emptyMap())
        assertNotNull(lease.snapshot)
        queue.removeFirst().run()
        assertEquals(0, reads)
        lease.close()
    }

    @Test fun theMaterialTableListsIdsInModelOrderWithoutImages() {
        val source = File(System.getProperty("abyssus.testData"), "project/Untitled/assets/model_fc33e1f1-015b-4524-9b10-aa417acd273c")
        val root = java.nio.file.Files.createTempDirectory("ray-model-materials").toFile()
        try {
            // the model's large TGA texture is left behind: the table must not need it
            File(root, "assets/model").mkdirs()
            source.listFiles { f -> f.extension != "tga" }!!.forEach { it.copyTo(File(root, "assets/model/${it.name}")) }
            val reader = RayModelSnapshotReader(AssimpModelLoader())
            val materials = reader.materials(AssetFiles(root, JsonProcessor()), "model")!!
            // the loader's identifiers, which scene optical overrides are keyed by, not the glTF names
            assertEquals(listOf("mat00", "mat01", "glass", "material_3"), materials.map { it.id })
            assertTrue(materials.all { it.pbr })
            assertNull(reader.materials(AssetFiles(root, JsonProcessor()), "missing"))
        } finally { root.deleteRecursively() }
    }

    @Test fun theRealModelLoaderOffersPreparedDataAndLateAcquisitionUsesTheSameCpuReader() {
        GdxNativesLoader.load()
        val source = File(System.getProperty("abyssus.testData"), "project/Untitled/assets/model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")
        val root = java.nio.file.Files.createTempDirectory("ray-model-snapshot").toFile()
        try {
            assertTrue(source.copyRecursively(File(root, "assets/model")))
            val files = AssetFiles(root, JsonProcessor())
            val queue = ArrayDeque<Runnable>()
            val reader = RayModelSnapshotReader(AssimpModelLoader())
            var reads = 0
            val store = RayModelSnapshots(Executor { queue.add(it) }, { f, n -> reads++; reader.read(f, n) }, reader::capture)
            val lease = store.acquire(files, "model")
            val loader = ModelLoader(AssimpModelLoader(), store)
            val prepared = checkNotNull(loader.prepare(files, "model"))
            loader.discard(prepared)
            assertNotNull(lease.snapshot)
            queue.removeFirst().run()
            assertEquals(0, reads)
            assertTrue(lease.snapshot!!.meshes.isNotEmpty())
            lease.close()
            // GPU/prepared objects are gone; a late CPU request still loads the same geometry without GL.
            val late = store.acquire(files, "model")
            queue.removeFirst().run()
            assertNotNull(late.snapshot)
            assertEquals(1, reads)
            late.close()
            assertEquals(0L, store.retainedBytes)
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationDropsLateResultsAndDoesNotRetainSnapshotsOrStartUnwantedWork() {
        val queue = ArrayDeque<Runnable>()
        val reader = RayModelSnapshotReader(AssimpModelLoader())
        lateinit var lease: RayModelSnapshotLease
        var reads = 0
        val store = RayModelSnapshots(Executor { queue.add(it) }, { _, _ ->
            reads++; lease.close(); reader.capture(data(), emptyMap())
        }, reader::capture)
        lease = store.acquire(files(), "model")
        queue.removeFirst().run()
        assertNull(lease.snapshot)
        assertEquals(0L, store.retainedBytes)
        val cancelled = store.acquire(files(), "model")
        cancelled.close()
        queue.removeFirst().run()
        assertEquals(1, reads)
    }

    @Test fun aCancelledRasterPreparationCannotFillAReplacementLease() {
        val queue = ArrayDeque<Runnable>()
        val reader = RayModelSnapshotReader(AssimpModelLoader())
        val freshData = data().apply { meshes[0].vertices[0] = 2f }
        val store = RayModelSnapshots(Executor { queue.add(it) }, { _, _ -> reader.capture(freshData, emptyMap()) }, reader::capture)
        val cancelled = store.acquire(files(), "model")
        val oldPreparation = checkNotNull(store.preparation(files(), "model"))
        val oldData = data().apply { meshes[0].vertices[0] = 1f }
        cancelled.close()
        val replacement = store.acquire(files(), "model")
        oldPreparation.offer(oldData, emptyMap())
        assertNull("Old raster preparation must not attach to a new CPU request", replacement.snapshot)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals(2f, replacement.snapshot!!.meshes[0].vertices()[0], 0f)
        replacement.close()
    }

    @Test fun cancellationPropagatesAndResourceLimitsAreExplicitFailures() {
        val reader = RayModelSnapshotReader(AssimpModelLoader())
        val queue = ArrayDeque<Runnable>()
        val cancelled = RayModelSnapshots(Executor { queue.add(it) }, { _, _ -> throw CancellationException("cancelled") }, reader::capture)
        val first = cancelled.acquire(files(), "model")
        assertThrows(CancellationException::class.java) { queue.removeFirst().run() }
        first.close()
        val bounded = RayModelSnapshots(Executor { queue.add(it) }, { _, _ -> reader.capture(data(), emptyMap()) }, reader::capture, maxBytes = 1)
        val second = bounded.acquire(files(), "model")
        queue.removeFirst().run()
        assertNull(second.snapshot)
        assertNotNull(second.failure)
        assertEquals(0L, bounded.retainedBytes)
        second.close()
    }

    @Test fun invalidationAcrossViewsReleasesRetainedBytesAndRejectsOldPreparations() {
        val queue = ArrayDeque<Runnable>()
        val reader = RayModelSnapshotReader(AssimpModelLoader())
        var revision = 1f
        val store = RayModelSnapshots(Executor { queue.add(it) }, { _, _ ->
            reader.capture(data().apply { meshes[0].vertices[0] = revision }, emptyMap())
        }, reader::capture)
        val files = files()
        val first = store.acquire(files, "model"); val second = store.acquire(files, "model")
        val oldPreparation = store.preparation(files, "model")!!
        queue.removeFirst().run()
        assertTrue(store.retainedBytes > 0)
        store.invalidate(files, "model")
        assertNull(first.snapshot); assertNull(second.snapshot)
        assertNotNull(second.failure); assertEquals(0L, store.retainedBytes)
        revision = 2f
        val replacement = store.acquire(files, "model")
        oldPreparation.offer(data(), emptyMap())
        assertNull(replacement.snapshot)
        queue.removeFirst().run()
        assertEquals(2f, replacement.snapshot!!.meshes[0].vertices()[0], 0f)
        val retained = store.retainedBytes
        first.close(); second.close()
        assertEquals(retained, store.retainedBytes)
        replacement.close(); assertEquals(0L, store.retainedBytes)
    }

    private fun files() = AssetFiles(File("/tmp/ray-snapshot-test"), JsonProcessor())
    private fun data(vertexCount: Int = 3) = ModelData().apply {
        meshes.add(ModelMesh().apply {
            id = "mesh"; attributes = arrayOf(VertexAttribute.Position()); vertices = FloatArray(vertexCount * 3)
            parts = arrayOf(ModelMeshPart().apply {
                id = "part"; primitiveType = GL20.GL_TRIANGLES; indices = intArrayOf(0, vertexCount - 2, vertexCount - 1)
            })
        })
        materials.add(PbrModelMaterial().apply { id = "material"; baseColor = Color.WHITE.cpy(); metallic = 0.5f; roughness = 0.25f })
        nodes.add(ModelNode().apply { id = "node"; parts = arrayOf(ModelNodePart().apply { meshPartId = "part"; materialId = "material" }) })
    }
}
