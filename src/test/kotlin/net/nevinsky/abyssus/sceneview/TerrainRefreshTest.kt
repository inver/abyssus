/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.scene.PlacedEntities
import net.nevinsky.abyssus.editor.scene.PlacedEntity
import net.nevinsky.abyssus.editor.content.Vec3
import net.nevinsky.abyssus.editor.content.Quat
import net.nevinsky.abyssus.editor.content.PlacementTransform
import net.nevinsky.abyssus.editor.content.AssetPlacement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * What the scene shows of a terrain after its asset is replaced: every entity placing it follows the replacement, with
 * its own transform, while entities of other assets are untouched. (The GPU side is covered by the opt-in GL tests.)
 */
class TerrainRefreshTest {
    private class Heights(val name: String)

    private class Entity(override val placement: AssetPlacement, override val asset: Heights) : PlacedEntity<Heights>

    private fun placement(entity: String, asset: String, x: Float = 0f) =
        AssetPlacement(entity, asset, PlacementTransform(Vec3(x, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f)))

    private val entities = PlacedEntities<Heights, Entity> { p, asset, _ -> Entity(p, asset) }

    @Test
    fun `every entity sharing a terrain follows its replacement and keeps its own placement`() {
        val placements = listOf(placement("1", "hills", 0f), placement("2", "hills", 500f), placement("3", "other", 9f))
        var hills = Heights("hills v1")
        val other = Heights("other")
        val assetOf = { name: String -> if (name == "hills") hills else other }
        entities.update(placements, assetOf)
        val before = entities.drawn.associateBy { it.placement.entityId }

        hills = Heights("hills v2")
        entities.update(placements, assetOf)
        val after = entities.drawn.associateBy { it.placement.entityId }

        assertSame(hills, after.getValue("1").asset)
        assertSame(hills, after.getValue("2").asset)
        assertSame("the same heights object serves both entities", after.getValue("1").asset, after.getValue("2").asset)
        assertEquals("transforms are untouched", 500f, after.getValue("2").placement.transform.position.x, 0f)
        assertEquals("entities are not moved to rest on the new surface", placements, after.values.map { it.placement })
        assertNotSame(before.getValue("1"), after.getValue("1"))
        assertSame("an unrelated asset's entity is kept", before.getValue("3"), after.getValue("3"))
    }

    @Test
    fun `an entity drawn from the old asset until the replacement exists`() {
        val placements = listOf(placement("1", "hills"))
        val v1 = Heights("v1")
        var current: Heights? = v1
        entities.update(placements) { current }
        // the cache keeps returning the old asset until the replacement is built, so nothing flickers away
        entities.update(placements) { current }
        assertSame(v1, entities.drawn.single().asset)
        current = Heights("v2")
        entities.update(placements) { current }
        assertEquals("v2", entities.drawn.single().asset.name)
    }

    @Test
    fun `a terrain that cannot load stops being drawn and returns when repaired`() {
        val placements = listOf(placement("1", "hills"))
        var current: Heights? = Heights("v1")
        entities.update(placements) { current }
        current = null
        entities.update(placements) { current }
        assertEquals(0, entities.drawn.size)
        current = Heights("v3")
        entities.update(placements) { current }
        assertEquals("v3", entities.drawn.single().asset.name)
    }
}
