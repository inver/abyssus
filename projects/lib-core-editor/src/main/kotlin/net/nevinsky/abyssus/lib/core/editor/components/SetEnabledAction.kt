/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.format.entityTypeOf

/** What an enable or disable edit addresses: a whole entity, or one component entry of it. */
sealed interface EnabledTarget {
    val entityId: Long

    data class Entity(override val entityId: Long) : EnabledTarget

    /** [kind] is the component's key in the scene, short or fully qualified. */
    data class Component(override val entityId: Long, val kind: String) : EnabledTarget
}

/**
 * Enables or disables an entity or a component of a scene tree: disabling a component lists it in the entity's `disabledComponents` (enabling unlists it); an entity is
 * switched by the disabled bit of its `flags`, whose other bits are kept, and nothing else is touched. The Properties check box and the tree eye both run it, through
 * `editSceneJson` or [net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing].
 */
class SetEnabledAction {

    private val FIXED = setOf(
        NameComponent::class.java.simpleName,
        ParentComponent::class.java.simpleName,
        PositionComponent::class.java.simpleName
    )

    /** True when [kind] can be switched: every component except Name, Parent and Position. */
    fun isTogglable(kind: String): Boolean = kind.substringAfterLast('.') !in FIXED

    /** True when the entity entry [entity] can be switched: anything that is not a `GROUP`. */
    fun isTogglable(entity: JsonNode): Boolean =
        entityTypeOf(entity) != "GROUP"


    /**
     * Sets the flag of [target] in the scene tree [root]. Returns false, with [root] untouched, when the entry is
     * missing, cannot be switched, or already has that state.
     */
    fun apply(entities: List<EntityDto>, target: EnabledTarget, enabled: Boolean): Boolean {
        val entity = entities.single { it.header().id == target.entityId }
        if (target is EnabledTarget.Entity) {
            entity.header().entityEnabled = enabled
            return true
        }

        entity.header().disabledComponents.addAll(
            entity.components
                .filter { it.javaClass.simpleName == (target as EnabledTarget.Component).kind }
                .map { it.javaClass }
        )
        return true
    }


}
