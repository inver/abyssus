/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.ecs

import net.nevinsky.abyssus.lib.core.ecs.component.isActive
import net.nevinsky.abyssus.lib.core.ecs.component.activeComponent
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import net.nevinsky.abyssus.lib.core.testing.loadEcs
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.CameraComponent
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.ecs.component.assetName
import net.nevinsky.abyssus.lib.core.ecs.system.SynchronizeCameraComponentSystem
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class DisabledSystemsTest {
    private fun load(entities: String): SceneEngine {
        val engine = SceneEngine()
        loadEcs("""{$entities}""", engine)
        return engine
    }

    private val model = """"RenderComponent":{"type":"MODEL","assetName":"tree"}"""

    @Test
    fun aDisabledRenderableIsNotRenderedButTheEntityIsPositioned() {
        val engine = load(""""1":{"disabledComponents":["RenderComponent"],"components":{"PositionComponent":{"position":{"x":1,"y":2,"z":3}},"RenderComponent":{"type":"MODEL","assetName":"tree"}}}""")
        val entity = engine.ids[1]!!
        assertNull(assetName(entity, MetaType.MODEL))
        assertNotNull(entity.activeComponent(PositionComponent::class.java))
    }

    @Test
    fun aDisabledEntityRendersNothing() {
        val engine = load(""""1":{"flags":1,"components":{$model}},"2":{"components":{$model}}""")
        assertNull(assetName(engine.ids[1]!!, MetaType.MODEL))
        assertEquals("tree", assetName(engine.ids[2]!!, MetaType.MODEL))
    }

    @Test
    fun togglingDuringUpdateTakesEffectWithoutAReload() {
        val engine = load(""""1":{"components":{$model}}""")
        val entity = engine.ids[1]!!
        assertEquals("tree", assetName(entity, MetaType.MODEL))
        entity.getComponent(HeaderComponent::class.java).entityEnabled = false
        assertNull(assetName(entity, MetaType.MODEL))
        entity.getComponent(HeaderComponent::class.java).entityEnabled = true
        assertEquals("tree", assetName(entity, MetaType.MODEL))
    }

    @Test
    fun aDisabledLightIsNotActive() {
        val engine = load(""""1":{"disabledComponents":["LightComponent"],"components":{"LightComponent":{"light":{"intensity":2}}}},"2":{"components":{"LightComponent":{"light":{"intensity":2}}}}""")
        assertTrue(!engine.ids[1]!!.isActive(LightComponent::class.java))
        assertTrue(engine.ids[2]!!.isActive(LightComponent::class.java))
    }

    @Test
    fun aDisabledFoliageIsNotListed() {
        val engine = load(""""1":{"disabledComponents":["FoliageComponent"],"components":{"RenderComponent":{"type":"TERRAIN","assetName":"t"},"FoliageComponent":{"assetName":"f"}}},"2":{"components":{"RenderComponent":{"type":"TERRAIN","assetName":"u"},"FoliageComponent":{"assetName":"g"}}}""")
        assertEquals(listOf("g"), SceneFoliageListing {}.list(engine).map { it.foliage })
        engine.ids[1]!!.getComponent(HeaderComponent::class.java).disabledComponents.clear()
        assertEquals(listOf("f", "g"), SceneFoliageListing {}.list(engine).map { it.foliage })
    }

    @Test
    fun theCameraSystemSkipsADisabledCamera() {
        val engine = SceneEngine()
        val ids = engine.ids
        val entity = com.badlogic.ashley.core.Entity()
        val position = PositionComponent()
        position.localPosition.set(5f, 6f, 7f)
        val camera = CameraComponent()
        entity.add(position)
        entity.add(camera)
        entity.add(HeaderComponent(1, disabledComponents = linkedSetOf(CameraComponent::class.java)))
        engine.addEntity(entity)
        val system = SynchronizeCameraComponentSystem(ids)
        engine.addSystem(system)
        engine.update(0.016f)
        assertEquals(0f, camera.camera.position.x, 0f)
        entity.getComponent(HeaderComponent::class.java).disabledComponents.clear()
        engine.update(0.016f)
        assertEquals(5f, camera.camera.position.x, 0f)
    }
}
