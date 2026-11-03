/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.editor.terrain.sha256Hex
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SculptManifestTest {
    private val codec = SculptManifestCodec(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER))
    private val sha = sha256Hex(byteArrayOf(1, 2, 3))
    private val recipe = SculptManifest(SculptBaseKind.RECIPE, 180, sha, sha)
    private val stored = SculptManifest(SculptBaseKind.STORED, 3, sha, sha, sha)

    @Test
    fun `a manifest round trips`() {
        assertEquals(SculptManifestCodec.Decoded.Manifest(recipe), codec.decode(codec.encode(recipe)))
        assertEquals(SculptManifestCodec.Decoded.Manifest(stored), codec.decode(codec.encode(stored)))
    }

    @Test
    fun `a recipe base writes no base fingerprint`() {
        assertTrue(!codec.encode(recipe).contains("baseSha256"))
        assertTrue(codec.encode(stored).contains("\"baseSha256\": \"$sha\""))
    }

    @Test
    fun `another schema version is unsupported`() {
        val text = codec.encode(recipe).replace("\"schemaVersion\": 1", "\"schemaVersion\": 0")
        assertEquals(SculptManifestCodec.Decoded.Unsupported("schemaVersion 0"), codec.decode(text))
    }

    @Test
    fun `an unknown base kind is malformed`() {
        val text = codec.encode(recipe).replace("\"RECIPE\"", "\"MAGIC\"")
        assertTrue(codec.decode(text) is SculptManifestCodec.Decoded.Malformed)
    }

    @Test
    fun `malformed text is reported`() {
        assertTrue(codec.decode("not json") is SculptManifestCodec.Decoded.Malformed)
        assertTrue(codec.decode("{}") is SculptManifestCodec.Decoded.Malformed)
    }

    @Test
    fun `a stored base needs its fingerprint`() {
        val text = codec.encode(stored).lines().filterNot { it.contains("baseSha256") }.joinToString("\n")
            .replace(sha + "\",\n", sha + "\"\n")
        assertTrue(codec.decode(text) is SculptManifestCodec.Decoded.Malformed)
    }

    @Test
    fun `a grid of the wrong size is refused`() {
        val heights = floatArrayOf(1f, 2f, 3f, 4f)
        val bytes = TerrainHeightEncoder().encode(heights)
        assertArrayEquals(heights, decodeSculptGrid(bytes, 2), 0f)
        assertNull(decodeSculptGrid(bytes, 3))
    }
}
