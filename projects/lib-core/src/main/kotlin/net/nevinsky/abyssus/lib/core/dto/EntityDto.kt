package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component


/**
 * One `ecs` entry: its [type], the integer [flags], the classes of the components its `disabledComponents` lists, and its
 * components in file order (a component this program cannot model is a
 * [net.nevinsky.abyssus.lib.core.ecs.component.RawComponent]). The first three are the entity's header
 * ([net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent]).
 */
data class EntityDto(
    val components: MutableList<Component> = mutableListOf(),
) {
    fun add(component: Component) {
        components.add(component)
    }
}
