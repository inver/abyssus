package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component

/** Bit of [EntityDto.flags] that disables the entity. */
const val ENTITY_DISABLED_FLAG = 1

/**
 * One `ecs` entry: its [type], the integer [flags], the classes of the components its `disabledComponents` lists, and its
 * components in file order (a component this program cannot model is a
 * [net.nevinsky.abyssus.lib.core.ecs.component.RawComponent]). The first three are the entity's header
 * ([net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent]).
 */
data class EntityDto(
    val components: List<Component> = emptyList(),
    val flags: Int = 0,
    val disabledComponents: Set<Class<out Component>> = emptySet(),
    val type: EntityType? = null,
) {
    val enabled: Boolean get() = flags and ENTITY_DISABLED_FLAG == 0
}
