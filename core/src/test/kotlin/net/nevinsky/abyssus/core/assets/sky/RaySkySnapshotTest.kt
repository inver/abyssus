/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.core.assets.sky

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.exrFixture
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.core.assets.loading.RaySnapshotStore
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrImage
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.skyShaders
import net.nevinsky.abyssus.core.assets.testMetaLoader
import net.nevinsky.abyssus.core.assets.testProject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

class RaySkySnapshotTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // face images are decoded by libGDX
    }

    private val exr = exrFixture()
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun project(): File = Files.createTempDirectory("raysky").toFile().also(dirs::add)

    private fun File.meta(folder: String, type: String, additional: String): File {
        File(this, "assets/$folder").mkdirs()
        File(this, "assets/$folder/meta.json").writeText(
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"$type","additional":$additional}"""
        )
        return File(this, "assets/$folder")
    }

    /** A project holding the HDR sky `sky` (the real 1024 x 512 EXR). */
    private fun hdrProject(): File = project().also { dir ->
        exr.copyTo(File(dir.meta("sky", "SKYBOX_HDR", """{"file":"sky.exr"}"""), "sky.exr"))
    }

    private fun hdrLoader(dir: File): HdrSkyRaySnapshotLoader {
        val files = FileLoader(dir)
        return HdrSkyRaySnapshotLoader(HdrSkyLoader(testMetaLoader(dir, fileLoader = files), ExrLoader(files), skyShaders(), ToneCurve()))
    }

    private fun texel(sky: RaySkySnapshot, u: Float, v: Float): FloatArray {
        val x = (u * sky.width).toInt().coerceIn(0, sky.width - 1); val y = (v * sky.height).toInt().coerceIn(0, sky.height - 1)
        return sky.rgba().copyOfRange((y * sky.width + x) * 4, (y * sky.width + x) * 4 + 4)
    }

    @Test fun hdrSkyKeepsLinearRadianceAboveOne() {
        val dir = hdrProject()
        val sky = hdrLoader(dir).load(testMetaLoader(dir).loadBaseMeta("sky")!!)!!
        assertTrue(sky.hdr)
        assertEquals(1024, sky.width); assertEquals(512, sky.height)
        assertTrue("radiance stays above white", sky.rgba().asList().chunked(4).any { it[0] > 1f })
        assertTrue("opaque", sky.rgba().asList().chunked(4).all { it[3] == 1f })
    }

    @Test fun largeHdrImagesAreDownsampledForTheRayTexture() {
        val width = 2048; val height = 1024
        val half = java.lang.Float.floatToFloat16(2f)
        val image = HdrImage(width, height, ShortArray(width * height * 3) { half })
        val sky = hdrLoader(hdrProject()).downsample(image)
        assertEquals(RAY_SKY_MAX_WIDTH, sky.width); assertEquals(RAY_SKY_MAX_WIDTH / 2, sky.height)
        assertEquals(2f, sky.rgba()[0], .02f)
        assertTrue(sky.byteSize <= RAY_SKY_MAX_WIDTH.toLong() * RAY_SKY_MAX_WIDTH / 2 * 16)
    }

    @Test fun cubeFacesLandWhereTheRasterCubeLookupPutsThem() {
        val dir = project()
        // face order: back, front, left, right, bottom, top are +X, -X, +Y, -Y, +Z, -Z
        val faces = mapOf("back" to Color.RED, "front" to Color.GREEN, "left" to Color.BLUE, "right" to Color.YELLOW, "bottom" to Color.CYAN, "top" to Color.MAGENTA)
        val folder = dir.meta("cube", "SKYBOX", "{${faces.keys.joinToString(",") { "\"$it\":\"$it.png\"" }}}")
        faces.forEach { (name, color) ->
            Pixmap(8, 8, Pixmap.Format.RGBA8888).also { it.setColor(color); it.fill(); PixmapIO.writePNG(FileHandle(File(folder, "$name.png")), it); it.dispose() }
        }
        val files = FileLoader(dir)
        val metas = testMetaLoader(dir, fileLoader = files)
        val sky = SkyboxRaySnapshotLoader(SkyboxLoader(files, metas, skyShaders())).load(metas.loadBaseMeta("cube")!!)
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
        val fixture = testProject("Untitled")
        assertNull(ProceduralSkyRaySnapshotLoader().load(testMetaLoader(fixture).loadBaseMeta("skybox_physical")!!))
    }

    /** The sky store over [loader] for [dir]'s skies, with queued background work. */
    private fun store(dir: File, queue: ArrayDeque<Runnable>, loader: RaySnapshotLoader<RaySkySnapshot, Nothing>, maxBytes: Long = 128L * 1024 * 1024) =
        RaySnapshotStore(Executor { queue.add(it) }, testMetaLoader(dir), loader, "sky", maxBytes)

    @Test fun leasesShareOneReadAndReleaseTheBytes() {
        val dir = hdrProject()
        val queue = ArrayDeque<Runnable>()
        var reads = 0
        val inner = hdrLoader(dir)
        val counting = object : RaySnapshotLoader<RaySkySnapshot, Nothing> {
            override fun load(meta: AssetMeta<Any>): RaySkySnapshot? { reads++; return inner.load(meta) }
        }
        val snapshots = store(dir, queue, counting)
        val first = snapshots.acquire("sky"); val second = snapshots.acquire("sky")
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
        val dir = hdrProject()
        val queue = ArrayDeque<Runnable>()
        val small = store(dir, queue, hdrLoader(dir), maxBytes = 16)
        val lease = small.acquire("sky")
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNull(lease.snapshot)
        assertNotNull(lease.failure)
        val missing = store(dir, queue, hdrLoader(dir))
        val gone = missing.acquire("no_such_sky")
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertNotNull(gone.failure)
    }
}
