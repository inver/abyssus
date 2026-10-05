/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.scene

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.JsonNode

/** The entities of a scene by the id they have in the file. */
class SceneEntityIds {
    private val byId = LinkedHashMap<Int, Entity>()

    val ids: Set<Int> get() = byId.keys

    operator fun get(id: Int): Entity? = byId[id]

    operator fun contains(id: Int) = id in byId

    fun register(id: Int, entity: Entity) {
        byId[id] = entity
    }
}

/** An Ashley [Engine] that knows the file ids of its entities. */
class SceneEngine : Engine() {
    val ids = SceneEntityIds()
}

/**
 * What the loader keeps of the `ecs` block besides the entities: every other top-level member (`metadata`, in file
 * order) as raw JSON, the components it could not bind (by entity id, then the key the file gave them, in file order),
 * and the problems met while loading. [carried] is what lets a scene be written back without losing a component this
 * program does not model; the writer adds it to the entity of the same id.
 */
class SceneEcsDocument(
    val extras: Map<String, JsonNode>,
    val warnings: List<String>,
    val carried: Map<Long, Map<String, JsonNode>> = emptyMap(),
    /** True when the block held its entities in an `entities` member (an older scene); the writer keeps that shape. */
    val wrapped: Boolean = false,
) {
    val metadata: JsonNode? get() = extras["metadata"]
}
