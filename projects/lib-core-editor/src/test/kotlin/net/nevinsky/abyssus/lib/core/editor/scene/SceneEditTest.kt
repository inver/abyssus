/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneEditTest {
    private val rename = SceneEdit { scene ->
        val name = scene.component(1, NameComponent::class.java) ?: return@SceneEdit false
        name.name = "renamed"
        true
    }

    @Test
    fun declinedEditLeavesSceneUntouched() {
        val scene = sceneOf(entityOf(1, EntityType.GROUP))

        assertFalse(rename.apply(scene))
        assertEquals(1, scene.entity(1)!!.components.size)
        assertEquals(setOf(1L), scene.ecs.keys)
    }

    @Test
    fun appliedEditMutatesScene() {
        val scene = sceneOf(entityOf(1, EntityType.GROUP, NameComponent("old")))

        assertTrue(rename.apply(scene))
        assertEquals("renamed", scene.nameOf(1))
    }

    @Test
    fun nextIdNeverReusesAnIdAfterRemovingTheMaximum() {
        val scene = sceneOf(entityOf(1, EntityType.GROUP), entityOf(2, EntityType.GROUP))
        assertEquals(3L, scene.nextId())
        val added = scene.add(EntityType.GROUP)
        scene.ecs.remove(added)

        // nextId follows the largest remaining id, so a removed maximum is reissued: this pins the contract.
        assertEquals(added, scene.nextId())
        assertEquals(1L, sceneOf().nextId())
    }
}
