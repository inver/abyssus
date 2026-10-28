/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.SceneEntityTree

/**
 * Removes one entity from a scene's JSON tree and nothing else: its assets stay, and another entity is never deleted,
 * detached or retargeted. Removal is refused, leaving the tree as it was, when a remaining entity still names the target
 * (built-in references, a path arrangement's slots, or an entity-reference field of one of [kinds]) or when the target
 * is an arrangement that still owns copies. Unknown components are opaque: no reference is guessed from their numbers.
 */
class EntityRemoval(
    private val messages: EditorMessages,
    kinds: List<ComponentKind<*>> = emptyList(),
) {
    private val byName: Map<String, ComponentKind<*>> =
        kinds.flatMap { listOf(it.name to it, it.codec.type.simpleName to it) }.toMap()

    /** Removes [entityId] from [root]; the tree is only touched for [EditResult.Changed]. */
    fun remove(root: JsonNode, entityId: String): EditResult {
        val entities =
            SceneEntityTree(root).entities() as? ObjectNode ?: return reject("entityRemovalMissing", entityId)
        if (!entities.has(entityId)) return reject("entityRemovalMissing", entityId)
        blocker(entities, entityId)?.let { return EditResult.Rejected(it) }
        entities.remove(entityId)
        return EditResult.Changed
    }

    /** The reason [entityId] cannot go, or null. */
    private fun blocker(entities: ObjectNode, entityId: String): String? {
        ownedCopies(entities, entityId)?.let { return messages.message("entityRemovalOwnsCopies", entityId, it) }
        for ((id, entity) in entities.properties()) {
            if (id == entityId) continue
            val components = entity["components"] as? ObjectNode ?: continue
            for ((key, node) in components.properties()) {
                val kindName = key.substringAfterLast('.')
                if (kindName == PathArrangementComponent::class.simpleName && slotIds(node).any { it.toString() == entityId }) {
                    return messages.message("entityRemovalInSlot", entityId, id)
                }
                field(kindName, node, entityId)?.let {
                    return messages.message("entityRemovalReferenced", entityId, id, "$kindName.$it")
                }
            }
        }
        return null
    }

    /** The first field of the component [kindName] ([node]) that names [entityId], or null. */
    private fun field(kindName: String, node: JsonNode, entityId: String): String? {
        BUILT_IN[kindName]?.firstOrNull {
            node[it]?.takeIf(JsonNode::isIntegralNumber)?.asInt()?.toString() == entityId
        }
            ?.let { return it }
        val kind = byName[kindName] ?: return null
        val fields = kind.fields.filter { it.kind == FieldKind.ENTITY_REF && it.name !in BUILT_IN[kindName].orEmpty() }
        if (fields.isEmpty()) return null
        return namedBy(kind, fields, node, entityId)
    }

    private fun <C : com.badlogic.ashley.core.Component> namedBy(
        kind: ComponentKind<C>, fields: List<ComponentField<*>>, node: JsonNode, entityId: String,
    ): String? {
        val component = runCatchingKeepingCancellation { kind.codec.read(node) }.getOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return fields.firstOrNull { (it as ComponentField<C>).get(component).trim() == entityId }?.name
    }

    /** The ids of the copies [entityId] owns as an arrangement, joined for the message, or null. */
    private fun ownedCopies(entities: ObjectNode, entityId: String): String? {
        val node = entities[entityId]?.get("components")?.properties()?.asSequence()
            ?.firstOrNull { it.key.substringAfterLast('.') == PathArrangementComponent::class.simpleName }?.value
            ?: return null
        return slotIds(node).filter { it != NO_ENTITY && entities.has(it.toString()) }.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
    }

    private fun slotIds(node: JsonNode): List<Int> =
        node["slots"]?.mapNotNull { it.takeIf(JsonNode::isIntegralNumber)?.asInt() }.orEmpty()

    private fun reject(key: String, vararg params: Any) = EditResult.Rejected(messages.message(key, *params))
}

/** Built-in components and the integer members that name another entity. */
private val BUILT_IN = mapOf(
    "ParentComponent" to listOf("parentEntityId"),
    "PositionComponent" to listOf("lookAtId"),
)
