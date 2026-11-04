/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.LightComponent
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class EntityClipboardTest {
    private val clipboard = EntityClipboard(JsonProcessor(NOPLogger.NOP_LOGGER), ResourceEditorMessages())

    private fun scene(): SceneDto =
        parseScene(
            """{"format":"abyssus","formatVersion":1,"ecs":{
          "1":{"type":"OBJECT","components":{"NameComponent":{"name":"tree"},
            "PositionComponent":{"localPosition":{"x":1,"y":2,"z":3}},
            "RenderComponent":{"shaderKey":"defaultShader","type":"MODEL","assetName":"tree"},
            "VendorComponent":{"gain":2.5}}},
          "2":{"type":"LIGHT_POINT","components":{"NameComponent":{"name":"lamp"}}},
          "3":{"type":"TERRAIN","components":{"RenderComponent":{"shaderKey":"terrain","type":"TERRAIN",
"assetName":"hills"}}},
          "4":{"type":"OBJECT","components":{"RenderComponent":{"type":"MODEL","assetName":"tree"},
"ParentComponent":{"parentEntityId":1}}}}}"""
        )

    private fun position(scene: SceneDto, id: Long): Vector3 =
        scene.component(id, PositionComponent::class.java)!!.localPosition

    @Test
    fun aPasteIsANewEntityAtThePointInAnotherScene() {
        val source = scene()
        val text = (clipboard.copy(source, 1) as CopyResult.Copied).text
        val target = scene()
        val before = target.ecs.size
        val pasted = clipboard.paste(target, text, Vector3(10f, 0f, -4f))
        assertEquals(EditResult.Changed, pasted.result)
        assertEquals(before + 1, target.ecs.size)
        val id = pasted.entityId!!
        assertEquals(5L, id)
        assertEquals(Vector3(10f, 0f, -4f), position(target, id))
        assertEquals("tree", target.component(id, RenderComponent::class.java)!!.assetName)
        assertEquals("tree", target.component(id, NameComponent::class.java)!!.name)
        assertEquals(EntityType.OBJECT, target.entity(id)!!.header().type)
        assertEquals(Vector3(1f, 2f, 3f), position(source, 1))
    }

    @Test
    fun eachPasteAddsAnotherEntity() {
        val source = scene()
        val text = (clipboard.copy(source, 1) as CopyResult.Copied).text
        val first = clipboard.paste(source, text, Vector3(0f, 0f, 0f)).entityId
        val second = clipboard.paste(source, text, Vector3(5f, 0f, 0f)).entityId
        assertTrue(first != second && first != 1L && second != 1L)
    }

    @Test
    fun aComponentTheEditorDoesNotModelTravelsAsItsText() {
        val source = scene()
        val text = (clipboard.copy(source, 1) as CopyResult.Copied).text
        val id = clipboard.paste(source, text, Vector3()).entityId!!
        val raw = source.entity(id)!!.component(RawComponentsWrapper::class.java)!!.entries.single()
        assertEquals("VendorComponent", raw.name)
        assertTrue(raw.json, raw.json.contains("2.5"))
    }

    @Test
    fun anEntityWithoutPositionGetsOneAtThePoint() {
        val source = scene()
        val text = (clipboard.copy(source, 1) as CopyResult.Copied).text
        source.entity(1)!!.components.removeAll { it is PositionComponent }
        val id = clipboard.paste(source, text, Vector3(7f, 8f, 9f)).entityId!!
        assertEquals(Vector3(7f, 8f, 9f), position(source, id))
    }

    @Test
    fun aLightIsPastedInAnotherSceneAtItsTypesHeight() {
        val source = parseScene(
            """{"format":"abyssus","formatVersion":1,"ecs":{
          "1":{"type":"LIGHT_SPOT","components":{"NameComponent":{"name":"lamp"},
            "PositionComponent":{"localPosition":{"x":1,"y":9,"z":3}},
            "LightComponent":{"light":{"intensity":2.5}}}},
          "2":{"type":"LIGHT_DIRECTIONAL","components":{"LightComponent":{"light":{"intensity":1.5}}}}}}"""
        )
        val target = scene()
        val spot = clipboard.paste(target, (clipboard.copy(source, 1) as CopyResult.Copied).text, Vector3(10f, 1f, -4f))
        assertEquals(EditResult.Changed, spot.result)
        assertEquals(Vector3(10f, 6f, -4f), position(target, spot.entityId!!))
        assertEquals(2.5f, target.component(spot.entityId!!, LightComponent::class.java)!!.light.intensity, 0f)
        assertEquals(EntityType.LIGHT_SPOT, target.entity(spot.entityId!!)!!.header().type)
        val sun = clipboard.paste(target, (clipboard.copy(source, 2) as CopyResult.Copied).text, Vector3(0f, 1f, 0f))
        assertEquals(Vector3(0f, 1f, 0f), position(target, sun.entityId!!))
    }

    @Test
    fun aSceneFileLightRoundTrips() {
        val source = parseScene(
            """{"formatVersion":1,"ecs":{"2":{"type":"LIGHT_SPOT","components":{"NameComponent":{"name":"Spot Light 2"},
"PositionComponent":{"localPosition":{"x":-1.4,"y":3.9,"z":0},"localRotation":{"x":0.70710677,"y":0,"z":0,"w":-0.70710677}},
"LightComponent":{"light":{"intensity":1,"range":100,"coneAngle":45,"edgeSoftness":0.2,"color":{"r":1,"g":1,"b":1,"a":1}}}}}}}"""
        )
        val text = (clipboard.copy(source, 2) as CopyResult.Copied).text
        assertTrue(clipboard.holdsEntity(text))
        assertEquals(EditResult.Changed, clipboard.paste(source, text, Vector3(1f, 0f, 1f)).result)
    }

    @Test
    fun entitiesThatCannotBeCopiedAreRefused() {
        val source = scene()
        assertTrue("no render component", clipboard.copy(source, 2) is CopyResult.Refused)
        assertTrue("terrain", clipboard.copy(source, 3) is CopyResult.Refused)
        assertTrue("has a parent", clipboard.copy(source, 4) is CopyResult.Refused)
        assertTrue("missing", clipboard.copy(source, 99) is CopyResult.Refused)
        assertNotNull(source.component(4, ParentComponent::class.java))
    }

    @Test
    fun foreignClipboardTextIsNotPasted() {
        val target = scene()
        val before = target.ecs.size
        assertFalse(clipboard.holdsEntity("hello"))
        assertFalse(clipboard.holdsEntity(null))
        assertTrue(
            clipboard.paste(target, "{\"abyssusEntity\":1,\"components\":{}}", Vector3()).result is EditResult.Rejected
        )
        assertTrue(clipboard.paste(target, "hello", Vector3()).result is EditResult.Rejected)
        assertEquals(before, target.ecs.size)
    }

    @Test
    fun copiedTextIsHeldAndANonFinitePointIsRefused() {
        val target = scene()
        val text = (clipboard.copy(target, 1) as CopyResult.Copied).text
        assertTrue(clipboard.holdsEntity(text))
        assertTrue(clipboard.paste(target, text, Vector3(Float.NaN, 0f, 0f)).result is EditResult.Rejected)
        assertEquals(4, target.ecs.size)
    }
}
