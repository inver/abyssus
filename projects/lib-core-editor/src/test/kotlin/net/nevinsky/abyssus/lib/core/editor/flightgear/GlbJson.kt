/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.flightgear

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
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
