/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Matrix4
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.WorldTransformComponent
import net.nevinsky.abyssus.lib.core.ecs.component.worldTransform
import net.nevinsky.abyssus.lib.core.format.EntityType

/**
 * Finds a game's dummies (`DUMMY` entities, named attach points) by their parent and name. When several children of
 * one parent share the name, the one with the lowest id is found and [warn] gets one line naming the parent and the
 * name (once per pair for this lookup).
 */
class Dummies(private val warn: (String) -> Unit) {
    private val warned = HashSet<Pair<Long, String>>()

    /** The child dummy [name] of entity [parentId] in [engine], or null. */
    fun find(engine: Engine, parentId: Long, name: String): Entity? = find(engine.entities, parentId, name)

    /** The child dummy [name] of entity [parentId] among [entities], or null. */
    fun find(entities: Iterable<Entity>, parentId: Long, name: String): Entity? {
        var found: Entity? = null
        var foundId = Long.MAX_VALUE
        var count = 0
        for (entity in entities) {
            val header = entity.getComponent(HeaderComponent::class.java) ?: continue
            if (header.type != EntityType.DUMMY) {
                continue
            }
            if (entity.getComponent(ParentComponent::class.java)?.parentEntityId?.toLong() != parentId) {
                continue
            }
            if (entity.getComponent(NameComponent::class.java)?.name != name) {
                continue
            }
            count++
            if (header.id < foundId) {
                found = entity
                foundId = header.id
            }
        }
        if (count > 1 && warned.add(parentId to name)) {
            warn("entity $parentId: $count child dummies are named '$name'; using entity $foundId")
        }
        return found
    }

    /** The world pose of [dummy] into [out] (its resolved world transform). */
    fun world(dummy: Entity, out: Matrix4): Matrix4 = dummy.worldTransform()?.let(out::set) ?: out.idt()

    /**
     * The pose of [dummy] in its [parent]'s frame into [out]: `inverse(parent world) x dummy world` once the hierarchy
     * has resolved both; before that (no update has run yet), the dummy's own `PositionComponent` pose, which is the
     * same unless the dummy is bound to a model node.
     */
    fun inParent(dummy: Entity, parent: Entity, out: Matrix4): Matrix4 {
        val dummyWorld = dummy.getComponent(WorldTransformComponent::class.java)?.takeIf { it.resolved }
        val parentWorld = parent.getComponent(WorldTransformComponent::class.java)?.takeIf { it.resolved }
        if (dummyWorld == null || parentWorld == null) {
            return dummy.getComponent(PositionComponent::class.java)?.getTransform()?.let(out::set) ?: out.idt()
        }
        return out.set(parentWorld.transform).inv().mul(dummyWorld.transform)
    }
}
