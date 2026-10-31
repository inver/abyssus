/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.ecs

import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import net.nevinsky.abyssus.lib.core.ecs.SceneEngine
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class FoliageEcsRoundTripTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)
    private val registry = ComponentRegistry()
    private val component =
        """{"assetName":"foliage_meadow","note":"x","extra":{"weight":2.5,"epsilon":1.0E-4,"neg":-0.0}}"""

    private fun roundTrip(edit: (FoliageComponent) -> Unit = {}): String {
        val ecs = """{"1":{"components":{"FoliageComponent":$component}}}"""
        val engine = SceneEngine()
        val warns = loadEcs(ecs, engine, registry, json)
        edit(engine.entities.single().getComponent(FoliageComponent::class.java))
        val out = EcsWriter(json, registry).write(engine, warns)
        return SceneJson().compact(out["1"]["components"]["FoliageComponent"])
    }

    @Test
    fun anUntouchedComponentIsWrittenBackExactly() = assertEquals(component, roundTrip())

    @Test
    fun aRenamedComponentChangesOnlyItsName() =
        assertEquals(component.replace("foliage_meadow", "other"), roundTrip { it.assetName = "other" })
}
