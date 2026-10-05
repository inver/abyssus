/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.runtime.ecs.component.IdComponent
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEcsDocument
import net.nevinsky.abyssus.runtime.ecs.scene.SceneEngine
import net.nevinsky.abyssus.runtime.schema.GameComponents

/**
 * Writes a [SceneEngine] as an `ecs` block in the native format, the counterpart of [EcsLoader]: every component is
 * turned into JSON by Jackson ([ObjectMapper.valueToTree]), with no per-component codec.
 *
 * - Entities are written in ascending order of their [IdComponent]'s id; an entity without one follows, numbered after
 *   the largest id.
 * - A component is written without the properties that equal a new instance's (its defaults), and a decimal the way
 *   the scene files spell it (`22`, not `22.0`), under its short name, built-in components first, then the game's.
 * - What the loader could not bind (kept in [SceneEcsDocument.carried]) is added after the components, under the key the
 *   file gave it, unchanged.
 * - The block is the entity map itself; a scene loaded from an older block that wrapped it in an `entities` member
 *   ([SceneEcsDocument.wrapped]) keeps that shape, with the document's extra members after the entities.
 * - Derived state (combined transform, light instance, point-to-point positions) is not written.
 * - A component carrying the file's own node (a light, a render component, a look-at reference) writes that node, so
 *   unknown members and number spelling survive an unchanged component.
 */
class EcsWriter(
    mapper: ObjectMapper,
    game: GameComponents = GameComponents(),
) {
    private val mapper: ObjectMapper = mapper.forEcsWriting()
    private val types = ComponentTypes(game)
    private val nodes = JsonNodeFactory.instance

    fun write(engine: SceneEngine, document: SceneEcsDocument): ObjectNode {
        val entities = nodes.objectNode()
        for ((id, entity) in byId(engine)) entities.set<JsonNode>(id.toString(), writeEntity(id, entity, document))
        if (!document.wrapped) return entities
        val out = nodes.objectNode()
        out.set<JsonNode>("entities", entities)
        document.extras.forEach { (key, node) -> out.set<JsonNode>(key, node) }
        return out
    }

    /** [component] as the scene file holds it: the value of its entry in an entity's `components`. */
    fun writeComponent(component: Component): JsonNode = mapper.valueToTree(component)

    /**
     * The entities of [engine] with the id they are written under, ascending by their [IdComponent]'s id, so the order
     * of the result never depends on the order entities were added in. An entity without an [IdComponent] follows, in
     * engine order, numbered after the largest id.
     */
    private fun byId(engine: SceneEngine): List<Pair<Long, Entity>> {
        val withId = engine.entities.mapNotNull { e -> e.getComponent(IdComponent::class.java)?.let { it.id to e } }
            .sortedBy { it.first }
        var nextId = (withId.lastOrNull()?.first ?: -1L) + 1
        val withoutId = engine.entities.filter { it.getComponent(IdComponent::class.java) == null }.map { nextId++ to it }
        return withId + withoutId
    }

    private fun writeEntity(id: Long, entity: Entity, document: SceneEcsDocument): ObjectNode {
        val components = nodes.objectNode()
        for (type in types.all) {
            entity.getComponent(type)?.let { components.set<JsonNode>(types.shortName(type), writeComponent(it)) }
        }
        // what the loader could not bind, after the components: unless the entity has a component of that class now
        document.carried[id]?.forEach { (key, node) ->
            val type = types.resolve(key)
            if ((type == null || entity.getComponent(type) == null) && !components.has(key)) components.set<JsonNode>(key, node)
        }
        return nodes.objectNode().set<ObjectNode>("components", components)
    }
}
