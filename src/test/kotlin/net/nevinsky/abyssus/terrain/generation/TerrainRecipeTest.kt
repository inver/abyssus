/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain.generation

import net.nevinsky.abyssus.terrain.sha256Hex
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.terrain.noise.FAST_NOISE_LITE_REVISION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainRecipeTest {
    private val codec = TerrainRecipeCodec(JsonProcessor())
    private val bytes = byteArrayOf(1, 2, 3, 4)
    private val recipe = TerrainRecipe(
        settings = TerrainGenerationSettings(seed = -7, featureSize = 150.5f, minHeight = -10f, maxHeight = 90f, octaves = 4, persistence = 0.25f, lacunarity = 3f),
        size = 1600,
        resolution = 180,
        heightsSha256 = sha256Hex(bytes),
    )

    private fun text() = codec.encode(recipe)

    @Test
    fun `a recipe round trips`() {
        assertEquals(TerrainRecipeCodec.Decoded.Recipe(recipe), codec.decode(text()))
    }

    @Test
    fun `the encoded recipe records generator revision settings and fingerprint`() {
        val t = text()
        assertTrue(t.endsWith("\n"))
        assertTrue(t.contains("\"id\": \"opensimplex2-fbm-v1\""))
        assertTrue(t.contains("\"sourceRevision\": \"$FAST_NOISE_LITE_REVISION\""))
        assertTrue(t.contains("\"seed\": -7"))
        assertTrue(t.contains("\"heightsSha256\": \"${sha256Hex(bytes)}\""))
    }

    @Test
    fun `no file is missing`() {
        assertEquals(RecipeStatus.Missing, codec.status(null, 1600, 180, bytes))
    }

    @Test
    fun `a matching recipe restores its settings`() {
        val status = codec.status(text(), 1600, 180, bytes)
        assertEquals(RecipeStatus.Matching(recipe), status)
        assertEquals(recipe.settings, codec.draftSettings(status))
    }

    @Test
    fun `changed heights or size or resolution are mismatches and offer defaults`() {
        assertEquals(setOf(MismatchReason.HEIGHTS), (codec.status(text(), 1600, 180, byteArrayOf(9)) as RecipeStatus.Mismatch).reasons)
        assertEquals(setOf(MismatchReason.SIZE), (codec.status(text(), 800, 180, bytes) as RecipeStatus.Mismatch).reasons)
        val both = codec.status(text(), 800, 90, byteArrayOf(9)) as RecipeStatus.Mismatch
        assertEquals(setOf(MismatchReason.SIZE, MismatchReason.RESOLUTION, MismatchReason.HEIGHTS), both.reasons)
        assertEquals(TerrainGenerationSettings(), codec.draftSettings(both))
    }

    @Test
    fun `malformed recipes are reported with a reason`() {
        for (bad in listOf("", "not json", "[]", "{}", """{"schemaVersion":1}""", text().replace("\"seed\": -7", "\"seed\": \"x\""),
            text().replace("\"octaves\": 4", "\"octaves\": 99"), text().replace("\"size\": 1600", "\"size\": 0"),
            text().replace(sha256Hex(bytes), "abc"), text().replace("\"resolution\": 180", "\"resolution\": 300"))) {
            val status = codec.status(bad, 1600, 180, bytes)
            assertTrue("$bad -> $status", status is RecipeStatus.Malformed && status.reason.isNotBlank())
        }
    }

    @Test
    fun `unknown schema or generator is unsupported and never silently reinterpreted`() {
        val newer = codec.status(text().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"), 1600, 180, bytes)
        assertEquals(RecipeStatus.Unsupported("schemaVersion 2"), newer)
        val other = codec.status(text().replace("opensimplex2-fbm-v1", "perlin-v9"), 1600, 180, bytes)
        assertEquals(RecipeStatus.Unsupported("generator perlin-v9"), other)
        assertEquals(TerrainGenerationSettings(), codec.draftSettings(other))
    }
}
