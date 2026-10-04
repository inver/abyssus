/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.terrain

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.assets.SPLAT_MAP
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.model.RayTextureColorSpace
import net.nevinsky.abyssus.assets.model.RayTextureFilter
import net.nevinsky.abyssus.assets.model.RayTextureWrap
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

class RayTerrainSnapshotTest {
    private val reader = RayTerrainSnapshotReader(TerrainDataReader())
    private fun data() = TerrainData(2, floatArrayOf(0f, 2f, 0f, 2f), 10, 4f)

    @Test fun immutableLocalGeometryPreservesRasterTrianglesNormalsUvsAndTransformedInstances() {
        val data = data()
        val snapshot = reader.capture(data, emptyMap())
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
        val snapshot = try { reader.capture(data(), images) } finally { images.values.forEach(Pixmap::dispose) }
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
        val root = terrainProject()
        try {
            val snapshot = reader.read(AssetFiles(root, JsonProcessor()), "terrain")!!
            assertNull(snapshot.splatMap)
            assertEquals(listOf("splatBase"), snapshot.layers.keys.toList())
            assertArrayEquals(byteArrayOf(32, 64, 128.toByte(), 255.toByte()), snapshot.layers.getValue("splatBase").image.rgba())
            assertEquals(6, snapshot.indices().size); assertEquals(4f, snapshot.uv, 0f)
        } finally { root.deleteRecursively() }
    }

    @Test fun capturedPreparationsSurviveDisposalAndLateAcquisitionSharesThenReleasesBytes() {
        val root = terrainProject()
        try {
            val queue = ArrayDeque<Runnable>(); var reads = 0
            val store = RayTerrainSnapshots(Executor { queue.add(it) }, { f, n -> reads++; reader.read(f, n) }, reader::capture)
            val files = AssetFiles(root, JsonProcessor())
            val lease = store.acquire(files, "terrain"); val second = store.acquire(files, "terrain")
            val loader = TerrainLoader(TerrainDataReader(), store)
            loader.discard(loader.prepare(files, "terrain")!!)
            assertNotNull(lease.snapshot); assertSame(lease.snapshot, second.snapshot)
            assertArrayEquals(byteArrayOf(32, 64, 128.toByte(), 255.toByte()), lease.snapshot!!.layers.getValue("splatBase").image.rgba())
            queue.removeFirst().run(); assertEquals(0, reads)
            lease.close(); assertTrue(store.retainedBytes > 0)
            second.close(); assertEquals(0L, store.retainedBytes)
            val late = store.acquire(files, "terrain")
            queue.removeFirst().run(); assertNotNull(late.snapshot); assertEquals(1, reads)
            late.close()
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationReacquisitionAndResourceLimitsCannotPublishUnwantedData() {
        val queue = ArrayDeque<Runnable>()
        val files = AssetFiles(File("/tmp/ray-terrain-snapshot-test"), JsonProcessor())
        val store = RayTerrainSnapshots(Executor { queue.add(it) }, { _, _ -> reader.capture(data(), emptyMap()) }, reader::capture)
        val lease = store.acquire(files, "terrain"); val capture = store.preparation(files, "terrain")!!
        lease.close(); val replacement = store.acquire(files, "terrain")
        capture.offer(data(), emptyMap()); assertNull(replacement.snapshot)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNotNull(replacement.snapshot); replacement.close(); assertEquals(0L, store.retainedBytes)
        val bounded = RayTerrainSnapshots(Executor { queue.add(it) }, { _, _ -> reader.capture(data(), emptyMap()) }, reader::capture, maxBytes = 1)
        val limited = bounded.acquire(files, "terrain"); queue.removeFirst().run()
        assertNull(limited.snapshot); assertNotNull(limited.failure); limited.close()
        assertThrows(IllegalArgumentException::class.java) { RayTerrainSnapshotReader(TerrainDataReader(), 1).capture(data(), emptyMap()) }
    }

    @Test fun invalidationAcrossViewsCannotRetainOrPublishTheDeletedRevision() {
        val queue = ArrayDeque<Runnable>()
        val files = AssetFiles(File("/tmp/ray-terrain-invalidate"), JsonProcessor())
        val store = RayTerrainSnapshots(Executor { queue.add(it) }, { _, _ -> reader.capture(data(), emptyMap()) }, reader::capture)
        val first = store.acquire(files, "terrain"); val otherView = store.acquire(files, "terrain")
        val oldPreparation = store.preparation(files, "terrain")!!
        queue.removeFirst().run()
        assertTrue(store.retainedBytes > 0)
        store.invalidate(files, "terrain")
        assertNull(otherView.snapshot); assertNotNull(otherView.failure)
        assertEquals(0L, store.retainedBytes)
        val next = store.acquire(files, "terrain")
        oldPreparation.offer(data(), emptyMap()); assertNull(next.snapshot)
        queue.removeFirst().run()
        val bytes = store.retainedBytes
        first.close(); otherView.close()
        assertEquals(bytes, store.retainedBytes)
        next.close(); assertEquals(0L, store.retainedBytes)
    }

    private fun channels(bytes: ByteArray) = bytes.map { (it.toInt() and 255) / 255f }
    private fun pixel(rgba: Int) = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply {
        setBlending(Pixmap.Blending.None)
        drawPixel(0, 0, rgba)
    }
    private fun terrainProject(): File {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("ray-terrain").toFile()
        val folder = File(root, "assets/terrain").apply { mkdirs() }
        DataOutputStream(File(folder, "terrain.data").outputStream()).use { out -> repeat(4) { out.writeFloat(it.toFloat()) } }
        File(folder, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":10,"uv":4,"splatMap":"missing","splatBase":"00000000-0000-0000-0000-000000000002","splatR":"00000000-0000-0000-0000-000000000001","splatG":"missing"}}""")
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
