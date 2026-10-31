/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaTextEditor
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.util.at
import net.nevinsky.abyssus.lib.core.editor.util.textAt
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class SplatBootstrapTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val writer = SplatAssetWriter(json, AssetMetaTextEditor(AssetMetaParser(json)), codec = SplatImageCodec(), clock = { 1_700_000_000_000L }, randomUuid = { UUID.fromString("00000000-0000-0000-0000-00000000abcd") })
    private val codec = SplatImageCodec()

    private val terrainMeta = """{
  "format": "abyssus",
  "formatVersion": 1,
  "version": 1,
  "uuid": "00000000-0000-0000-0000-0000000000aa",
  "type": "TERRAIN",
  "additional": {
    "terrainFile": "terrain.data",
    "size": 200,
    "uv": 8.0,
    "splatMap": null,
    "splatR": "r-uuid"
  }
}"""

    private fun planned(plan: SplatPlan) = (plan as SplatPlan.Planned).files

    @Test
    fun `a blank map at the requested size, a compact texture meta and the terrain link`() {
        val files = planned(writer.create(tmp.root, "terrain_a", terrainMeta, 128))
        val image = codec.decode("splat.png", files.imageBytes)
        assertEquals(128, image.side)
        assertTrue(image.rgba.all { it.toInt() == 0 })
        assertEquals("texture_terrain_a_splat", files.folder)
        assertEquals("00000000-0000-0000-0000-00000000abcd", files.uuid)
        assertTrue(files.metaText.contains(""""type":"TEXTURE""""))
        assertTrue(files.metaText.contains(""""file":"splat.png""""))
        assertTrue(files.metaText.contains(""""formatVersion":1"""))
        assertTrue(!files.metaText.contains("\n"))
    }

    @Test
    fun `the limits are accepted and one beyond is refused`() {
        for (ok in listOf(64, 2048)) assertTrue(writer.create(tmp.root, "t", terrainMeta, ok) is SplatPlan.Planned)
        for (bad in listOf(63, 2049, 0, -1)) {
            assertEquals(SplatPlanProblem.RESOLUTION, (writer.create(tmp.root, "t", terrainMeta, bad) as SplatPlan.Refused).problem)
        }
    }

    @Test
    fun `only splatMap changes in the terrain meta`() {
        val files = planned(writer.create(tmp.root, "terrain_a", terrainMeta, 64))
        val before = AssetMetaReader(json).read(terrainMeta).values
        val after = AssetMetaReader(json).read(files.terrainMetaText).values
        assertEquals(files.uuid, after.textAt("additional", "splatMap"))
        assertEquals(before.at("additional", "terrainFile"), after.at("additional", "terrainFile"))
        assertEquals(before.at("additional", "size"), after.at("additional", "size"))
        assertEquals(before.at("additional", "uv"), after.at("additional", "uv"))
        assertEquals(before.textAt("additional", "splatR"), after.textAt("additional", "splatR"))
        assertEquals(before.textAt("uuid"), after.textAt("uuid"))
    }

    @Test
    fun `a missing splatMap key is added`() {
        val compact = """{"formatVersion":1,"version":1,"uuid":"00000000-0000-0000-0000-0000000000aa","type":"TERRAIN","additional":{"size":200}}"""
        val text = planned(writer.create(tmp.root, "t", compact, 64)).terrainMetaText
        val values = AssetMetaReader(json).read(text).values
        assertEquals("00000000-0000-0000-0000-00000000abcd", values.textAt("additional", "splatMap"))
        assertEquals(200, values.at("additional", "size"))
    }

    @Test
    fun `a taken folder name picks the next free one and leaves the existing folder`() {
        File(tmp.root, "assets/texture_terrain_a_splat").mkdirs()
        File(tmp.root, "assets/Texture_Terrain_A_Splat_2").mkdirs()
        File(tmp.root, "assets/texture_terrain_a_splat/keep.txt").writeText("x")
        assertEquals("texture_terrain_a_splat_3", planned(writer.create(tmp.root, "terrain_a", terrainMeta, 64)).folder)
        assertEquals("x", File(tmp.root, "assets/texture_terrain_a_splat/keep.txt").readText())
    }

    @Test
    fun `an invalid or unsupported terrain meta is refused with nothing planned`() {
        for (text in listOf("{ not json", """{"formatVersion":1,"additional":{}}""", """{"format":"abyssus","formatVersion":2,"additional":{}}""")) {
            assertEquals(SplatPlanProblem.TERRAIN_META, (writer.create(tmp.root, "t", text, 64) as SplatPlan.Refused).problem)
        }
    }

    @Test
    fun `a fresh uuid skips the ones in use`() {
        val ids = ArrayDeque(listOf(UUID(0, 1), UUID(0, 2)))
        File(tmp.root, "assets/other").mkdirs()
        File(tmp.root, "assets/other/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"${UUID(0, 1)}","type":"TEXTURE"}""")
        val files = planned(SplatAssetWriter(json, AssetMetaTextEditor(AssetMetaParser(json)), codec = SplatImageCodec(), randomUuid = { ids.removeFirst() }).create(tmp.root, "t", terrainMeta, 64))
        assertEquals(UUID(0, 2).toString(), files.uuid)
    }
}
