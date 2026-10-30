/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity

/**
 * What an entity keeps of the `enabled` flags of its `ecs` entry: the entity's own flag and the classes of its disabled
 * components. A disabled thing is present as data and absent as behavior. It is only added to an entity that has a disabled
 * part, so a scene that never uses the flag has none.
 */
class EnabledState(
    var entityEnabled: Boolean = true,
    val disabledComponents: MutableSet<Class<out Component>> = LinkedHashSet(),
) : Component {
    val isDefault: Boolean get() = entityEnabled && disabledComponents.isEmpty()
}

/** True when [entity] is enabled; a disabled entity contributes nothing. */
fun Entity.isActive(): Boolean = getComponent(EnabledState::class.java)?.entityEnabled ?: true

/** True when [entity] and its component of class [type] are both enabled. A missing component is not active. */
fun Entity.isActive(type: Class<out Component>): Boolean {
    val state = getComponent(EnabledState::class.java)
    if (state != null && (!state.entityEnabled || type in state.disabledComponents)) return false
    return getComponent(type) != null
}

/** The [type] component of this entity when it and the entity are enabled, else null. */
fun <T : Component> Entity.activeComponent(type: Class<T>): T? = if (isActive(type)) getComponent(type) else null
