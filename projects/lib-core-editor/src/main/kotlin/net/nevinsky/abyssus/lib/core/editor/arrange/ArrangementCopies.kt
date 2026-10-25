/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.document.sceneEcsRows

/** The entity of an arrangement or of a copy: its `components` object, or null. */
private fun components(entity: JsonNode): JsonNode? = entity.get("components")?.takeIf { it.isObject }

/** The entries of the arrangement's `slots` that name an entity (not `-1`); empty for an entity that is no arrangement. */
private fun slotIds(entity: JsonNode): List<Int> =
    components(entity)?.get(PathArrangementComponent::class.simpleName)?.get("slots")?.takeIf { it.isArray }
        ?.mapNotNull { slot -> slot.takeIf { it.isInt && it.asInt() >= 0 }?.asInt() }.orEmpty()

/**
 * How many copies the arrangement [entity] lists in its `slots` (`-1` marks an empty slot); 0 for an entity that has no
 * `PathArrangementComponent`.
 */
fun arrangementCopyCount(entity: JsonNode): Int = slotIds(entity).size

/**
 * The ids of the linked copies of every path arrangement in the scene's [ecs] object: the entities that an
 * arrangement lists in `slots` and that point back to it with a `ParentComponent`. A detached copy (no
 * `ParentComponent`, or one to another entity) is an ordinary entity and is not in the set.
 */
fun arrangementCopyIds(ecs: JsonNode): Set<String> {
    val entities =
        sceneEcsRows(ecs).filter { it.node.isObject && it.name.toIntOrNull() != null }.associate { it.name to it.node }
    val copies = HashSet<String>()
    for ((id, entity) in entities) {
        for (slot in slotIds(entity)) {
            val parent = entities[slot.toString()]?.let(::components)?.get("ParentComponent")?.get("parentEntityId")
            if (parent != null && parent.isInt && parent.asInt().toString() == id) copies += slot.toString()
        }
    }
    return copies
}

/** The id of the arrangement that [entityId] is a linked copy of (see [arrangementCopyIds]), or null for any other entity. */
fun arrangementOwnerOf(ecs: JsonNode, entityId: String): String? {
    val entities =
        sceneEcsRows(ecs).filter { it.node.isObject && it.name.toIntOrNull() != null }.associate { it.name to it.node }
    val parent = entities[entityId]?.let(::components)?.get("ParentComponent")?.get("parentEntityId")
        ?.takeIf { it.isInt }?.asInt() ?: return null
    return parent.toString().takeIf { owner -> entityId.toIntOrNull() in slotIds(entities[owner] ?: return null) }
}
