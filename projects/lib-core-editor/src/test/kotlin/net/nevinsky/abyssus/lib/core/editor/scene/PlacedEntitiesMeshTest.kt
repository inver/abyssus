/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.PlacementTransform
import net.nevinsky.abyssus.lib.core.editor.content.storageName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Entities showing different meshes of one model asset are built from different assets. */
class PlacedEntitiesMeshTest {
    private class Built(val key: String)

    private class Entity(override val placement: AssetPlacement, override val asset: Built) : PlacedEntity<Built>

    private val builtByKey = mapOf(
        "duo" to Built("duo"),
        ModelKey("duo", "body").encode() to Built("body"),
        ModelKey("duo", "glass").encode() to Built("glass"),
    )

    private fun placed() = PlacedEntities<Built, Entity> { placement, asset, _ -> Entity(placement, asset) }

    private fun at(id: Long, mesh: String?) = AssetPlacement(id, "duo", PlacementTransform.IDENTITY, mesh)

    @Test
    fun eachMeshGetsItsOwnAssetAndTheWholeModelItsOwn() {
        val entities = placed()
        entities.update(listOf(at(1, null), at(2, "body"), at(3, "glass"), at(4, "glass")), builtByKey::get)
        assertEquals(listOf("duo", "body", "glass", "glass"), entities.drawn.map { it.asset.key })
        assertSame(entities.drawn.toList()[2].asset, entities.drawn.toList()[3].asset)
        assertNotSame(entities.drawn.toList()[1].asset, entities.drawn.toList()[2].asset)
    }

    @Test
    fun anUnknownMeshSkipsOnlyItsEntity() {
        val entities = placed()
        entities.update(listOf(at(1, "body"), at(2, "nope"), at(3, null)), builtByKey::get)
        assertEquals(listOf(1L, 3L), entities.drawn.map { it.placement.entityId })
    }

    @Test
    fun changingTheMeshReplacesTheEntity() {
        val entities = placed()
        entities.update(listOf(at(1, "body")), builtByKey::get)
        val before = entities.drawn.single()
        entities.update(listOf(at(1, "glass")), builtByKey::get)
        assertEquals("glass", entities.drawn.single().asset.key)
        assertNotSame(before, entities.drawn.single())
    }

    @Test
    fun theStorageNameIsTheEncodedKey() {
        assertEquals("duo", at(1, null).storageName)
        assertEquals(ModelKey("duo", "body").encode(), at(1, "body").storageName)
    }
}
