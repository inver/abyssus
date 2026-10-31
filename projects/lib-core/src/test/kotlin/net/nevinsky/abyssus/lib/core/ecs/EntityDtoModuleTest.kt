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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityDtoModuleTest {
    private fun entity(text: String) = sceneWithEcs("""{"1":$text}""").ecs.getValue(1L)

    private fun com.badlogic.ashley.core.Entity.body(): List<Component> =
        components.toList().filter { it !is HeaderComponent }

    private fun com.badlogic.ashley.core.Entity.header() = getComponent(HeaderComponent::class.java)

    @Test
    fun componentsBindInFileOrderAndFlagsDefaultToZero() {
        val e = entity("""{"components":{"PositionComponent":{},"NameComponent":{"name":"a"}}}""")
        assertEquals(listOf(PositionComponent::class.java, NameComponent::class.java), e.body().map { it.javaClass })
        assertEquals(0, e.header().flags)
        assertEquals(1L, e.header().id)
        assertTrue(e.header().entityEnabled)
    }

    @Test
    fun theDisabledBitDisablesTheEntityAndOtherBitsAreKept() {
        val e = entity("""{"flags":5,"components":{}}""")
        assertFalse(e.header().entityEnabled)
        assertEquals(4, e.header().flags)
    }

    @Test
    fun aComponentsOwnEnabledFlagIsStrippedBeforeBinding() {
        val e = entity("""{"disabledComponents":["NameComponent"],"components":{"NameComponent":{"name":"a"}}}""")
        assertEquals("a", (e.body().single() as NameComponent).name)
        assertEquals(setOf(NameComponent::class.java), e.header().disabledComponents)
    }

    @Test
    fun anUnknownOrUnbindableComponentIsKeptRawWithItsReason() {
        val e = entity("""{"components":{"PickableComponent":{"w":2.50},"PositionComponent":{"localPosition":"x"}}}""")
        val raw = e.getComponent(RawComponentsWrapper::class.java).entries
        assertEquals(listOf("PickableComponent", "PositionComponent"), raw.map { it.name })
        assertTrue(raw[0].reason, raw[0].reason.contains("not modeled"))
        assertEquals("""{"w":2.50}""", raw[0].json)
    }
}
