/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs.scene

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
 * What the loader keeps of the `ecs` block besides the entities: every other top-level member (`archetypes`,
 * `componentIdentifiers`, `metadata`, in file order) as raw JSON, and the problems met while loading.
 */
class SceneEcsDocument(val extras: Map<String, JsonNode>, val warnings: List<String>) {
    val archetypes: JsonNode? get() = extras["archetypes"]
    val componentIdentifiers: JsonNode? get() = extras["componentIdentifiers"]
    val metadata: JsonNode? get() = extras["metadata"]
}
