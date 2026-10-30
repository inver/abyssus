/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class EnabledStateTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val registry = ComponentRegistry()
    private val loader = EcsLoader(json, registry)

    private fun load(entity: String, reg: ComponentRegistry = registry): Pair<SceneEngine, EcsLoadingWarns> {
        val engine = SceneEngine()
        val warns = EcsLoader(json, reg).loadToEngine(NativeJsonTree().parse("""{"entities":{"1":$entity}}"""), engine)
        return engine to warns
    }

    @Test
    fun aSceneWithoutTheFlagHasNoState() {
        val (engine, _) = load("""{"components":{"NameComponent":{"name":"a"}}}""")
        val entity = engine.entities.single()
        assertNull(entity.getComponent(EnabledState::class.java))
        assertTrue(entity.isActive())
        assertTrue(entity.isActive(NameComponent::class.java))
    }

    @Test
    fun aDisabledComponentLoadsAsDataWithoutBehavior() {
        val (engine, warns) = load(
            """{"components":{"NameComponent":{"name":"a","enabled":false},"PositionComponent":{"position":{"x":1,"y":2,"z":3}}}}"""
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
        val (engine, _) = load("""{"enabled":false,"components":{"PositionComponent":{}}}""")
        val entity = engine.entities.single()
        assertFalse(entity.isActive())
        assertFalse(entity.isActive(PositionComponent::class.java))
        assertNull(entity.activeComponent(PositionComponent::class.java))
    }

    @Test
    fun theFlagIsReadAgainWhenTheStateChanges() {
        val (engine, _) = load("""{"enabled":false,"components":{"PositionComponent":{}}}""")
        val entity = engine.entities.single()
        entity.getComponent(EnabledState::class.java).entityEnabled = true
        assertTrue(entity.isActive(PositionComponent::class.java))
    }

    class ClashComponent : Component {
        var enabled: Boolean = true
    }

    @Test
    fun aComponentThatDeclaresEnabledIsReservedAndKeptRaw() {
        val reg = ComponentRegistry()
        reg.register("ClashComponent", ClashComponent::class.java)
        assertTrue(reg.isReserved(ClashComponent::class.java))
        assertEquals(listOf(ClashComponent::class.java), reg.reservedClashes)
        assertFalse(reg.isReserved(NameComponent::class.java))
        val (engine, warns) = load("""{"components":{"ClashComponent":{"enabled":false}}}""", reg)
        assertNull(engine.entities.single().getComponent(ClashComponent::class.java))
        assertEquals(1, warns.carried.size)
        assertTrue(warns.warnings.single().contains("reserves"))
    }
}
