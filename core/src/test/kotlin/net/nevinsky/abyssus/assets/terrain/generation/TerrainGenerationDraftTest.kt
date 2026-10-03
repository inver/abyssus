/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.terrain.generation

import net.nevinsky.abyssus.assets.terrain.noise.FastNoiseSamplerFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TerrainGenerationDraftTest {
    private val source = SourceSnapshot("meta", "sha", "recipe", true)
    private val settings = TerrainGenerationSettings()
    private val generator = TerrainGenerator(FastNoiseSamplerFactory())

    private fun draft() = TerrainGenerationDraft(settings, 100, 8, source)
    private fun heights() = FloatArray(64) { it.toFloat() }

    @Test
    fun `a completed matching preview can be applied`() {
        val d = draft()
        assertNull(d.applicable(source))
        val request = d.begin()!!
        assertTrue(d.generating)
        assertTrue(d.complete(request, heights()))
        assertFalse(d.generating)
        assertNotNull(d.applicable(source))
    }

    @Test
    fun `changing a setting after the preview disables apply until a new one completes`() {
        val d = draft()
        d.complete(d.begin()!!, heights())
        d.updateSettings(settings.copy(seed = 1))
        assertNull(d.preview)
        assertNull(d.applicable(source))
        d.complete(d.begin()!!, heights())
        assertNotNull(d.applicable(source))
    }

    @Test
    fun `equal settings keep the preview`() {
        val d = draft()
        d.complete(d.begin()!!, heights())
        d.updateSettings(settings.copy())
        assertNotNull(d.preview)
    }

    @Test
    fun `a result for superseded settings is stale`() {
        val d = draft()
        val old = d.begin()!!
        d.updateSettings(settings.copy(seed = 2))
        assertFalse(d.complete(old, heights()))
        assertNull(d.preview)
        assertFalse(d.generating)
    }

    @Test
    fun `only the latest of two requests is accepted`() {
        val d = draft()
        val first = d.begin()!!
        val second = d.begin()!!
        assertFalse(d.complete(first, heights()))
        assertTrue(d.generating)
        assertTrue(d.complete(second, heights()))
    }

    @Test
    fun `a stale failure does not clear a newer request`() {
        val d = draft()
        val first = d.begin()!!
        val second = d.begin()!!
        d.fail(first)
        assertTrue(d.generating)
        d.fail(second)
        assertFalse(d.generating)
    }

    @Test
    fun `cancel discards the pending request and the preview`() {
        val d = draft()
        val request = d.begin()!!
        d.cancel()
        assertFalse(d.generating)
        assertFalse(d.complete(request, heights()))
        d.complete(d.begin()!!, heights())
        d.cancel()
        assertNull(d.preview)
    }

    @Test
    fun `invalid settings or geometry cannot start a request`() {
        val d = TerrainGenerationDraft(settings.copy(featureSize = 0f), 100, 8, source)
        assertEquals(listOf(SettingsError.FEATURE_SIZE), d.settingsErrors())
        assertNull(d.begin())
        val g = draft()
        g.updateGeometry(100, 1)
        assertNull(g.begin())
        g.updateGeometry(0, 8)
        assertNull(g.begin())
        g.updateGeometry(100, 256)
        assertNull(g.begin())
    }

    @Test
    fun `a geometry change drops the preview`() {
        val d = draft()
        d.complete(d.begin()!!, heights())
        d.updateGeometry(200, 8)
        assertNull(d.preview)
    }

    @Test
    fun `a changed source needs a new preview`() {
        val d = draft()
        d.complete(d.begin()!!, heights())
        assertNull(d.applicable(source.copy(heightsSha256 = "other")))
        assertNull(d.applicable(source.copy(metaText = "edited")))
        assertNull(d.applicable(source.copy(recipeText = null)))
        assertNull(d.applicable(source.copy(folderExists = false)))
        assertNotNull(d.applicable(source))
        d.sourceChanged()
        assertNull(d.preview)
        d.complete(d.begin()!!, heights())
        assertNull("a draft whose source changed stays stale", d.applicable(source))
    }

    @Test
    fun `randomize changes only the seed and drops the preview`() {
        val d = draft()
        d.complete(d.begin()!!, heights())
        d.randomizeSeed(Random(1))
        assertNotEquals(settings.seed, d.settings.seed)
        assertEquals(settings.copy(seed = d.settings.seed), d.settings)
        assertNull(d.preview)
    }

    @Test
    fun `same seed previews are identical`() {
        val a = draft()
        val b = draft()
        val ra = a.begin()!!
        val rb = b.begin()!!
        a.complete(ra, generator.generate(ra.resolution, ra.size, ra.settings))
        b.complete(rb, generator.generate(rb.resolution, rb.size, rb.settings))
        assertArrayEquals(a.preview!!.heights, b.preview!!.heights, 0f)
        assertArrayEquals(a.preview!!.image.pixels, b.preview!!.image.pixels)
    }

    @Test
    fun `the heightmap maps min to black and max to white`() {
        val image = heightmapImage(floatArrayOf(0f, 60f, 120f, 30f), 2, 0f, 120f)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals(listOf(0, 128, 255, 64), image.pixels.map { it.toInt() and 0xff })
        val clamped = heightmapImage(floatArrayOf(-5f, 500f, 0f, 0f), 2, 0f, 120f)
        assertEquals(listOf(0, 255), clamped.pixels.take(2).map { it.toInt() and 0xff })
    }
}
