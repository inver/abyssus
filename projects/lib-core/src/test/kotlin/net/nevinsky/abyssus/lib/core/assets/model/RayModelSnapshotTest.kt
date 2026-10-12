/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.loading.RaySnapshotLease
import net.nevinsky.abyssus.lib.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.lib.core.assets.loading.RaySnapshotStore
import net.nevinsky.abyssus.lib.core.assets.model.ModelLoader
import net.nevinsky.abyssus.lib.core.assets.model.ModelRaySnapshotLoader
import net.nevinsky.abyssus.lib.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.lib.core.assets.model.RayModelSource
import net.nevinsky.abyssus.lib.core.assets.model.RayTextureColorSpace
import net.nevinsky.abyssus.lib.core.assets.model.RayTextureFilter
import net.nevinsky.abyssus.lib.core.assets.model.RayTextureWrap
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

class RayModelSnapshotTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    /** A project with one model asset `model` that only has a `meta.json`: enough for a store to resolve the meta. */
    private val project: File = Files.createTempDirectory("ray-snapshot-test").toFile().also { dir ->
        dirs += dir
        File(dir, "assets/model").mkdirs()
        File(dir, "assets/model/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"MODEL","additional":{"file":"model.gltf"}}"""
        )
    }
    private val files = FileLoader(project)
    private val metas = testMetaLoader(project, fileLoader = files)
    private val capturer = ModelRaySnapshotLoader(files, AssimpModelLoader())

    /** Reads through [read]; captures like the real loader. */
    private inner class FakeLoader(val read: () -> RayModelSnapshot?) : RaySnapshotLoader<RayModelSnapshot, RayModelSource> {
        override fun load(meta: AssetMeta<Any>): RayModelSnapshot? = read()
        override fun capture(source: RayModelSource): RayModelSnapshot = capturer.capture(source)
    }

    private fun store(queue: ArrayDeque<Runnable>, loader: RaySnapshotLoader<RayModelSnapshot, RayModelSource>, maxBytes: Long = 256L * 1024 * 1024) =
        RaySnapshotStore(Executor { queue.add(it) }, metas, loader, "model", maxBytes)

    private fun capture(data: ModelData, images: Map<String, Pixmap> = emptyMap()) = capturer.capture(
        RayModelSource(
            data,
            images
        )
    )

    @Test fun keepsThirtyTwoBitIndicesAndCopiesGeometryAndNodeMaterials() {
        val data = data(70002)
        val material = data.materials[0] as PbrModelMaterial
        val snapshot = capture(data)
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
            capture(data, mapOf("shared.png" to image))
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
        val snapshot = capture(data())
        var reads = 0
        val store = store(queue, FakeLoader { reads++; snapshot })
        val first = store.acquire("model")
        val second = store.acquire("model")
        assertNull(first.snapshot)
        queue.removeFirst().run()
        assertSame(first.snapshot, second.snapshot)
        assertEquals(1, reads)
        first.close(); first.close()
        assertNull(first.snapshot)
        assertSame(snapshot, second.snapshot)
        second.close()
        assertEquals(0L, store.retainedBytes)
        val next = store.acquire("model")
        queue.removeFirst().run()
        assertEquals(2, reads)
        next.close()
    }

    @Test fun enablingAfterRasterPreparationAcquiresCpuDataWithoutReloadingOrDisposingGpuAssets() {
        val queue = ArrayDeque<Runnable>()
        var reads = 0
        var captures = 0
        val counting = object : RaySnapshotLoader<RayModelSnapshot, RayModelSource> {
            override fun load(meta: AssetMeta<Any>): RayModelSnapshot { reads++; return capture(data()) }
            override fun capture(source: RayModelSource): RayModelSnapshot { captures++; return capturer.capture(source) }
        }
        val store = store(queue, counting)
        store.preparation("model")?.offer(RayModelSource(data(), emptyMap())) // raster is already cached; there is no CPU interest yet
        assertEquals(0, captures)
        val lease = store.acquire("model")
        queue.removeFirst().run()
        assertNotNull(lease.snapshot)
        assertEquals(1, reads)
        lease.close()
    }

    @Test fun demandedCpuDataIsCapturedBeforePreparationImagesCanBeDisposed() {
        val queue = ArrayDeque<Runnable>()
        var reads = 0
        val store = store(queue, FakeLoader { reads++; error("A captured snapshot must be reused") })
        val lease = store.acquire("model")
        store.preparation("model")!!.offer(RayModelSource(data(), emptyMap()))
        assertNotNull(lease.snapshot)
        queue.removeFirst().run()
        assertEquals(0, reads)
        lease.close()
    }

    @Test fun theRealModelLoaderOffersPreparedDataAndLateAcquisitionUsesTheSameCpuReader() {
        GdxNativesLoader.load()
        val source = File(System.getProperty("abyssus.testData"), "project/Untitled/assets/model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")
        val root = Files.createTempDirectory("ray-model-snapshot").toFile().also(dirs::add)
        assertTrue(source.copyRecursively(File(root, "assets/model")))
        val files = FileLoader(root)
        val metas = testMetaLoader(root, fileLoader = files)
        val queue = ArrayDeque<Runnable>()
        val assimp = AssimpModelLoader()
        val real = ModelRaySnapshotLoader(files, assimp)
        var reads = 0
        val counting = object : RaySnapshotLoader<RayModelSnapshot, RayModelSource> {
            override fun load(meta: AssetMeta<Any>): RayModelSnapshot { reads++; return real.load(meta) }
            override fun capture(source: RayModelSource) = real.capture(source)
        }
        val store = RaySnapshotStore(Executor { queue.add(it) }, metas, counting, "model")
        val lease = store.acquire("model")
        val loader = ModelLoader(metas, assimp, files, store)
        val prepared = checkNotNull(loader.prepare("model")).staged
        loader.discardStaged(prepared)
        assertNotNull(lease.snapshot)
        queue.removeFirst().run()
        assertEquals(0, reads)
        assertTrue(lease.snapshot!!.meshes.isNotEmpty())
        lease.close()
        // GPU/prepared objects are gone; a late CPU request still loads the same geometry without GL.
        val late = store.acquire("model")
        queue.removeFirst().run()
        assertNotNull(late.snapshot)
        assertEquals(1, reads)
        late.close()
        assertEquals(0L, store.retainedBytes)
    }

    @Test fun cancellationDropsLateResultsAndDoesNotRetainSnapshotsOrStartUnwantedWork() {
        val queue = ArrayDeque<Runnable>()
        lateinit var lease: RaySnapshotLease<RayModelSnapshot>
        var reads = 0
        val store = store(queue, FakeLoader { reads++; lease.close(); capture(data()) })
        lease = store.acquire("model")
        queue.removeFirst().run()
        assertNull(lease.snapshot)
        assertEquals(0L, store.retainedBytes)
        val cancelled = store.acquire("model")
        cancelled.close()
        queue.removeFirst().run()
        assertEquals(1, reads)
    }

    @Test fun aCancelledRasterPreparationCannotFillAReplacementLease() {
        val queue = ArrayDeque<Runnable>()
        val freshData = data().apply { meshes[0].vertices[0] = 2f }
        val store = store(queue, FakeLoader { capture(freshData) })
        val cancelled = store.acquire("model")
        val oldPreparation = checkNotNull(store.preparation("model"))
        val oldData = data().apply { meshes[0].vertices[0] = 1f }
        cancelled.close()
        val replacement = store.acquire("model")
        oldPreparation.offer(RayModelSource(oldData, emptyMap()))
        assertNull("Old raster preparation must not attach to a new CPU request", replacement.snapshot)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals(2f, replacement.snapshot!!.meshes[0].vertices()[0], 0f)
        replacement.close()
    }

    @Test fun cancellationPropagatesAndResourceLimitsAreExplicitFailures() {
        val queue = ArrayDeque<Runnable>()
        val cancelled = store(queue, FakeLoader { throw CancellationException("cancelled") })
        val first = cancelled.acquire("model")
        assertThrows(CancellationException::class.java) { queue.removeFirst().run() }
        first.close()
        val bounded = store(queue, FakeLoader { capture(data()) }, maxBytes = 1)
        val second = bounded.acquire("model")
        queue.removeFirst().run()
        assertNull(second.snapshot)
        assertNotNull(second.failure)
        assertEquals(0L, bounded.retainedBytes)
        second.close()
    }

    @Test fun invalidationAcrossViewsReleasesRetainedBytesAndRejectsOldPreparations() {
        val queue = ArrayDeque<Runnable>()
        var revision = 1f
        val store = store(queue, FakeLoader { capture(data().apply { meshes[0].vertices[0] = revision }) })
        val first = store.acquire("model"); val second = store.acquire("model")
        val oldPreparation = store.preparation("model")!!
        queue.removeFirst().run()
        assertTrue(store.retainedBytes > 0)
        store.invalidate("model")
        assertNull(first.snapshot); assertNull(second.snapshot)
        assertNotNull(second.failure); assertEquals(0L, store.retainedBytes)
        revision = 2f
        val replacement = store.acquire("model")
        oldPreparation.offer(RayModelSource(data(), emptyMap()))
        assertNull(replacement.snapshot)
        queue.removeFirst().run()
        assertEquals(2f, replacement.snapshot!!.meshes[0].vertices()[0], 0f)
        val retained = store.retainedBytes
        first.close(); second.close()
        assertEquals(retained, store.retainedBytes)
        replacement.close(); assertEquals(0L, store.retainedBytes)
    }

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
