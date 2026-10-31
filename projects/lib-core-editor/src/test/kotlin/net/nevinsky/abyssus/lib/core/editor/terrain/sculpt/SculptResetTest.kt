/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerationSettings
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipe
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.sha256Hex
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SculptResetTest {
    private val log = org.slf4j.helpers.NOPLogger.NOP_LOGGER
    private val generator = TerrainGenerator(FastNoiseSamplerFactory())
    private val encoder = TerrainHeightEncoder()
    private val recipes = TerrainRecipeCodec(JsonProcessor(log))
    private val manifests = SculptManifestCodec(JsonProcessor(log))
    private val reader = SculptStateReader(generator, recipes, manifests)
    private val planner = SculptPlanner(generator, encoder, recipes, manifests)

    private val resolution = 9
    private val size = 100
    private val settings = TerrainGenerationSettings()
    private val base = generator.generate(resolution, size, settings)
    private val baseBytes = encoder.encode(base)
    private val delta = FloatArray(base.size) { if (it % 4 == 0) 1.5f else 0f }
    private val deltaBytes = encoder.encode(delta)
    private val composed = encoder.encode(composeSculpt(base, delta))
    private val recipeText = recipes.encode(TerrainRecipe(settings, size, resolution, sha256Hex(baseBytes)))

    private fun recipeFiles(): SculptFiles {
        val manifest = SculptManifest(SculptBaseKind.RECIPE, resolution, sha256Hex(deltaBytes), sha256Hex(composed))
        return SculptFiles(size, resolution, composed, recipeText, manifests.encode(manifest), deltaBytes, null)
    }

    private fun storedFiles(): SculptFiles {
        val manifest = SculptManifest(SculptBaseKind.STORED, resolution, sha256Hex(deltaBytes), sha256Hex(composed), sha256Hex(baseBytes))
        return SculptFiles(size, resolution, composed, null, manifests.encode(manifest), deltaBytes, baseBytes)
    }

    @Test
    fun `resetting a recipe terrain gives a fresh generation and removes the sculpt files`() {
        val files = recipeFiles()
        val out = planner.reset(files, reader.read(files))
        assertArrayEquals(baseBytes, out.terrain)
        assertArrayEquals(recipeText.toByteArray(), out.recipe)
        assertNull(out.manifest)
        assertNull(out.delta)
        assertNull(out.base)
    }

    @Test
    fun `resetting a stored base terrain restores the pre sculpt bytes`() {
        val files = storedFiles()
        val out = planner.reset(files, reader.read(files))
        assertArrayEquals(baseBytes, out.terrain)
        assertNull(out.recipe)
        assertNull(out.base)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unsafe terrain cannot be reset`() {
        val files = recipeFiles()
        val edited = SculptFiles(size, resolution, composed.copyOf().also { it[0] = 7 }, files.recipeText, files.manifestText, files.deltaBytes, null)
        planner.reset(edited, reader.read(edited))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unsculpted terrain cannot be reset`() {
        val plain = SculptFiles(size, resolution, baseBytes, null, null, null, null)
        planner.reset(plain, reader.read(plain))
    }

    @Test
    fun `forgetting keeps the current heights and recipe and removes the sculpt files`() {
        val files = recipeFiles()
        val edited = SculptFiles(size, resolution, composed.copyOf().also { it[0] = 7 }, files.recipeText, files.manifestText, files.deltaBytes, null)
        val state = reader.read(edited)
        assertEquals(SculptProblem.COMPOSED_MISMATCH, (state as SculptState.Unsafe).problem)
        val out = planner.forget(edited, state)
        assertArrayEquals(edited.terrainBytes, out.terrain)
        assertArrayEquals(recipeText.toByteArray(), out.recipe)
        assertNull(out.manifest)
        assertNull(out.delta)
        assertNull(out.base)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unsculpted terrain has nothing to forget`() {
        val plain = SculptFiles(size, resolution, baseBytes, null, null, null, null)
        planner.forget(plain, reader.read(plain))
    }
}
