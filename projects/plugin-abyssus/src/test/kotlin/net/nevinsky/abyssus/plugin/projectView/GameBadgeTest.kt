/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameBadgeTest {
    private fun component(name: String) = DtoEntry("/s/ecs/0/components/$name", name, null, null, null, null, listOf("ecs", "0", "components"))

    @Test
    fun builtInComponentsCarryNoBadge() {
        listOf("RenderComponent", "NameComponent", "PositionComponent", "FoliageComponent", "RigidBodyComponent", "ColliderComponent").forEach {
            assertFalse(it, isGameComponent(it))
        }
        assertFalse(isGameComponent("net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent"))
    }

    @Test
    fun aComponentTheGameRegistersIsBadged() {
        assertTrue(isGameComponent("PlaneComponent"))
        assertTrue(isGameComponent("PilotComponent"))
        assertTrue(isGameComponent("com.example.game.NameComponent"))
    }

    @Test
    fun theBadgeTakesPartInRowEquality() {
        val game = component("PlaneComponent")
        val builtIn = component("RenderComponent")
        assertTrue(game.isGame)
        assertFalse(builtIn.isGame)
    }
}
