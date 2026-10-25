/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.GdxNativesLoader
import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StampImageSourceTest {
    private val roots = mutableListOf<File>()
    private val images = StampImages(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER))

    init {
        GdxNativesLoader.load()
    }

    @After
    fun cleanUp() = roots.forEach(File::deleteRecursively)

    private fun assets(): File = Files.createTempDirectory("abyssus-stamp").toFile().also(roots::add)

    private fun textureAsset(assets: File, name: String, type: String, writeImage: Boolean, pixels: IntArray = intArrayOf(0x000000ff, 0xffffffff.toInt())) {
        val folder = File(assets, name).apply { mkdirs() }
        File(folder, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"$type","uuid":"$name","additional":{"file":"image.png"}}""")
        if (!writeImage) return
        val pixmap = Pixmap(pixels.size, 1, Pixmap.Format.RGBA8888)
        try {
            pixmap.setBlending(Pixmap.Blending.None)
            pixels.forEachIndexed { x, rgba -> pixmap.drawPixel(x, 0, rgba) }
            PixmapIO.writePNG(FileHandle(File(folder, "image.png")), pixmap)
        } finally {
            pixmap.dispose()
        }
    }

    @Test
    fun `the project's readable image assets are listed by folder name`() {
        val dir = assets()
        textureAsset(dir, "b_pixmap", "PIXMAP_TEXTURE", writeImage = true)
        textureAsset(dir, "a_texture", "TEXTURE", writeImage = true)
        textureAsset(dir, "missing_file", "TEXTURE", writeImage = false)
        File(dir, "model_x").apply { mkdirs() }.resolve("meta.json").writeText("""{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{"file":"image.png"}}""")
        assertEquals(listOf("a_texture", "b_pixmap"), images.list(dir).map { it.name })
    }

    @Test
    fun `an image is read as luminance from black to white`() {
        val dir = assets()
        textureAsset(dir, "stamp", "TEXTURE", writeImage = true, pixels = intArrayOf(0x000000ff, 0xffffffff.toInt(), 0xff0000ff.toInt()))
        val image = images.decode(images.list(dir).single().file)!!
        assertEquals(3, image.width)
        assertEquals(0f, image.sample(0f, 0.5f), 1e-3f)
        assertEquals(1f, image.sample(0.5f, 0.5f), 1e-3f)
        assertEquals(0.2126f, image.sample(1f, 0.5f), 1e-3f) // pure red
    }

    @Test
    fun `a missing or damaged file has no image so the Stamp tool has none`() {
        val dir = assets()
        assertNull(images.decode(File(dir, "gone.png")))
        val broken = File(dir, "broken.png").apply { writeText("not an image") }
        assertNull(images.decode(broken))
        val tools = SculptToolFactory(FastNoiseSamplerFactory())
        assertNull(tools.create(SculptToolKind.STAMP, SculptToolValues(stampImage = images.decode(broken))))
    }

    @Test
    fun `a readable image gives a stamp and every other tool needs none`() {
        val dir = assets()
        textureAsset(dir, "stamp", "TEXTURE", writeImage = true)
        val tools = SculptToolFactory(FastNoiseSamplerFactory())
        val values = SculptToolValues(stampImage = images.decode(images.list(dir).single().file))
        assertNotNull(tools.create(SculptToolKind.STAMP, values))
        for (kind in SculptToolKind.entries) assertNotNull(kind.name, tools.create(kind, values))
        for (kind in SculptToolKind.entries.filter { it != SculptToolKind.STAMP }) assertNotNull(kind.name, tools.create(kind, SculptToolValues()))
    }
}
