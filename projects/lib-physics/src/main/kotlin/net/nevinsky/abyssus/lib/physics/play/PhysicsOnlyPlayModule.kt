/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.physics.play

import net.nevinsky.abyssus.lib.physics.ConstraintKind
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import java.util.Locale

/** Play without a game: physics alone, with each rope's tension as telemetry. Used when a project has no `play.json`. */
class PhysicsOnlyPlayModule : PlayModule {
    override fun telemetry(world: PhysicsWorld): Map<String, String> =
        world.constraints.filter { it.kind == ConstraintKind.DISTANCE }.withIndex().associate { (i, rope) ->
            val name = rope.entity.getComponent(NameComponent::class.java)?.name ?: "rope ${i + 1}"
            "Tension: $name" to String.format(Locale.ROOT, "%.2f N", rope.tension)
        }
}
