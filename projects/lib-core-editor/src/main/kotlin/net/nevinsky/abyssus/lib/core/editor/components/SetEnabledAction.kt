/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent

/** What an enable or disable edit addresses: a whole entity, or one component entry of it. */
sealed interface EnabledTarget {
    val entityId: Long

    data class Entity(override val entityId: Long) : EnabledTarget

    /** [kind] is the component's key in the scene, short or fully qualified. */
    data class Component(override val entityId: Long, val kind: String) : EnabledTarget
}

/**
 * Enables or disables an entity or a component of a scene: disabling a component lists its class in the entity's disabled
 * components (enabling unlists it); an entity is switched by its header's enabled state; nothing else is touched. The
 * Properties check box and the tree eye both run it, through `editSceneJson` or
 * [net.nevinsky.abyssus.lib.core.editor.headless.HeadlessEditing].
 */
class SetEnabledAction {

    private val FIXED = setOf(
        NameComponent::class.java.simpleName,
        ParentComponent::class.java.simpleName,
        PositionComponent::class.java.simpleName
    )

    /** True when [kind] can be switched: every component except Name, Parent and Position. */
    fun isTogglable(kind: String): Boolean = kind.substringAfterLast('.') !in FIXED

    /** True when [entity] can be switched: anything that is not a `GROUP`. */
    fun isTogglable(entity: EntityDto): Boolean = entity.header().type != EntityType.GROUP

    /**
     * Sets the state of [target] in [scene]. Returns false, with [scene] untouched, when the entity or component is
     * missing, cannot be switched, or already has that state.
     */
    fun apply(scene: SceneDto, target: EnabledTarget, enabled: Boolean): Boolean {
        val entity = scene.entity(target.entityId) ?: return false
        if (!isTogglable(entity)) return false
        val header = entity.header()
        if (target is EnabledTarget.Entity) {
            if (header.entityEnabled == enabled) return false
            header.entityEnabled = enabled
            return true
        }
        val kind = (target as EnabledTarget.Component).kind.substringAfterLast('.')
        if (!isTogglable(kind)) return false
        val component = entity.components.firstOrNull { it.javaClass.simpleName == kind }
        if (component != null) {
            return if (enabled) header.disabledComponents.remove(component.javaClass)
            else header.disabledComponents.add(component.javaClass)
        }
        for (wrapper in entity.components.filterIsInstance<RawComponentsWrapper>()) {
            val at = wrapper.entries.indexOfFirst { it.name.substringAfterLast('.') == kind }
            if (at < 0) continue
            val raw = wrapper.entries[at]
            if (raw.disabled == !enabled) return false
            wrapper.entries[at] = RawComponent(raw.name, raw.json, raw.reason, !enabled)
            return true
        }
        return false
    }
}
