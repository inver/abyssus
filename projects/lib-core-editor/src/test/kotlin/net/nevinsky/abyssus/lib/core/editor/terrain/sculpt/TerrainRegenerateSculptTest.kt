/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.sculpt

import net.nevinsky.abyssus.lib.core.editor.terrain.FastNoiseSamplerFactory
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerationSettings
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainGenerator
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainHeightEncoder
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainPreview
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipe
import net.nevinsky.abyssus.lib.core.editor.terrain.TerrainRecipeCodec
import net.nevinsky.abyssus.lib.core.editor.terrain.sha256Hex
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainRegenerateSculptTest {
    private val log = org.slf4j.helpers.NOPLogger.NOP_LOGGER
    private val generator = TerrainGenerator(FastNoiseSamplerFactory())
    private val encoder = TerrainHeightEncoder()
    private val recipes = TerrainRecipeCodec(JsonProcessor(log))
    private val manifests = SculptManifestCodec(JsonProcessor(log))
    private val reader = SculptStateReader(generator, recipes, manifests)
    private val planner = SculptPlanner(generator, encoder, recipes, manifests)

    private val resolution = 9
    private val size = 100
    private val oldSettings = TerrainGenerationSettings()
    private val newSettings = oldSettings.copy(seed = 99)
    private val oldBase = generator.generate(resolution, size, oldSettings)
    private val newBase = generator.generate(resolution, size, newSettings)
    private val delta = FloatArray(oldBase.size) { if (it % 5 == 0) 3.25f else 0f }
    private val deltaBytes = encoder.encode(delta)
    private val preview = TerrainPreview(newSettings, size, resolution, newBase)

    private fun sculptedRecipeFiles(): SculptFiles {
        val composed = encoder.encode(composeSculpt(oldBase, delta))
        val manifest = SculptManifest(SculptBaseKind.RECIPE, resolution, sha256Hex(deltaBytes), sha256Hex(composed))
        val recipe = recipes.encode(TerrainRecipe(oldSettings, size, resolution, sha256Hex(encoder.encode(oldBase))))
        return SculptFiles(size, resolution, composed, recipe, manifests.encode(manifest), deltaBytes, null)
    }

    private fun asFiles(set: SculptFileSet) = SculptFiles(
        size, resolution, set.terrain, set.recipe?.toString(Charsets.UTF_8), set.manifest?.toString(Charsets.UTF_8), set.delta, set.base,
    )

    @Test
    fun `keeping sculpting gives the new noise plus the same delta bytes`() {
        val files = sculptedRecipeFiles()
        val out = planner.regenerate(files, reader.read(files), preview, discard = false)
        assertArrayEquals(encoder.encode(composeSculpt(newBase, delta)), out.terrain)
        assertArrayEquals(deltaBytes, out.delta)
        assertNull(out.base)
        val state = reader.read(asFiles(out)) as SculptState.Sculpted
        assertArrayEquals(newBase, state.base, 0f)
        assertArrayEquals(delta, state.delta, 0f)
    }

    @Test
    fun `the recipe fingerprints the new base not the composed heights`() {
        val files = sculptedRecipeFiles()
        val out = planner.regenerate(files, reader.read(files), preview, discard = false)
        val recipe = (recipes.decode(out.recipe!!.toString(Charsets.UTF_8)) as TerrainRecipeCodec.Decoded.Recipe).recipe
        assertEquals(sha256Hex(encoder.encode(newBase)), recipe.heightsSha256)
        assertEquals(newSettings, recipe.settings)
        assertTrue(!out.terrain.contentEquals(encoder.encode(newBase)))
    }

    @Test
    fun `discarding gives the new noise alone and removes the sculpt files`() {
        val files = sculptedRecipeFiles()
        val out = planner.regenerate(files, reader.read(files), preview, discard = true)
        assertArrayEquals(encoder.encode(newBase), out.terrain)
        assertNull(out.manifest)
        assertNull(out.delta)
        assertNull(out.base)
        assertNotNull(out.recipe)
        assertEquals(SculptState.None, reader.read(asFiles(out)))
    }

    @Test
    fun `an unsculpted terrain regenerates as before`() {
        val plain = SculptFiles(size, resolution, encoder.encode(oldBase), null, null, null, null)
        val out = planner.regenerate(plain, reader.read(plain), preview, discard = false)
        assertArrayEquals(encoder.encode(newBase), out.terrain)
        assertNull(out.manifest)
    }

    @Test
    fun `a stored base is replaced by the recipe's`() {
        val baseBytes = encoder.encode(oldBase)
        val composed = encoder.encode(composeSculpt(oldBase, delta))
        val manifest = SculptManifest(SculptBaseKind.STORED, resolution, sha256Hex(deltaBytes), sha256Hex(composed), sha256Hex(baseBytes))
        val files = SculptFiles(size, resolution, composed, null, manifests.encode(manifest), deltaBytes, baseBytes)
        val out = planner.regenerate(files, reader.read(files), preview, discard = false)
        assertNull(out.base)
        val state = reader.read(asFiles(out)) as SculptState.Sculpted
        assertEquals(SculptBaseKind.RECIPE, state.kind)
        assertArrayEquals(newBase, state.base, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unsafe state is refused`() {
        val files = sculptedRecipeFiles()
        val broken = SculptFiles(size, resolution, files.terrainBytes, files.recipeText, files.manifestText, null, null)
        planner.regenerate(broken, reader.read(broken), preview, discard = false)
    }
}
