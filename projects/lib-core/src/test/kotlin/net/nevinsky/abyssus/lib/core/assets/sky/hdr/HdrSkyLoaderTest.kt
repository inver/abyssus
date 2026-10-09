/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.hdr

import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.exrFixture
import net.nevinsky.abyssus.lib.gdx.assets.skyShaders
import net.nevinsky.abyssus.lib.gdx.assets.testMetaLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HdrSkyLoaderTest {
    private val exr = exrFixture()
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    /** A project with one HDR sky asset `sky` whose meta names [file] (which is the real EXR when [copyExr]). */
    private fun project(file: String? = "sky.exr", copyExr: Boolean = true): File {
        val dir = Files.createTempDirectory("hdrsky").toFile().also(dirs::add)
        val folder = File(dir, "assets/sky").apply { mkdirs() }
        if (copyExr) exr.copyTo(File(folder, "sky.exr"))
        val named = file?.let { "\"file\":\"$it\"" } ?: ""
        File(folder, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"lastModified":0,"type":"SKYBOX_HDR","additional":{$named}}""")
        return dir
    }

    private fun loader(dir: File): HdrSkyLoader {
        val files = FileLoader(dir)
        return HdrSkyLoader(testMetaLoader(dir, fileLoader = files), ExrLoader(files), skyShaders(), ToneCurve())
    }

    @Test
    fun preparesTheNamedExr() {
        val prepared = loader(project()).prepare("sky")!!.staged
        assertEquals("sky.exr", prepared.file)
        assertEquals("sky", prepared.name)
        assertEquals(1024, prepared.image.width)
        assertEquals(512, prepared.image.height)
    }

    @Test
    fun anUnknownAssetPreparesNothing() {
        assertNull(loader(project()).prepare("no_such_sky"))
    }

    @Test
    fun aMetaWithoutAFileFailsWithAReason() {
        val error = runCatching { loader(project(file = null)).prepare("sky") }.exceptionOrNull()
        assertTrue("got $error", error is IllegalArgumentException)
    }

    @Test
    fun aMissingFileFailsWithItsName() {
        val error = runCatching { loader(project(copyExr = false)).prepare("sky") }.exceptionOrNull()
        assertTrue("got $error", error is IllegalStateException && error.message!!.contains("sky.exr"))
    }

    @Test
    fun aFileThatIsNotExrFails() {
        val dir = project(copyExr = false)
        File(dir, "assets/sky/sky.exr").writeText("#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n")
        val error = runCatching { loader(dir).prepare("sky") }.exceptionOrNull()
        assertTrue("got $error", error is IllegalStateException && error.message!!.contains("OpenEXR"))
    }
}
