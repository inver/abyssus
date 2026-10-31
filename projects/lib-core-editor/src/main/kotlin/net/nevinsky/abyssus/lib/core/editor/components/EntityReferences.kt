/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

/**
 * Which component fields name another entity: the built-in `lookAtId` and `parentEntityId`, plus every
 * [FieldKind.ENTITY_REF] field of [kinds]. Component removal and entity removal both ask it, so a reference contributed
 * by a component schema is honoured by both.
 */
class EntityReferences(kinds: List<ComponentKind<*>> = emptyList()) {
    private val byName: Map<String, ComponentKind<*>> =
        kinds.flatMap { listOf(it.name to it, it.codec.type.simpleName to it) }.toMap()

    /** The first field of the component [kindName] ([node]) that names [entityId], or null. */
    fun fieldNaming(kindName: String, node: JsonNode, entityId: String): String? {
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
}

/** Built-in components and the integer members that name another entity. */
private val BUILT_IN = mapOf(
    "ParentComponent" to listOf("parentEntityId"),
    "PositionComponent" to listOf("lookAtId"),
)
