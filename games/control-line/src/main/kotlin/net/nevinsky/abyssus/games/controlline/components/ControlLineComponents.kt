/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.components

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.physics.PhysicsComponents
import net.nevinsky.abyssus.runtime.schema.ComponentRegistry

/** The game's own components, without the physics ones (what Play's host adds to its own). */
class GameComponents : ComponentRegistry {
    override fun components(): List<Class<out Component>> = listOf(PilotComponent::class.java, PlaneComponent::class.java)
}

/** Everything the field scene uses: the physics components and the game's own. Loaded by the game; exported as schema. */
class ControlLineComponents : ComponentRegistry {
    override fun components(): List<Class<out Component>> = PhysicsComponents().components() + GameComponents().components()
}
