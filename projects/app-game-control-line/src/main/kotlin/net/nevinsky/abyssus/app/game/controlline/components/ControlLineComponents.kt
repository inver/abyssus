/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.components

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.physics.PHYSICS_COMPONENTS

/** The game's own components by the name a scene file gives them, without the physics ones (what Play's host adds to its own). */
val GAME_COMPONENTS: Map<String, Class<out Component>> = mapOf(
    "PilotComponent" to PilotComponent::class.java,
    "PlaneComponent" to PlaneComponent::class.java,
)

/** Everything the field scene uses: the physics components and the game's own. Registered by the game. */
val CONTROL_LINE_COMPONENTS: Map<String, Class<out Component>> = PHYSICS_COMPONENTS + GAME_COMPONENTS
