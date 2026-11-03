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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SculptCommitTest {
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
    private val generated = generator.generate(resolution, size, settings)
    private val generatedBytes = encoder.encode(generated)
    private val recipeText = recipes.encode(TerrainRecipe(settings, size, resolution, sha256Hex(generatedBytes)))

    private fun draftOver(base: FloatArray, delta: FloatArray = FloatArray(base.size)) =
        SculptDraft(resolution, base, composeSculpt(base, delta), delta.copyOf())

    private fun asFiles(set: SculptFileSet) = SculptFiles(
        size, resolution, set.terrain, set.recipe?.toString(Charsets.UTF_8), set.manifest?.toString(Charsets.UTF_8), set.delta, set.base,
    )

    @Test
    fun `the first stroke over a matching recipe writes no base and keeps the recipe`() {
        val files = SculptFiles(size, resolution, generatedBytes, recipeText, null, null, null)
        val draft = draftOver(generated).also { it.add(4, 4, 6f); it.recompose() }
        val out = planner.commitStroke(files, reader.read(files), draft)
        assertNull(out.base)
        assertArrayEquals(recipeText.toByteArray(), out.recipe)
        assertArrayEquals(encoder.encode(draft.composed), out.terrain)
        val state = reader.read(asFiles(out)) as SculptState.Sculpted
        assertEquals(SculptBaseKind.RECIPE, state.kind)
        assertArrayEquals(generated, state.base, 0f)
        assertEquals(6f, state.delta[4 * resolution + 4], 0f)
    }

    @Test
    fun `the first stroke over a terrain with no recipe stores the heights from before the stroke`() {
        val files = SculptFiles(size, resolution, generatedBytes, null, null, null, null)
        val draft = draftOver(generated).also { it.add(1, 1, -3f); it.recompose() }
        val out = planner.commitStroke(files, reader.read(files), draft)
        assertArrayEquals(generatedBytes, out.base)
        assertNull(out.recipe)
        val state = reader.read(asFiles(out)) as SculptState.Sculpted
        assertEquals(SculptBaseKind.STORED, state.kind)
        assertArrayEquals(generated, state.base, 0f)
    }

    @Test
    fun `a recipe that does not match the heights is left alone and the base is stored`() {
        val other = recipes.encode(TerrainRecipe(settings.copy(seed = 5), size, resolution, sha256Hex(generatedBytes)))
        val files = SculptFiles(size, resolution, generatedBytes, other, null, null, null)
        val draft = draftOver(generated).also { it.add(2, 2, 1f); it.recompose() }
        val out = planner.commitStroke(files, reader.read(files), draft)
        assertNotNull(out.base)
        assertArrayEquals(other.toByteArray(), out.recipe)
    }

    @Test
    fun `a later stroke keeps the base kind and the stored base bytes`() {
        val first = SculptFiles(size, resolution, generatedBytes, null, null, null, null)
        val firstOut = planner.commitStroke(first, reader.read(first), draftOver(generated).also { it.add(1, 1, 2f); it.recompose() })
        val second = asFiles(firstOut)
        val state = reader.read(second) as SculptState.Sculpted
        val draft = draftOver(state.base, state.delta).also { it.add(5, 5, 4f); it.recompose() }
        val out = planner.commitStroke(second, state, draft)
        assertArrayEquals(generatedBytes, out.base)
        val after = reader.read(asFiles(out)) as SculptState.Sculpted
        assertEquals(2f, after.delta[1 * resolution + 1], 0f)
        assertEquals(4f, after.delta[5 * resolution + 5], 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unsafe state is refused`() {
        val files = SculptFiles(size, resolution, generatedBytes, recipeText, "nope", null, null)
        planner.commitStroke(files, reader.read(files), draftOver(generated))
    }
}
