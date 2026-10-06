/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.play

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import net.nevinsky.abyssus.lib.physics.jolt.PhysicsWorld
import net.nevinsky.abyssus.lib.runtime.ecs.scene.SceneEngine
import net.nevinsky.abyssus.lib.runtime.schema.ComponentRegistry

/**
 * What a game adds to Play in Abyssus: its own components, the systems that act on the simulation and what it does
 * with input. Named by class in `abyssus/play.json` (it needs a no-argument constructor); [PlayHostMain] loads it.
 */
interface PlayModule {
    /** The game's components, without the physics ones (the host registers those itself). */
    fun components(): ComponentRegistry = ComponentRegistry { emptyList() }

    /**
     * The systems to run before each physics advance, given the world, the engine and the entity selected when Play
     * was pressed (null for none). They are updated with the frame's time, in list order.
     */
    fun systems(world: PhysicsWorld, engine: SceneEngine, selection: Entity?): List<EntitySystem> = emptyList()

    /** A key or mouse event from the Scene view; called on the play loop's thread, between frames. */
    fun input(event: PlayInput) = Unit

    /** Values to show while playing, sent with the poses. */
    fun telemetry(world: PhysicsWorld): Map<String, String> = emptyMap()

    /** Extra debug segments to draw, sent with the poses. */
    fun lines(world: PhysicsWorld): List<DebugLine> = emptyList()
}
