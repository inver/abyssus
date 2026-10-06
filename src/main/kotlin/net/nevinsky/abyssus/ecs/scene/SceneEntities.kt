/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.ecs.scene

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Inserts new entities into a scene's JSON tree, in either layout: a native entity map (`ecs` is the map), or an older
 * `ecs.entities` block whose entities name an `archetype` from `ecs.archetypes`. Edits only the tree; callers write it
 * through `editSceneJson`.
 */
object SceneEntities {
    private val nodes = JsonNodeFactory.instance

    /** Whether an entity can be added to [root]: a native scene whose `ecs`, when present, is an object. */
    fun canAdd(root: JsonNode): Boolean = LightEntities.canAdd(root)

    /**
     * Adds an entity holding the components [build] makes for its id (one above the highest numeric entity id); in a
     * wrapped layout it gets the archetype listing exactly those components, reusing a matching one. Null when [root]
     * cannot take an entity.
     */
    fun insert(root: JsonNode, build: (id: String) -> ObjectNode): String? {
        if (!canAdd(root)) return null
        val ecs = root.get("ecs") as? ObjectNode ?: nodes.objectNode()
        val wrapped = isWrapped(ecs)
        val entities = if (wrapped) ecs.get("entities") as? ObjectNode ?: nodes.objectNode() else ecs
        val id = nextId(entities) ?: return null
        val entity = nodes.objectNode()
        if (wrapped) {
            if (!ecs.has("entities")) ecs.set<JsonNode>("entities", entities)
            entity.put("archetype", 0) // placeholder, set below once the components are known
        }
        entities.set<JsonNode>(id, entity.set<JsonNode>("components", build(id)))
        if (!root.has("ecs")) (root as ObjectNode).set<JsonNode>("ecs", ecs)
        if (wrapped && !matchArchetype(root, id)) {
            entities.remove(id)
            return null
        }
        return id
    }

    /**
     * Points entity [id] of a wrapped layout at the archetype listing exactly its components, adding one when none
     * matches. Does nothing (and is true) in a native layout. False when the entity or the table cannot be read.
     */
    fun matchArchetype(root: JsonNode, id: String): Boolean {
        val ecs = root.get("ecs") as? ObjectNode ?: return false
        if (!isWrapped(ecs)) return true
        val entity = (ecs.get("entities") as? ObjectNode)?.get(id) as? ObjectNode ?: return false
        val names = (entity.get("components") as? ObjectNode)?.fieldNames()?.asSequence()?.toList() ?: return false
        val archetypes = ecs.get("archetypes") as? ObjectNode ?: nodes.objectNode().also { ecs.set<JsonNode>("archetypes", it) }
        val matching = archetypes.properties().firstOrNull { (key, value) ->
            key.toIntOrNull()?.toString() == key && value.isArray && value.size() == names.size &&
                value.all { it.isTextual } && value.map { it.asText() }.toSet() == names.toSet()
        }?.key
        val archetype = matching ?: nextId(archetypes)?.also { key ->
            archetypes.set<JsonNode>(key, nodes.arrayNode().also { a -> names.forEach(a::add) })
        } ?: return false
        entity.put("archetype", archetype.toInt())
        return true
    }

    private fun isWrapped(ecs: ObjectNode) = ecs.get("entities") is ObjectNode || ecs.has("archetypes")

    private fun nextId(values: ObjectNode): String? {
        val highest = values.fieldNames().asSequence().mapNotNull(String::toIntOrNull).maxOrNull() ?: -1
        return if (highest == Int.MAX_VALUE) null else (highest + 1).coerceAtLeast(0).toString()
    }
}
