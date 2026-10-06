/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.flightgear

import com.badlogic.gdx.files.FileHandle
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.core.flightgear.fixtureSkinPixel
import net.nevinsky.abyssus.lib.core.flightgear.sgiRle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The JSON chunk of a GLB, after checking its header and chunk layout. */
fun glbJson(glb: ByteArray): JsonNode {
    val data = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(0x46546C67, data.getInt(0))
    assertEquals(2, data.getInt(4))
    assertEquals(glb.size, data.getInt(8))
    val jsonLength = data.getInt(12)
    assertEquals(0x4E4F534A, data.getInt(16))
    assertEquals(0, jsonLength % 4)
    val binLength = data.getInt(20 + jsonLength)
    assertEquals(0x004E4942, data.getInt(24 + jsonLength))
    assertEquals(glb.size, 28 + jsonLength + binLength)
    val json = ObjectMapper().readTree(String(glb, 20, jsonLength))
    assertEquals(binLength, json["buffers"][0]["byteLength"].asInt())
    return json
}

class GlbWriterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun quad(material: Int, y: Float) = GltfPrimitive(
        material,
        floatArrayOf(0f, y, 0f, 1f, y, 0f, 1f, y, 1f, 0f, y, 1f),
        floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
        intArrayOf(0, 1, 2, 0, 2, 3),
    )

    private fun write(): ByteArray = GlbWriter().write(
        listOf(GltfNode("Body", listOf(quad(0, 0f))), GltfNode("Wing", listOf(quad(1, 1f)))),
        listOf(
            GltfMaterial("skin", floatArrayOf(1f, 1f, 1f, 1f), 0, false),
            GltfMaterial("glass", floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f), null, true),
        ),
        listOf("textures/skin.png"),
        "test",
    )

    @Test
    fun theGlbIsStructurallyValid() {
        val json = glbJson(write())
        assertEquals("2.0", json["asset"]["version"].asText())
        assertEquals(listOf("Body", "Wing"), json["nodes"].map { it["name"].asText() })
        val position = json["accessors"][json["meshes"][1]["primitives"][0]["attributes"]["POSITION"].asInt()]
        assertEquals(listOf(0.0, 1.0, 0.0), position["min"].map { it.asDouble() })
        assertEquals(listOf(1.0, 1.0, 1.0), position["max"].map { it.asDouble() })
        assertEquals("BLEND", json["materials"][1]["alphaMode"].asText())
        assertTrue(json["materials"][1]["doubleSided"].asBoolean())
        assertEquals("textures/skin.png", json["images"][0]["uri"].asText())
        json["bufferViews"].forEach { assertEquals(0, it["byteOffset"].asInt() % 4) }
    }

    @Test
    fun theSameInputWritesTheSameBytes() {
        assertTrue(write().contentEquals(write()))
    }

    @Test
    fun theModelLoaderReadsItBackWithItsTexture() {
        val folder = temp.newFolder("model")
        File(folder, "textures").mkdirs()
        File(folder, "textures/skin.png").writeBytes(SgiImage().toPng(sgiRle(4, 2, 3, ::fixtureSkinPixel)))
        val file = File(folder, "model.glb").apply { writeBytes(write()) }
        val data = AssimpModelLoader().loadData(FileHandle(file))
        assertEquals(2, data.meshes.size)
        val textures = data.materials.flatMap { it.textures?.toList().orEmpty() }.map { File(it.fileName).name }
        assertEquals(listOf("skin.png"), textures)
        assertTrue(!File(folder, "embedded").exists())
    }
}
