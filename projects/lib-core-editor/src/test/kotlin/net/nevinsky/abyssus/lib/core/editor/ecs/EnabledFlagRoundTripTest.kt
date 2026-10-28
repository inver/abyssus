/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.ecs

import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class EnabledFlagRoundTripTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val registry = ComponentRegistry()

    private fun roundTrip(entity: String, edit: (SceneEngine) -> Unit = {}): String {
        val ecs = """{"1":$entity}"""
        val engine = SceneEngine()
        val warns = loadEcs(ecs, engine, registry, json)
        edit(engine)
        return SceneJson().compact(EcsWriter(json, registry).write(engine, warns)["1"])
    }

    @Test
    fun aDisabledComponentStaysListedInTheEntityHeader() {
        val entity = """{"disabledComponents":["NameComponent"],"components":{"NameComponent":{"name":"a"}}}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun aDisabledEntityKeepsItsFlagBeforeTheComponents() {
        val entity = """{"flags":1,"components":{"NameComponent":{"name":"a"}}}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun aSourceBackedComponentKeepsTheFlagOutOfItsSource() {
        val entity = """{"disabledComponents":["FoliageComponent"],"components":{"FoliageComponent":{"assetName":"m","extra":{"w":2.5}}}}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun enablingInTheEngineDropsTheFlag() {
        val entity = """{"disabledComponents":["NameComponent"],"components":{"NameComponent":{"name":"a"}}}"""
        val out = roundTrip(entity) { it.entities.single().getComponent(HeaderComponent::class.java).disabledComponents.clear() }
        assertEquals("""{"components":{"NameComponent":{"name":"a"}}}""", out)
    }

    @Test
    fun theHeaderIsWrittenAsTypeFlagsAndDisabledComponents() {
        val entity = """{"type":"LIGHT_SPOT","flags":5,"disabledComponents":["NameComponent","Mystery"],"components":{"NameComponent":{"name":"a"},"Mystery":{"w":2.50}}}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun aSceneWithoutTheFlagIsWrittenWithoutIt() {
        val entity = """{"components":{"NameComponent":{"name":"a"}}}"""
        assertEquals(entity, roundTrip(entity))
    }
}
