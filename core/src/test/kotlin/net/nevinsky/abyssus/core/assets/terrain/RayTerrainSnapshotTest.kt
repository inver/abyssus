/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.core.assets.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotStore
import net.nevinsky.abyssus.core.assets.model.RayTextureColorSpace
import net.nevinsky.abyssus.core.assets.model.RayTextureFilter
import net.nevinsky.abyssus.core.assets.model.RayTextureWrap
import net.nevinsky.abyssus.core.assets.testMetaLoader
import net.nevinsky.abyssus.core.assets.texture.TextureLoader
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

class RayTerrainSnapshotTest {
    private val roots = mutableListOf<File>()

    @After
    fun cleanUp() = roots.forEach(File::deleteRecursively)

    private class Graph(val root: File) {
        val files = FileLoader(root)
        val metas: AssetMetaLoader = testMetaLoader(root, fileLoader = files)

        /** The CPU snapshot loader: it reads heights through a terrain loader and splat images through a texture loader. */
        fun snapshotLoader(maxBytes: Long = 128L * 1024 * 1024) =
            TerrainRaySnapshotLoader(TerrainLoader(files, metas), TextureLoader(files, metas), maxBytes)
    }

    private fun graph(): Graph = Graph(terrainProject().also(roots::add))
    private fun data() = TerrainData(2, floatArrayOf(0f, 2f, 0f, 2f), 10, 4f)

    private fun TerrainRaySnapshotLoader.capture(data: TerrainData, images: Map<String, Pixmap> = emptyMap()) =
        snapshot(data, images)

    private fun store(
        graph: Graph,
        queue: ArrayDeque<Runnable>,
        loader: RaySnapshotLoader<RayTerrainSnapshot, Nothing> = graph.snapshotLoader(),
        maxBytes: Long = 256L * 1024 * 1024,
    ) = RaySnapshotStore(Executor { queue.add(it) }, graph.metas, loader, "terrain", maxBytes)

    @Test fun immutableLocalGeometryPreservesRasterTrianglesNormalsUvsAndTransformedInstances() {
        val data = data()
        val snapshot = graph().snapshotLoader().capture(data)
        assertArrayEquals(intArrayOf(3, 1, 0, 0, 2, 3), snapshot.indices())
        assertArrayEquals(data.vertices(), snapshot.vertices(), 0f)
        val vertices = snapshot.vertices()
        val world = Matrix4().setToTranslation(3f, 7f, -5f).scale(2f, 3f, 4f)
        assertEquals(Vector3(23f, 13f, -5f), Vector3(vertices[8], vertices[9], vertices[10]).mul(world))
        val normal = Vector3(vertices[11], vertices[12], vertices[13]).mul(Matrix3().set(world).inv().transpose()).nor()
        assertTrue(normal.epsilonEquals(Vector3(-0.5f, 1f / 3f, 0f).nor(), 1e-6f))
        data.heights.fill(99f); snapshot.heights().fill(33f); snapshot.vertices().fill(33f); snapshot.indices().fill(33)
        assertArrayEquals(floatArrayOf(0f, 2f, 0f, 2f), snapshot.heights(), 0f)
        assertEquals(10f, snapshot.vertices()[8], 0f)
        assertEquals(4f, snapshot.vertices()[14], 0f)
        assertEquals(3, snapshot.indices()[0])
        assertEquals(2, snapshot.resolution)
        assertEquals(1f, vertices[8] / snapshot.size, 0f) // local splat UV, independent of instance scale
        assertEquals(4f, snapshot.uv, 0f)
    }

    @Test fun capturesSequentialSplatBlendInputsAndDistinctSamplerConventions() {
        GdxNativesLoader.load()
        val images = linkedMapOf(SPLAT_MAP to pixel(0x80800000.toInt()), "splatBase" to pixel(0x000000ff),
            "splatR" to pixel(0xff0000ff.toInt()), "splatG" to pixel(0x00ff00ff))
        val snapshot = try { graph().snapshotLoader().capture(data(), images) } finally { images.values.forEach(Pixmap::dispose) }
        val splat = snapshot.splatMap!!
        assertEquals(RayTextureWrap.CLAMP_TO_EDGE, splat.sampler.wrapU)
        assertEquals(RayTextureFilter.LINEAR, splat.sampler.minFilter)
        assertFalse(splat.sampler.mipmaps)
        assertEquals(listOf("splatBase", "splatR", "splatG"), snapshot.layers.keys.toList())
        snapshot.layers.values.forEach {
            assertEquals(RayTextureColorSpace.LINEAR, it.colorSpace)
            assertEquals(RayTextureWrap.REPEAT, it.sampler.wrapU)
            assertEquals(RayTextureFilter.MIPMAP_LINEAR_LINEAR, it.sampler.minFilter)
            assertEquals(RayTextureFilter.LINEAR, it.sampler.magFilter)
            assertTrue(it.sampler.mipmaps)
        }
        // Reconstruct sequential shader mixes from retained bytes; channels are not normalized weights.
        val weights = channels(splat.image.rgba())
        var color = channels(snapshot.layers.getValue("splatBase").image.rgba())
        listOf("splatR", "splatG", "splatB", "splatA").forEachIndexed { index, key ->
            snapshot.layers[key]?.let { layer ->
                val target = channels(layer.image.rgba())
                color = color.indices.map { color[it] * (1f - weights[index]) + target[it] * weights[index] }
            }
        }
        assertEquals(0.24999616f, color[0], 1e-6f); assertEquals(0.5019608f, color[1], 1e-6f)
        assertEquals(0f, color[2], 0f); assertEquals(1f, color[3], 0f)
        splat.image.rgba().fill(0)
        assertEquals(128, splat.image.rgba()[0].toInt() and 255)
    }

