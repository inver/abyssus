/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.assetName
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class RenderComponentMeshIdTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    private fun render(body: String): RenderComponent {
        val engine = SceneEngine()
        loadEcs("""{"1":{"components":{"RenderComponent":$body}}}""", engine)
        return engine.ids[1]!!.getComponent(RenderComponent::class.java)
    }

    @Test
    fun loadKeepsTheMeshId() {
        val component = render("""{"type":"MODEL","assetName":"tree","meshId":"glass"}""")
        assertEquals("glass", component.meshId)
        assertEquals("tree", component.assetName)
    }

    @Test
    fun noMeshIdMeansTheWholeModel() {
        assertNull(render("""{"type":"MODEL","assetName":"tree"}""").meshId)
    }

    @Test
    fun writeKeepsItWhenSetAndOmitsItWhenNull() {
        val component = render("""{"type":"MODEL","assetName":"tree","meshId":"glass"}""")
        assertTrue(json.toString(component).contains(""""meshId":"glass""""))
        component.meshId = null
        assertFalse(json.toString(component).contains("meshId"))
    }

    @Test
    fun aTerrainIgnoresItButKeepsIt() {
        val engine = SceneEngine()
        loadEcs("""{"1":{"components":{"RenderComponent":{"type":"TERRAIN","assetName":"t","meshId":"x"}}}}""", engine)
        val entity = engine.ids[1]!!
        assertEquals("t", assetName(entity, MetaType.TERRAIN))
        assertEquals("x", entity.getComponent(RenderComponent::class.java).meshId)
    }
}
