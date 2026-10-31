/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoliageComponentEditsTest {
    private val edits = FoliageComponentEdits()

    @Test
    fun attachGivesATerrainItsFoliageOnceAndRenamesIt() {
        val scene = sceneOf(entityOf(1, EntityType.TERRAIN))

        assertTrue(edits.attach(scene, 1L, "foliage_meadow"))
        assertEquals("foliage_meadow", scene.component(1, FoliageComponent::class.java)!!.assetName)
        assertFalse("naming the same foliage again changes nothing", edits.attach(scene, 1L, "foliage_meadow"))
        assertTrue(edits.attach(scene, 1L, "other"))
        assertEquals(1, scene.entity(1)!!.components.count { it is FoliageComponent })
        assertEquals("other", scene.component(1, FoliageComponent::class.java)!!.assetName)
    }

    @Test
    fun attachToAMissingEntityDoesNothing() {
        val scene = sceneOf(entityOf(1, EntityType.TERRAIN))
        assertFalse(edits.attach(scene, 9L, "foliage_meadow"))
        assertNull(scene.component(1, FoliageComponent::class.java))
    }

    @Test
    fun removeTakesTheEntitiesOutAndReportsChange() {
        val scene = sceneOf(entityOf(1, EntityType.OBJECT), entityOf(2, EntityType.OBJECT), entityOf(3, EntityType.OBJECT))
        val result = edits.remove(scene, setOf(1L, 2L, 99L))
        assertTrue(result.changed)
        assertNull(result.blockedBy)
        assertEquals(setOf(3L), scene.ecs.keys)
    }

    @Test
    fun removeOfNothingPresentIsNoChange() {
        val scene = sceneOf(entityOf(1, EntityType.OBJECT))
        assertFalse(edits.remove(scene, setOf(99L)).changed)
        assertEquals(setOf(1L), scene.ecs.keys)
    }

    @Test
    fun removeIsBlockedByAParentOrLookAtReferenceFromARemainingEntity() {
        val parented = sceneOf(entityOf(1, EntityType.OBJECT), entityOf(2, EntityType.OBJECT, ParentComponent(1)))
        val parentResult = edits.remove(parented, setOf(1L))
        assertFalse(parentResult.changed)
        assertTrue(parentResult.blockedBy!!, parentResult.blockedBy!!.contains("ParentComponent.parentEntityId"))
        assertEquals(setOf(1L, 2L), parented.ecs.keys)

        val aimed = sceneOf(entityOf(1, EntityType.OBJECT), entityOf(2, EntityType.OBJECT, PositionComponent(lookAtId = 1)))
        assertTrue(edits.remove(aimed, setOf(1L)).blockedBy!!.contains("PositionComponent.lookAtId"))
        assertEquals("removing the referrer too is allowed", true, edits.remove(aimed, setOf(1L, 2L)).changed)
        assertTrue(aimed.ecs.isEmpty())
    }
}