    @Test fun failedAndMissingLayerImagesMatchRasterOmissionsWithoutGl() {
        val graph = graph()
        val snapshot = graph.snapshotLoader().load(graph.metas.loadBaseMeta("terrain")!!)!!
        assertNull(snapshot.splatMap)
        assertEquals(listOf("splatBase"), snapshot.layers.keys.toList())
        assertArrayEquals(byteArrayOf(32, 64, 128.toByte(), 255.toByte()), snapshot.layers.getValue("splatBase").image.rgba())
        assertEquals(6, snapshot.indices().size); assertEquals(4f, snapshot.uv, 0f)
    }

    @Test fun leasesShareOneReadAndReleaseTheBytesWithTheLastClose() {
        val graph = graph()
        val queue = ArrayDeque<Runnable>(); var reads = 0
        val real = graph.snapshotLoader()
        val counting = object : RaySnapshotLoader<RayTerrainSnapshot, Nothing> {
            override fun load(meta: AssetMeta<Any>): RayTerrainSnapshot { reads++; return real.load(meta) }
        }
        val store = store(graph, queue, counting)
        val lease = store.acquire("terrain"); val second = store.acquire("terrain")
        queue.removeFirst().run()
        assertNotNull(lease.snapshot); assertSame(lease.snapshot, second.snapshot)
        assertArrayEquals(byteArrayOf(32, 64, 128.toByte(), 255.toByte()), lease.snapshot!!.layers.getValue("splatBase").image.rgba())
        assertEquals(1, reads)
        lease.close(); assertTrue(store.retainedBytes > 0)
        second.close(); assertEquals(0L, store.retainedBytes)
        val late = store.acquire("terrain")
        queue.removeFirst().run(); assertNotNull(late.snapshot); assertEquals(2, reads)
        late.close()
    }

    @Test fun aClosedLeaseNeverStartsItsReadAndResourceLimitsAreExplicitFailures() {
        val graph = graph()
        val queue = ArrayDeque<Runnable>(); var reads = 0
        val real = graph.snapshotLoader()
        val store = store(graph, queue, object : RaySnapshotLoader<RayTerrainSnapshot, Nothing> {
            override fun load(meta: AssetMeta<Any>): RayTerrainSnapshot { reads++; return real.load(meta) }
        })
        store.acquire("terrain").close()
        queue.removeFirst().run()
        assertEquals("a read nobody wants is not started", 0, reads)
        assertEquals(0L, store.retainedBytes)
        val bounded = store(graph, queue, maxBytes = 1)
        val limited = bounded.acquire("terrain"); queue.removeFirst().run()
        assertNull(limited.snapshot); assertNotNull(limited.failure); limited.close()
        assertThrows(IllegalArgumentException::class.java) { graph.snapshotLoader(maxBytes = 1).capture(data()) }
    }

    @Test fun invalidationAcrossViewsCannotRetainOrPublishTheDeletedRevision() {
        val graph = graph()
        val queue = ArrayDeque<Runnable>()
        val store = store(graph, queue)
        val first = store.acquire("terrain"); val otherView = store.acquire("terrain")
        queue.removeFirst().run()
        assertTrue(store.retainedBytes > 0)
        store.invalidate("terrain")
        assertNull(otherView.snapshot); assertNotNull(otherView.failure)
        assertEquals(0L, store.retainedBytes)
        val next = store.acquire("terrain")
        assertNull(next.snapshot)
        queue.removeFirst().run()
        val bytes = store.retainedBytes
        assertTrue(bytes > 0)
        first.close(); otherView.close()
        assertEquals("closing the old leases does not free the new revision", bytes, store.retainedBytes)
        next.close(); assertEquals(0L, store.retainedBytes)
    }

    private fun channels(bytes: ByteArray) = bytes.map { (it.toInt() and 255) / 255f }
    private fun pixel(rgba: Int) = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply {
        setBlending(Pixmap.Blending.None)
        drawPixel(0, 0, rgba)
    }

    /**
     * A terrain asset whose splat fields hold texture `uuid`s: one resolves to a valid image, one to a broken file, and
     * the rest to nothing.
     */
    private fun terrainProject(): File {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("ray-terrain").toFile()
        val folder = File(root, "assets/terrain").apply { mkdirs() }
        DataOutputStream(File(folder, "terrain.data").outputStream()).use { out -> repeat(4) { out.writeFloat(it.toFloat()) } }
        File(folder, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":10,"uv":4,"splatMap":"missing","splatBase":"00000000-0000-0000-0000-000000000002","splatR":"00000000-0000-0000-0000-000000000001","splatG":"00000000-0000-0000-0000-000000000099"}}""")
        val broken = File(root, "assets/broken").apply { mkdirs() }
        File(broken, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"00000000-0000-0000-0000-000000000001","type":"TEXTURE","additional":{"file":"broken.png"}}""")
        File(broken, "broken.png").writeText("not an image")
        val valid = File(root, "assets/valid").apply { mkdirs() }
        File(valid, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"00000000-0000-0000-0000-000000000002","type":"TEXTURE","additional":{"file":"valid.png"}}""")
        val image = pixel(0x204080ff)
        try { PixmapIO.writePNG(FileHandle(File(valid, "valid.png")), image) } finally { image.dispose() }
        return root
    }
}
