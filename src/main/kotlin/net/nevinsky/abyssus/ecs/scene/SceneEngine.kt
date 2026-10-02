/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
