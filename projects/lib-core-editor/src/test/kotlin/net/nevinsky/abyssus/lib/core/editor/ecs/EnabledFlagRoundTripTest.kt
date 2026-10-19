/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.ecs

import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.ecs.EnabledState
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
        val ecs = SceneJson().parse("""{"entities":{"1":$entity}}""")
        val engine = SceneEngine()
        val warns = EcsLoader(json, registry).loadToEngine(ecs, engine)
        edit(engine)
        return SceneJson().compact(EcsWriter(json, registry).write(engine, warns)["entities"]["1"])
    }

    @Test
    fun aDisabledComponentKeepsItsFlagAsTheLastKey() {
        val entity = """{"components":{"NameComponent":{"name":"a","enabled":false}}}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun aDisabledEntityKeepsItsFlagAfterTheComponents() {
        val entity = """{"components":{"NameComponent":{"name":"a"}},"enabled":false}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun aSourceBackedComponentKeepsTheFlagOutOfItsSource() {
        val entity = """{"components":{"FoliageComponent":{"assetName":"m","extra":{"w":2.50},"enabled":false}}}"""
        assertEquals(entity, roundTrip(entity))
    }

    @Test
    fun enablingInTheEngineDropsTheFlag() {
        val entity = """{"components":{"NameComponent":{"name":"a","enabled":false}}}"""
        val out = roundTrip(entity) { it.entities.single().getComponent(EnabledState::class.java).disabledComponents.clear() }
        assertEquals("""{"components":{"NameComponent":{"name":"a"}}}""", out)
    }

    @Test
    fun aSceneWithoutTheFlagIsWrittenWithoutIt() {
        val entity = """{"components":{"NameComponent":{"name":"a"}}}"""
        assertEquals(entity, roundTrip(entity))
    }
}
