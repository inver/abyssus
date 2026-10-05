/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Where a scene file keeps its entities: `ecs.<id>.components.<ComponentName>`, or in an older scene
 * `ecs.entities.<id>.components.<ComponentName>` (an `entities` member that is an object makes the scene "wrapped").
 */
class SceneEcsPaths {
    /** The entity map of the scene [root], or null when it has no `ecs` object. */
    fun entities(root: JsonNode): JsonNode? = entitiesIn(root.get("ecs"))

    /** The entity map of a scene's `ecs` object [ecs]: its `entities` member when that is an object, else [ecs] itself. */
    fun entitiesIn(ecs: JsonNode?): JsonNode? {
        if (ecs == null || !ecs.isObject) return null
        return ecs.get("entities")?.takeIf { it.isObject } ?: ecs
    }

    /** The JSON keys leading to the entity map of [root]: `ecs`, then `entities` for a wrapped scene. */
    fun entityKeys(root: JsonNode): List<String> =
        if (root.get("ecs")?.get("entities")?.isObject == true) listOf("ecs", "entities") else listOf("ecs")

    /** The `components` object of entity [entityId], or null when the entity or its components are missing. */
    fun components(root: JsonNode, entityId: String): ObjectNode? =
        entities(root)?.get(entityId)?.get("components") as? ObjectNode

    /** The `components` object of [entity] (an `ecs.entities` value), or null. */
    fun componentsOf(entity: JsonNode?): ObjectNode? = entity?.get("components") as? ObjectNode

    /** The `NameComponent.name` in [components] when it is non-blank text, else [id]. */
    fun entityName(components: JsonNode?, id: String): String =
        components?.get("NameComponent")?.get("name")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() } ?: id
}
