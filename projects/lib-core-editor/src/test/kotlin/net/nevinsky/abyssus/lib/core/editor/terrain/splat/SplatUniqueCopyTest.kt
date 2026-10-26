/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

class SplatUniqueCopyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val shared = "11111111-1111-1111-1111-111111111111"
    private val assets get() = File(tmp.root, "assets")

    private fun terrain(name: String): File {
        val dir = File(assets, name).apply { mkdirs() }
        File(dir, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"uuid":"t-$name","type":"TERRAIN","additional":{"size":100,"splatMap":"$shared"}}""")
        return dir
    }

    @Test
    fun `the copy equals the source bytes and only this terrain is pointed at it`() {
        val a = terrain("terrain_a")
        val b = terrain("terrain_b")
        val sharedDir = File(assets, "texture_splat").apply { mkdirs() }
        File(sharedDir, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"uuid":"$shared","type":"TEXTURE","additional":{"file":"splat.png"}}""")
        val image = BufferedImage(64, 64, BufferedImage.TYPE_4BYTE_ABGR)
        image.raster.setPixel(3, 4, intArrayOf(200, 10, 20, 30))
        ImageIO.write(image, "png", File(sharedDir, "splat.png"))
        val reader = SplatStateReader(json, SplatImageCodec())
        val bBefore = File(b, "meta.json").readText()
        val ready = reader.read(assets, a) as SplatState.Ready
        assertEquals(listOf("terrain_b"), ready.users)

        val writer = SplatAssetWriter(json, SplatImageCodec(), randomUuid = { UUID.fromString("22222222-2222-2222-2222-222222222222") })
        val files = (writer.uniqueCopy(tmp.root, "terrain_a", File(a, "meta.json").readText(), ready.pixels) as SplatPlan.Planned).files

        assertArrayEquals(ready.pixels.rgba, SplatImageCodec().decode("splat.png", files.imageBytes).rgba)
        assertTrue(files.terrainMetaText.contains("22222222-2222-2222-2222-222222222222"))
        assertTrue(!files.terrainMetaText.contains(shared))
        assertEquals(bBefore, File(b, "meta.json").readText())
    }
}
