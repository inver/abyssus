/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/** Where a scene file keeps its entities: `ecs.entities.<id>.components.<ComponentName>`. */
class SceneEcsPaths {
    /** The `ecs.entities` object of the scene [root], or null when it has none. */
    fun entities(root: JsonNode): JsonNode? = root.get("ecs")?.get("entities")?.takeIf { it.isObject }

    /** The `entities` object of a scene's `ecs` object [ecs] (as `SceneDto.ecs` holds it), or null. */
    fun entitiesIn(ecs: JsonNode?): JsonNode? = ecs?.get("entities")?.takeIf { it.isObject }

    /** The `components` object of entity [entityId], or null when the entity or its components are missing. */
    fun components(root: JsonNode, entityId: String): ObjectNode? =
        entities(root)?.get(entityId)?.get("components") as? ObjectNode

    /** The `components` object of [entity] (an `ecs.entities` value), or null. */
    fun componentsOf(entity: JsonNode?): ObjectNode? = entity?.get("components") as? ObjectNode

    /** The `NameComponent.name` in [components] when it is non-blank text, else [id]. */
    fun entityName(components: JsonNode?, id: String): String =
        components?.get("NameComponent")?.get("name")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() } ?: id
}
