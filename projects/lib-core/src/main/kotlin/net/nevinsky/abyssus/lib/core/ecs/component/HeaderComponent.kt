/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import net.nevinsky.abyssus.lib.core.dto.EntityType

/**
 * What every loaded entity is before its other components: its [id] as the scene file keys it, its [type] (the `type` of its entry, null when it has none), whether it is enabled ([entityEnabled]), the bits of its `flags` this program
 * does not own ([flags], kept so a write puts them back) and the classes of its disabled components. A disabled thing is
 * present as data and absent as behavior.
 */
class HeaderComponent(
    val id: Long = -1,
    var type: EntityType? = null,
    var entityEnabled: Boolean = true,
    val disabledComponents: MutableSet<Class<out Component>> = LinkedHashSet(),
    var flags: Int = 0,
) : Component

/** True when [this] entity is enabled; a disabled entity contributes nothing. An entity without a header is enabled. */
fun Entity.isActive(): Boolean = getComponent(HeaderComponent::class.java)?.entityEnabled ?: true

/** True when this entity and its component of class [type] are both enabled. A missing component is not active. */
fun Entity.isActive(type: Class<out Component>): Boolean {
    val header = getComponent(HeaderComponent::class.java)
    if (header != null && (!header.entityEnabled || type in header.disabledComponents)) {
        return false
    }
    return getComponent(type) != null
}

/** The [type] component of this entity when it and the entity are enabled, else null. */
fun <T : Component> Entity.activeComponent(type: Class<T>): T? = if (isActive(type)) {
    getComponent(type)
} else null
