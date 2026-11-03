/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.testing.sceneWithEcs
import org.junit.Assert.*
import org.junit.Test

class EntityDtoModuleTest {
    private fun entity(text: String) = sceneWithEcs("""{"1":$text}""").ecs.getValue(1L)

    private fun com.badlogic.ashley.core.Entity.body(): List<Component> =
        components.toList().filter { it !is HeaderComponent }

    private fun com.badlogic.ashley.core.Entity.header() = getComponent(HeaderComponent::class.java)

    @Test
    fun componentsBindInFileOrderAndFlagsDefaultToZero() {
        val e = entity("""{"components":{"PositionComponent":{},"NameComponent":{"name":"a"}}}""")
        assertEquals(
            setOf(HeaderComponent::class.java, PositionComponent::class.java, NameComponent::class.java),
            e.components.map { it.javaClass }.toSet()
        )
        val header = e.components.filterIsInstance<HeaderComponent>().first()
        assertEquals(0, header.flags)
        assertEquals(1L, header.id)
        assertTrue(header.entityEnabled)
    }

    @Test
    fun theDisabledBitDisablesTheEntityAndOtherBitsAreKept() {
        val e = entity("""{"flags":5,"components":{}}""")
        val header = e.components.filterIsInstance<HeaderComponent>().first()
        assertFalse(header.entityEnabled)
        assertEquals(4, header.flags)
    }

    @Test
    fun aComponentsOwnEnabledFlagIsStrippedBeforeBinding() {
        val e = entity("""{"disabledComponents":["NameComponent"],"components":{"NameComponent":{"name":"a"}}}""")
        val header = e.components.filterIsInstance<HeaderComponent>().first()
        assertEquals("a", e.components.filterIsInstance<NameComponent>().single().name)
        assertEquals(setOf(NameComponent::class.java), header.disabledComponents)
    }

    @Test
    fun anUnknownOrUnbindableComponentIsKeptRawWithItsReason() {
        val e = entity("""{"components":{"PickableComponent":{"w":2.50},"PositionComponent":{"localPosition":"x"}}}""")
        val raw = e.components.filterIsInstance<RawComponentsWrapper>().single().entries
        assertEquals(listOf("PickableComponent", "PositionComponent"), raw.map { it.name })
        assertTrue(raw[0].reason, raw[0].reason.contains("not modeled"))
        assertEquals("""{"w":2.5}""", raw[0].json)
    }
}
