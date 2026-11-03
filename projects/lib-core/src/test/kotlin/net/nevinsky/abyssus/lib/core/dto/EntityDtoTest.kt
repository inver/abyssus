package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import org.junit.Assert.*
import org.junit.Test

class EntityDtoTest {
    private fun entity(header: HeaderComponent?, vararg parts: Component) =
        EntityDto(mutableListOf<Component>().also { list -> header?.let(list::add); list.addAll(parts) })

    @Test
    fun componentFindsTheFirstInstanceOfAClassOrNull() {
        val name = NameComponent("a")
        val e = entity(HeaderComponent(1), name, NameComponent("b"))
        assertSame(name, e.component(NameComponent::class.java))
        assertNull(e.component(PositionComponent::class.java))
    }

    @Test
    fun anEntityWithoutAHeaderIsActive() = assertTrue(entity(null, NameComponent("a")).isActive())

    @Test
    fun aDisabledEntityIsNotActiveAndHasNoActiveComponent() {
        val e = entity(HeaderComponent(1, entityEnabled = false), NameComponent("a"))
        assertFalse(e.isActive())
        assertFalse(e.isActive(NameComponent::class.java))
        assertNull(e.activeComponent(NameComponent::class.java))
    }

    @Test
    fun aComponentListedAsDisabledIsNotActive() {
        val header = HeaderComponent(1, disabledComponents = linkedSetOf(NameComponent::class.java))
        val e = entity(header, NameComponent("a"), PositionComponent())
        assertFalse(e.isActive(NameComponent::class.java))
        assertNull(e.activeComponent(NameComponent::class.java))
        assertNotNull(e.activeComponent(PositionComponent::class.java))
    }

    @Test
    fun aMissingComponentIsNotActive() = assertFalse(entity(HeaderComponent(1)).isActive(NameComponent::class.java))
}
