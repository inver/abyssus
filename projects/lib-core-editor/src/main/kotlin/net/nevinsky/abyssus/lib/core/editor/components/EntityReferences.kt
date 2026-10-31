/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent

/**
 * Which component fields name another entity: the built-in `lookAtId` and `parentEntityId`, plus every
 * [FieldKind.ENTITY_REF] field of [kinds]. Component removal and entity removal both ask it, so a reference contributed
 * by a component schema is honoured by both.
 */
class EntityReferences(private val kinds: List<ComponentKind<*>> = emptyList()) {
    /** The first field of [component] that names [entityId], or null. */
    fun fieldNaming(component: Component, entityId: Long): String? {
        when (component) {
            is ParentComponent -> if (component.parentEntityId.toLong() == entityId) return "parentEntityId"
            is PositionComponent -> if (component.lookAtId.toLong() == entityId) return "lookAtId"
        }
        val kind = kinds.firstOrNull { it.type.isInstance(component) } ?: return null
        return namedBy(kind, component, entityId)
    }

    private fun <C : Component> namedBy(kind: ComponentKind<C>, component: Component, entityId: Long): String? {
        val typed = kind.type.cast(component)
        return kind.fields.firstOrNull { it.kind == FieldKind.ENTITY_REF && it.get(typed).trim() == entityId.toString() }?.name
    }
}
