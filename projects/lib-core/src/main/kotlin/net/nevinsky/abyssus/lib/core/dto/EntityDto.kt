package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.annotation.JsonIgnore
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent


/**
  * One `ecs` entry: its [type], the integer [flags], the classes of the components its `disabledComponents` lists, and
  * its
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

    @JsonIgnore
    fun header(): HeaderComponent {
        return components.filterIsInstance<HeaderComponent>().first()
    }

    /** The first component that is an instance of [type], or null. */
    @JsonIgnore
    fun <C : Component> component(type: Class<C>): C? =
        components.firstOrNull { type.isInstance(it) }?.let(type::cast)

    /**
     * True when the entity is enabled; a disabled entity contributes nothing. An entity without a header is enabled.
     */
    @JsonIgnore
    fun isActive(): Boolean = component(HeaderComponent::class.java)?.entityEnabled ?: true

    /** True when the entity and its component of class [type] are both enabled. A missing component is not active. */
    @JsonIgnore
    fun isActive(type: Class<out Component>): Boolean {
        val header = component(HeaderComponent::class.java)
        if (header != null && (!header.entityEnabled || type in header.disabledComponents)) {
            return false
        }
        return component(type) != null
    }

    /** The [type] component when the entity and the component are both enabled, else null. */
    @JsonIgnore
    fun <C : Component> activeComponent(type: Class<C>): C? = if (isActive(type)) {
        component(type)
    } else {
        null
    }
}
