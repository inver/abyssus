/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.shadows

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.scene.DirectionalSource
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.PointSource
import net.nevinsky.abyssus.lib.core.editor.scene.SpotCone
import net.nevinsky.abyssus.lib.core.editor.scene.SpotSource
import net.nevinsky.abyssus.lib.gdx.shader.SHADOW_MAX_LOCAL
import net.nevinsky.abyssus.lib.gdx.shader.SHADOW_MAX_VIEWS
import net.nevinsky.abyssus.plugin.sceneview.*
import org.junit.Assert.*
import org.junit.Test

class ShadowLayoutTest {
    private val color = Color(1f, 1f, 1f, 1f)

    /** A stable numeric id for a readable light name: the kind letter picks the hundreds, a trailing digit the unit. */
    private fun lid(name: String): Long =
        when {
            name == "sun" -> 900L
            name == "spot" -> 800L
            else ->
                (when (name[0]) {
                    'd' -> 100L
                    'p' -> 200L
                    else -> 300L
                }) + name.drop(1).toLong()
        }

    private fun lights(points: List<String>, spots: List<String> = emptyList(), dirs: List<String> = emptyList()) =
        LightSet(
            dirs.map { DirectionalSource(Vector3(0f, -1f, 0f), color, lid(it)) },
            points.map { PointSource(Vector3(0f, 0f, 0f), color, 10f, lid(it)) },
            spots.map {
                SpotSource(lid(it), Vector3(0f, 0f, 0f), Vector3(0f, 0f, -1f), color, 10f, SpotCone(45f, 0.2f))
            },
        )

    @Test
    fun everyLightGetsTilesAndTheTilesAreUniqueAndInsideTheAtlas() {
        val allocated =
            ShadowLayout()
                .allocate(lights(listOf("p3", "p1", "p2"), listOf("s4", "s3", "s2", "s1"), listOf("d2", "d1")))
        assertEquals(
            setOf("d1", "p1", "p2", "p3", "s1", "s2", "s3", "s4").map(::lid).toSet(),
            allocated.map { it.entityId }.toSet(),
        )
        val tiles = allocated.flatMap { it.tiles }
        assertEquals(1 + 3 * 6 + 4, tiles.size)
        assertEquals(tiles.size, tiles.map { it.index }.toSet().size)
        for (tile in tiles) {
            assertEquals(4096 / 5, tile.size)
            assertEquals(4096, tile.atlasSize)
            assertTrue(tile.x >= 0 && tile.y >= 0 && tile.x + tile.size <= 4096 && tile.y + tile.size <= 4096)
        }
    }

    @Test
    fun twoHundredFiftySixPointLightsAreAllShadowedWithinTheViewBudget() {
        val names = (1..SHADOW_MAX_LOCAL).map { "p$it" }
        val allocated = ShadowLayout().allocate(lights(names, dirs = listOf("sun")))
        assertEquals(names.size + 1, allocated.size)
        val tiles = allocated.flatMap { it.tiles }
        assertEquals(1 + SHADOW_MAX_LOCAL * 6, tiles.size)
        assertTrue(tiles.size <= SHADOW_MAX_VIEWS)
        assertEquals(tiles.size, tiles.map { it.index }.toSet().size)
        assertTrue(tiles.all { it.x + it.size <= 4096 && it.y + it.size <= 4096 })
    }

    @Test
    fun twoHundredFiftySixSpotLightsUseSixteenByTheSixteenTiles() {
        val allocated = ShadowLayout().allocate(lights(emptyList(), (1..SHADOW_MAX_LOCAL).map { "s$it" }))
        assertEquals(SHADOW_MAX_LOCAL, allocated.size)
        assertTrue(allocated.flatMap { it.tiles }.all { it.size == 256 })
    }

    @Test
    fun theGridShrinksAgainWhenMostLightsAreGone() {
        val layout = ShadowLayout()
        layout.allocate(lights(emptyList(), (1..64).map { "s$it" }))
        val one = layout.allocate(lights(emptyList(), listOf("s1"))).single()
        assertEquals(4096, one.tiles.single().size)
    }

    @Test
    fun smallLightSetsUseAvailableAtlasResolution() {
        val layout = ShadowLayout()
        assertEquals(4096, layout.allocate(lights(emptyList(), dirs = listOf("sun"))).single().tiles.single().size)
        val pair = layout.allocate(lights(emptyList(), listOf("spot"), listOf("sun")))
        assertTrue(pair.flatMap { it.tiles }.all { it.size == 2048 })
        assertEquals(pair, layout.allocate(lights(emptyList(), listOf("spot"), listOf("sun"))))
    }

    @Test
    fun reorderRemovalAndReplacementPreserveSurvivingTiles() {
        val layout = ShadowLayout()
        val first =
            layout.allocate(lights(listOf("p1", "p2"), listOf("s1", "s2"), listOf("d1"))).associateBy { it.entityId }
        val reorder =
            layout.allocate(lights(listOf("p2", "p1"), listOf("s2", "s1"), listOf("d1"))).associateBy { it.entityId }
        assertEquals(first, reorder)
        val removal =
            layout.allocate(lights(listOf("p2"), listOf("s2", "s3"), listOf("d1"))).associateBy { it.entityId }
        for (name in listOf("p2", "s2", "d1")) assertEquals(first[lid(name)], removal[lid(name)])
        assertFalse(removal.containsKey(lid("p1")))
        assertEquals(emptyList<ShadowAllocation>(), layout.allocate(NO_LIGHTS))
    }
}
