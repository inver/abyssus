/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.assets.sky

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.files.FileHandle
import net.nevinsky.abyssus.assets.sky.hdr.HdrFixtures
import net.nevinsky.abyssus.assets.testLoading
import net.nevinsky.abyssus.assets.testProject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

class RaySkySnapshotTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // face images are decoded by libGDX
    }

    private val loading = testLoading()
    private val reader = RaySkySnapshotReader(loading.decoder, loading.hdrFiles)
    private val fixture = testProject("Untitled")

    private fun withProject(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("raysky").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    private fun texel(sky: RaySkySnapshot, u: Float, v: Float): FloatArray {
        val x = (u * sky.width).toInt().coerceIn(0, sky.width - 1); val y = (v * sky.height).toInt().coerceIn(0, sky.height - 1)
        return sky.rgba().copyOfRange((y * sky.width + x) * 4, (y * sky.width + x) * 4 + 4)
    }

    @Test fun hdrSkyKeepsLinearRadianceAboveOne() {
        val sky = reader.read(loading.files(fixture), "skybox_hdr")!!
        assertTrue(sky.hdr)
        assertEquals(HdrFixtures.FIXTURE_WIDTH, sky.width); assertEquals(HdrFixtures.FIXTURE_HEIGHT, sky.height)
        assertTrue("the sun patch stays far above white", sky.rgba().asList().chunked(4).any { it[0] > 10f })
        val ground = texel(sky, .5f, .9f)
        assertEquals(.08f, ground[0], .01f); assertEquals(1f, ground[3], 0f)
    }

    @Test fun largeHdrImagesAreDownsampledForTheRayTexture() = withProject { dir ->
        val folder = File(dir, "assets/big"); folder.mkdirs()
        File(folder, "meta.json").writeText("""{"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{}}""")
        HdrFixtures.write(File(folder, "sky.hdr"), 2048, 1024, pixel = HdrFixtures.uniform(2f))
        val sky = reader.read(loading.files(dir), "big")!!
        assertEquals(RAY_SKY_MAX_WIDTH, sky.width); assertEquals(RAY_SKY_MAX_WIDTH / 2, sky.height)
        assertEquals(2f, sky.rgba()[0], .02f)
        assertTrue(sky.byteSize <= RAY_SKY_MAX_WIDTH.toLong() * RAY_SKY_MAX_WIDTH / 2 * 16)
    }

    @Test fun cubeFacesLandWhereTheRasterCubeLookupPutsThem() = withProject { dir ->
        val folder = File(dir, "assets/cube"); folder.mkdirs()
        // Mundus order: back, front, left, right, bottom, top are +X, -X, +Y, -Y, +Z, -Z
        val faces = mapOf("back" to Color.RED, "front" to Color.GREEN, "left" to Color.BLUE, "right" to Color.YELLOW, "bottom" to Color.CYAN, "top" to Color.MAGENTA)
        faces.forEach { (name, color) ->
            Pixmap(8, 8, Pixmap.Format.RGBA8888).also { it.setColor(color); it.fill(); PixmapIO.writePNG(FileHandle(File(folder, "$name.png")), it); it.dispose() }
        }
        File(folder, "meta.json").writeText("""{"version":1,"lastModified":0,"type":"SKYBOX","additional":{${faces.keys.joinToString(",") { "\"$it\":\"$it.png\"" }}}}""")
        val sky = reader.read(loading.files(dir), "cube")!!
        assertFalse(sky.hdr)
        assertEquals(RAY_SKY_MAX_WIDTH, sky.width)
        fun rgb(c: Color) = floatArrayOf(c.r, c.g, c.b)
        fun at(u: Float, v: Float) = texel(sky, u, v).copyOf(3)
        assertArrayEquals(rgb(Color.MAGENTA), at(.5f, .5f), .01f)    // -Z: the centre column faces -Z
        assertArrayEquals(rgb(Color.RED), at(.75f, .5f), .01f)       // +X
        assertArrayEquals(rgb(Color.GREEN), at(.25f, .5f), .01f)     // -X
        assertArrayEquals(rgb(Color.CYAN), at(0f, .5f), .01f)        // +Z wraps the seam
        assertArrayEquals(rgb(Color.BLUE), at(.5f, .01f), .01f)      // +Y is the top row
        assertArrayEquals(rgb(Color.YELLOW), at(.5f, .99f), .01f)    // -Y is the bottom row
    }

    @Test fun proceduralSkiesAreNotTransferable() {
        assertNull(reader.read(loading.files(fixture), "skybox_physical"))
        assertNull(reader.read(loading.files(fixture), "no_such_sky"))
    }

    @Test fun leasesShareOneReadAndReleaseTheBytes() {
        val queue = ArrayDeque<Runnable>(); val executor = Executor { queue.add(it) }
        var reads = 0
        val snapshots = RaySkySnapshots(executor, { files, name -> reads++; reader.read(files, name) })
        val files = loading.files(fixture)
        val first = snapshots.acquire(files, "skybox_hdr"); val second = snapshots.acquire(files, "skybox_hdr")
        assertNull(first.snapshot)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals(1, reads)
        assertSame(first.snapshot, second.snapshot)
        assertTrue(snapshots.retainedBytes > 0)
        first.close(); first.close()
        assertNotNull(second.snapshot)
        second.close()
        assertEquals(0L, snapshots.retainedBytes)
        assertNull(second.snapshot)
    }

    @Test fun anUnreadableOrOversizedSkyFailsExplicitly() {
        val queue = ArrayDeque<Runnable>(); val executor = Executor { queue.add(it) }
        val small = RaySkySnapshots(executor, { files, name -> reader.read(files, name) }, maxBytes = 16)
        val lease = small.acquire(loading.files(fixture), "skybox_hdr")
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNull(lease.snapshot)
        assertNotNull(lease.failure)
        val missing = RaySkySnapshots(executor, { files, name -> reader.read(files, name) })
        val gone = missing.acquire(loading.files(fixture), "no_such_sky")
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNotNull(gone.failure)
    }
}
