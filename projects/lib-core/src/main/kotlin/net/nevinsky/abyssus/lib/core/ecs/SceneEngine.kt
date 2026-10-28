/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity

/** The entities of a scene by the id they have in the file. */
class SceneEntityIds {
    private val byId = LinkedHashMap<Int, Entity>()

    val ids: Set<Int> get() = byId.keys

    operator fun get(id: Int): Entity? = byId[id]

    operator fun contains(id: Int) = id in byId

    fun register(id: Int, entity: Entity) {
        byId[id] = entity
    }

    fun <T : Component> positionOf(id: Int, clazz: Class<T>): T? {
        return get(id)?.getComponent(clazz)
    }
}

class SceneEngine : Engine() {
    val ids = SceneEntityIds()
}

/**
 * What the loader keeps of the `ecs` block besides the entities: the components it could not bind (by entity id, then the
 * key the file gave them, in file order, as [RawComponent]s holding their JSON text), and the problems met while loading. [carried] is what lets a scene be written back without losing a component this
 * program does not model; the writer adds it to the entity of the same id.
 */
//todo use this object instead of EcsReadWarnings
class EcsLoadingWarns(
    val warnings: List<String>,
    val carried: Map<Long, Map<String, RawComponent>> = emptyMap(),
)