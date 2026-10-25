/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerationSettings
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipe
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.sha256Hex
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SculptStateTest {
    private val log = org.slf4j.helpers.NOPLogger.NOP_LOGGER
    private val generator = TerrainGenerator(FastNoiseSamplerFactory())
    private val recipes = TerrainRecipeCodec(JsonProcessor(log))
    private val manifests = SculptManifestCodec(JsonProcessor(log))
    private val reader = SculptStateReader(generator, recipes, manifests)

    private val resolution = 9
    private val size = 100
    private val settings = TerrainGenerationSettings()
    private val base = generator.generate(resolution, size, settings)
    private val delta = FloatArray(base.size) { if (it % 7 == 0) 2.5f else 0f }
    private val composed = FloatArray(base.size) { base[it] + delta[it] }

    private val baseBytes = encodeSculptGrid(base)
    private val deltaBytes = encodeSculptGrid(delta)
    private val composedBytes = encodeSculptGrid(composed)
    private val recipeText = recipes.encode(TerrainRecipe(settings, size, resolution, sha256Hex(baseBytes)))

    private fun manifestText(kind: SculptBaseKind = SculptBaseKind.RECIPE, composedSha: String = sha256Hex(composedBytes)) =
        manifests.encode(
            SculptManifest(kind, resolution, sha256Hex(deltaBytes), composedSha, if (kind == SculptBaseKind.STORED) sha256Hex(baseBytes) else null),
        )

    private fun files(
        terrain: ByteArray = composedBytes,
        recipe: String? = recipeText,
        manifest: String? = manifestText(),
        delta: ByteArray? = deltaBytes,
        stored: ByteArray? = null,
    ) = SculptFiles(size, resolution, terrain, recipe, manifest, delta, stored)

    private fun problem(state: SculptState) = (state as SculptState.Unsafe).problem

    @Test
    fun `no manifest is no sculpting`() {
        assertSame(SculptState.None, reader.read(files(manifest = null)))
    }

    @Test
    fun `matching recipe terrain is sculpted over the regenerated base`() {
        val state = reader.read(files()) as SculptState.Sculpted
        assertEquals(SculptBaseKind.RECIPE, state.kind)
        assertArrayEquals(base, state.base, 0f)
        assertArrayEquals(delta, state.delta, 0f)
    }

    @Test
    fun `a stored base is read from its file even without a recipe`() {
        val state = reader.read(files(recipe = null, manifest = manifestText(SculptBaseKind.STORED), stored = baseBytes))
        assertArrayEquals(base, (state as SculptState.Sculpted).base, 0f)
        assertEquals(SculptBaseKind.STORED, state.kind)
    }

    @Test
    fun `an external terrain data edit is unsafe`() {
        val edited = composedBytes.copyOf().also { it[3] = (it[3] + 1).toByte() }
        assertEquals(SculptProblem.COMPOSED_MISMATCH, problem(reader.read(files(terrain = edited))))
    }

    @Test
    fun `a missing delta is unsafe`() {
        assertEquals(SculptProblem.DELTA_MISSING, problem(reader.read(files(delta = null))))
    }

    @Test
    fun `a changed or short delta is unsafe`() {
        assertEquals(SculptProblem.DELTA_MISMATCH, problem(reader.read(files(delta = deltaBytes.copyOf().also { it[0] = 1 }))))
        assertEquals(SculptProblem.DELTA_MISMATCH, problem(reader.read(files(delta = deltaBytes.copyOf(8)))))
    }

    @Test
    fun `a recipe that no longer matches the base is unsafe`() {
        val other = recipes.encode(TerrainRecipe(settings.copy(seed = 1), size, resolution, sha256Hex(baseBytes)))
        assertEquals(SculptProblem.RECIPE_UNUSABLE, problem(reader.read(files(recipe = other))))
    }

    @Test
    fun `a recipe base with no recipe is unsafe`() {
        assertEquals(SculptProblem.RECIPE_UNUSABLE, problem(reader.read(files(recipe = null))))
    }

    @Test
    fun `a missing or changed stored base is unsafe`() {
        val stored = manifestText(SculptBaseKind.STORED)
        assertEquals(SculptProblem.BASE_MISSING, problem(reader.read(files(manifest = stored))))
        assertEquals(SculptProblem.BASE_MISMATCH, problem(reader.read(files(manifest = stored, stored = baseBytes.copyOf().also { it[0] = 9 }))))
    }

    @Test
    fun `malformed unsupported and other resolution manifests are unsafe`() {
        assertEquals(SculptProblem.MANIFEST_MALFORMED, problem(reader.read(files(manifest = "nope"))))
        assertEquals(SculptProblem.MANIFEST_UNSUPPORTED, problem(reader.read(files(manifest = manifestText().replace("\"schemaVersion\": 1", "\"schemaVersion\": 3")))))
        assertEquals(SculptProblem.RESOLUTION, problem(reader.read(files(manifest = manifestText().replace("\"resolution\": 9", "\"resolution\": 10")))))
    }

    @Test
    fun `an unsafe terrain says why in its detail`() {
        assertTrue((reader.read(files(manifest = "nope")) as SculptState.Unsafe).detail.isNotEmpty())
    }
}
