/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.scene

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.runtime.ecs.component.IdComponent
import net.nevinsky.abyssus.runtime.ecs.component.RawComponentsComponent

/**
 * Writes a [SceneEngine] as an `ecs` block in the native format. Modeled components are written by their
 * [ComponentCodec], carried ones unchanged, in the order of the file they came from; the [SceneEcsDocument]'s extra
 * members follow the entities. Derived state (combined transform, light instance) is not written.
 */
class SceneEcsWriter(private val codecs: ComponentCodecs = ComponentCodecs(), private val format: net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat = net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat()) {
    private val nodes = JsonNodeFactory.instance

    fun write(engine: SceneEngine, document: SceneEcsDocument): ObjectNode {
        val entities = nodes.objectNode()
        var nextId = (engine.entities.mapNotNull { it.getComponent(IdComponent::class.java)?.id }.maxOrNull() ?: -1) + 1
        for (entity in engine.entities) {
            val id = entity.getComponent(IdComponent::class.java)?.id ?: nextId++
            entities.set<JsonNode>(id.toString(), writeEntity(entity))
        }
        val out = nodes.objectNode()
        out.set<JsonNode>("entities", entities)
        document.extras.forEach { (key, node) -> out.set<JsonNode>(key, node) }
        format.requireEcs(out)
        return out
    }

    private fun writeEntity(entity: Entity): ObjectNode {
        val raw = entity.getComponent(RawComponentsComponent::class.java)
        val components = nodes.objectNode()
        for (name in raw?.order.orEmpty()) {
            raw?.components?.get(name)?.let { components.set<JsonNode>(name, it) } ?: writeKnown(entity, name, components)
        }
        for (codec in codecs.all) if (!components.has(codec.name)) writeKnown(entity, codec.name, components)

        val out = nodes.objectNode()
        raw?.archetype?.let { out.set<JsonNode>("archetype", it) }
        out.set<JsonNode>("components", components)
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeKnown(entity: Entity, name: String, into: ObjectNode) {
        val codec = codecs[name] as ComponentCodec<Component>? ?: return
        entity.getComponent(codec.type)?.let { into.set<JsonNode>(name, codec.write(it)) }
    }
}
