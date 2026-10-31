/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.*
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class HeaderComponentTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val registry = ComponentRegistry()

    private fun load(entity: String, reg: ComponentRegistry = registry): Pair<SceneEngine, EcsLoadingWarns> {
        val engine = SceneEngine()
        val warns = loadEcs("""{"1":$entity}""", engine, reg, json)
        return engine to warns
    }

    @Test
    fun aSceneWithoutTheFlagHasAnEnabledHeader() {
        val (engine, _) = load("""{"components":{"NameComponent":{"name":"a"}}}""")
        val entity = engine.entities.single()
        assertTrue(entity.getComponent(HeaderComponent::class.java).entityEnabled)
        assertTrue(entity.isActive())
        assertTrue(entity.isActive(NameComponent::class.java))
    }

    @Test
    fun aDisabledComponentLoadsAsDataWithoutBehavior() {
        val (engine, warns) = load(
            """{"disabledComponents":["NameComponent"],"components":{"NameComponent":{"name":"a"},"PositionComponent":{"position":{"x":1,"y":2,"z":3}}}}"""
        )
        assertTrue(warns.warnings.toString(), warns.warnings.isEmpty())
        val entity = engine.entities.single()
        assertEquals("a", entity.getComponent(NameComponent::class.java).name)
        assertFalse(entity.isActive(NameComponent::class.java))
        assertNull(entity.activeComponent(NameComponent::class.java))
        assertTrue(entity.isActive(PositionComponent::class.java))
        assertTrue(entity.isActive())
    }

    @Test
    fun aDisabledEntityContributesNothing() {
        val (engine, _) = load("""{"flags":1,"components":{"PositionComponent":{}}}""")
        val entity = engine.entities.single()
        assertFalse(entity.isActive())
        assertFalse(entity.isActive(PositionComponent::class.java))
        assertNull(entity.activeComponent(PositionComponent::class.java))
    }

    @Test
    fun theFlagIsReadAgainWhenTheStateChanges() {
        val (engine, _) = load("""{"flags":1,"components":{"PositionComponent":{}}}""")
        val entity = engine.entities.single()
        entity.getComponent(HeaderComponent::class.java).entityEnabled = true
        assertTrue(entity.isActive(PositionComponent::class.java))
    }

    class ClashComponent : Component {
        var enabled: Boolean = false
    }

    @Test
    fun aComponentThatDeclaresEnabledIsAnOrdinaryComponent() {
        val reg = ComponentRegistry()
        reg.register("ClashComponent", ClashComponent::class.java)
        val (engine, warns) = load("""{"components":{"ClashComponent":{"enabled":true}}}""", reg)
        assertTrue(engine.entities.single().getComponent(ClashComponent::class.java).enabled)
        assertTrue(warns.warnings.isEmpty())
    }

    @Test
    fun theHeaderCarriesTheTypeAndTheDisabledComponents() {
        val (engine, _) = load("""{"type":"LIGHT_SPOT","disabledComponents":["NameComponent","Mystery"],"components":{"NameComponent":{"name":"a"},"Mystery":{"w":2.50}}}""")
        val header = engine.entities.single().getComponent(HeaderComponent::class.java)
        assertEquals(net.nevinsky.abyssus.lib.core.dto.EntityType.LIGHT_SPOT, header.type)
        assertEquals(setOf(NameComponent::class.java), header.disabledComponents)
    }
}
