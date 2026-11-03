/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrangementCopiesTest {
    private fun arrangement(vararg slots: Int) = PathArrangementComponent().also { it.slots = slots.toMutableList() }

    private fun child(id: Long, parent: Int) = entityOf(id, EntityType.OBJECT, ParentComponent(parent))

    private val scene: SceneDto = sceneOf(
        entityOf(1, EntityType.OBJECT, NameComponent("Ground")),
        entityOf(2, EntityType.OBJECT, NameComponent("Railway"), arrangement(3, -1, 4, 9, 6)),
        child(3, 2),
        child(4, 2),
        entityOf(5, EntityType.OBJECT, NameComponent("Detached")),
        child(6, 5),
    )

    @Test
    fun theCopiesAreTheEntitiesAnArrangementListsAndThatPointBackToIt() {
        // 9 does not exist, -1 is an empty slot and 6 points at another entity: none of them is a linked copy
        assertEquals(setOf(3L, 4L), arrangementCopyIds(scene))
    }

    @Test
    fun aSceneWithoutArrangementsHasNoCopies() {
        assertEquals(emptySet<Long>(), arrangementCopyIds(sceneOf(entityOf(1, EntityType.OBJECT))))
        assertEquals(emptySet<Long>(), arrangementCopyIds(sceneOf()))
    }

    @Test
    fun aCopyIsOwnedByTheArrangementThatListsItAndPointsBack() {
        assertEquals(2L, arrangementOwnerOf(scene, 3))
        assertEquals(null, arrangementOwnerOf(scene, 6))
        assertEquals(null, arrangementOwnerOf(scene, 1))
    }

    @Test
    fun theCountIsTheSlotsThatNameAnEntity() {
        assertEquals(4, arrangementCopyCount(scene.entity(2)!!))
        assertEquals(0, arrangementCopyCount(scene.entity(1)!!))
    }
}
