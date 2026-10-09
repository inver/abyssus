/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.terrain

import net.nevinsky.abyssus.lib.gdx.editor.meta.TERRAIN_DATA_FILE
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.gdx.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.editor.testProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.DataOutputStream

class TerrainAssetEncodingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = JsonProcessor()
    private val encoder = TerrainHeightEncoder()
    private val writer = TerrainAssetWriter(json, encoder)
    private val fixture = File(testProject("Untitled"), "assets/terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b")

    @Test
    fun `encoding matches DataOutputStream writeFloat`() {
        val heights = floatArrayOf(0f, 1.5f, -2.25f, 120f, Float.MIN_VALUE, 1234.5678f)
        val expected = java.io.ByteArrayOutputStream().also { out -> DataOutputStream(out).use { d -> heights.forEach(d::writeFloat) } }
        assertArrayEquals(expected.toByteArray(), encoder.encode(heights))
        assertEquals(heights.size * 4, encoder.encode(heights).size)
    }

    @Test
    fun `the 180 resolution fixture round trips byte for byte`() {
        val original = File(fixture, "terrain.data").readBytes()
        val data = java.nio.ByteBuffer.wrap(original).asFloatBuffer().let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }
            .let { heights -> net.nevinsky.abyssus.lib.gdx.assets.terrain.TerrainData(Math.round(Math.sqrt(heights.size.toDouble())).toInt(), heights, 1600, 60f) }
        assertEquals(180, data.resolution)
        assertArrayEquals(original, encoder.encode(data.heights))
    }

    @Test
    fun `new terrain meta follows the native writer`() {
        val text = writer.meta(java.util.UUID.fromString("2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"), 1699293063182L, 1600)
        assertEquals(
            """{"format":"abyssus","formatVersion":1,"version":1,"lastModified":1699293063182,"uuid":"2cf70bf7-f7ee-4c41-934c-e40df1d35c8b","type":"TERRAIN",""" +
                """"additional":{"terrainFile":"terrain.data","size":1600,"uv":1.0,"splatMap":null,"splatBase":null,""" +
                """"splatR":null,"splatG":null,"splatB":null,"splatA":null}}""",
            text,
        )
    }

    @Test
    fun `the fixture meta differs only in uv`() {
        val fixtureText = File(fixture, "meta.json").readText()
        // The fixture is pretty printed; new asset metadata is compact, with the same field order and values.
        assertEquals(json.readObject(fixtureText.replace("\"uv\": 60.0", "\"uv\": 1.0")).toString(), writer.meta(java.util.UUID.fromString("2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"), 1699293063182L, 1600))
    }

    @Test
    fun `a new asset loads through the ordinary asset files`() {
        val heights = TerrainGenerator(FastNoiseSamplerFactory()).generate(33, 800, TerrainGenerationSettings())
        val files = writer.create(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"), 1L, 800, heights)
        val dir = File(tmp.root, "assets/hills").apply { mkdirs() }
        File(dir, "meta.json").writeText(files.metaText)
        File(dir, TERRAIN_DATA_FILE).writeBytes(files.heightBytes)

        val fileLoader = FileLoader(tmp.root)
        val read = checkNotNull(TerrainLoader(fileLoader, AssetMetaLoader(json, fileLoader)).prepare("hills")).staged.data
        assertEquals(800, read.size)
        assertEquals(1f, read.uv)
        assertEquals(33, read.resolution)
        assertArrayEquals(heights, read.heights, 0f)
    }

    @Test
    fun `sha256 of the bytes is lowercase hex`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non positive size is refused`() {
        writer.meta(java.util.UUID.randomUUID(), 1L, 0)
    }
}
