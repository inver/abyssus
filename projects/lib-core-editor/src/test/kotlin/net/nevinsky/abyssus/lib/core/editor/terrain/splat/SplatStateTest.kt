/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SplatStateTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val reader = SplatStateReader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), SplatImageCodec())
    private val assets get() = File(tmp.root, "assets")
    private val uuid = "aaaaaaaa-0000-0000-0000-000000000001"

    private fun terrain(name: String, splat: String?, header: String = """"format":"abyssus","formatVersion":1,"""): File {
        val dir = File(assets, name).apply { mkdirs() }
        val ref = splat?.let { """"splatMap":"$it",""" }.orEmpty()
        File(dir, "meta.json").writeText("""{$header"version":1,"uuid":"t-$name","type":"TERRAIN","additional":{$ref"size":100,"terrainFile":"terrain.data"}}""")
        return dir
    }

    private fun texture(name: String, id: String, image: BufferedImage?, file: String = "splat.png"): File {
        val dir = File(assets, name).apply { mkdirs() }
        File(dir, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"uuid":"$id","type":"TEXTURE","additional":{"file":"$file"}}""")
        if (image != null) ImageIO.write(image, "png", File(dir, file))
        return dir
    }

    private fun rgba(side: Int = 64) = BufferedImage(side, side, BufferedImage.TYPE_4BYTE_ABGR)

    @Test
    fun `no splat map reads as none`() {
        val t = terrain("terrain_a", null)
        assertTrue(reader.read(assets, t) is SplatState.None)
    }

    @Test
    fun `an unresolved uuid reads as none and keeps the uuid`() {
        val t = terrain("terrain_a", "missing")
        assertEquals("missing", (reader.read(assets, t) as SplatState.None).unresolved)
    }

    @Test
    fun `a readable map is ready with no other users`() {
        val t = terrain("terrain_a", uuid)
        texture("texture_splat", uuid, rgba())
        val state = reader.read(assets, t) as SplatState.Ready
        assertEquals(64, state.pixels.side)
        assertEquals("texture_splat", state.folder.name)
        assertTrue(state.users.isEmpty())
    }

    @Test
    fun `a second terrain on the same map is listed`() {
        val t = terrain("terrain_a", uuid)
        terrain("terrain_b", uuid)
        terrain("terrain_c", "other")
        texture("texture_splat", uuid, rgba())
        assertEquals(listOf("terrain_b"), (reader.read(assets, t) as SplatState.Ready).users)
    }

    @Test
    fun `unreadable, not rgba and too large images are unusable with a reason`() {
        val t = terrain("terrain_a", uuid)
        texture("texture_splat", uuid, null)
        assertEquals(SplatProblem.IMAGE_MISSING, (reader.read(assets, t) as SplatState.Unusable).problem)
        File(assets, "texture_splat/splat.png").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(SplatImageProblem.UNREADABLE, (reader.read(assets, t) as SplatState.Unusable).image)
        ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", File(assets, "texture_splat/splat.png"))
        assertEquals(SplatImageProblem.NOT_RGBA8, (reader.read(assets, t) as SplatState.Unusable).image)
        ImageIO.write(rgba(4096), "png", File(assets, "texture_splat/splat.png"))
        assertEquals(SplatImageProblem.TOO_LARGE, (reader.read(assets, t) as SplatState.Unusable).image)
    }

    @Test
    fun `an invalid or unsupported terrain meta is unusable`() {
        assertEquals(SplatProblem.TERRAIN_META, (reader.read(assets, terrain("a", uuid, header = "")) as SplatState.Unusable).problem)
        assertEquals(
            SplatProblem.TERRAIN_META,
            (reader.read(assets, terrain("b", uuid, header = """"format":"abyssus","formatVersion":2,""")) as SplatState.Unusable).problem,
        )
    }

    @Test
    fun `an unreadable texture meta no longer resolves the map`() {
        val t = terrain("terrain_a", uuid)
        texture("texture_splat", uuid, rgba())
        File(assets, "texture_splat/meta.json").writeText("{ not json")
        assertTrue(reader.read(assets, t) is SplatState.None)
    }
}
