/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.parseScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Text-level cases (undo restores the text, number spelling, key order) are deferred to Phase 6 with the serializer. */
class SetEnabledActionTest {
    private val sceneText = File(testProject("Untitled"), "scenes/Main Scene.scene").readText()
    private val action = SetEnabledAction()
    private val render = EnabledTarget.Component(0L, "RenderComponent")

    private fun scene(): SceneDto = parseScene(sceneText)

    @Test
    fun disablingAComponentListsItInTheEntityHeader() {
        val scene = scene()

        assertTrue(action.apply(scene, render, false))

        val entity = scene.entity(0)!!
        assertEquals(setOf(RenderComponent::class.java), entity.header().disabledComponents)
        assertTrue("the component itself stays", entity.component(RenderComponent::class.java) != null)
    }

    @Test
    fun enablingUnlistsTheComponent() {
        val scene = scene()
        action.apply(scene, render, false)

        assertTrue(action.apply(scene, render, true))

        assertTrue(scene.entity(0)!!.header().disabledComponents.isEmpty())
    }

    @Test
    fun disablingAnEntityClearsItsEnabledStateAndKeepsTheOtherFlagBits() {
        val scene = scene()
        scene.entity(2)!!.header().flags = 4

        assertTrue(action.apply(scene, EnabledTarget.Entity(2L), false))
        assertFalse(scene.entity(2)!!.header().entityEnabled)
        assertEquals(4, scene.entity(2)!!.header().flags)

        assertTrue(action.apply(scene, EnabledTarget.Entity(2L), true))
        assertTrue(scene.entity(2)!!.header().entityEnabled)
        assertEquals(4, scene.entity(2)!!.header().flags)
    }

    @Test
    fun anAlreadyDisabledOrEnabledTargetIsUnchanged() {
        val scene = scene()
        assertFalse(action.apply(scene, render, true))
        action.apply(scene, render, false)
        assertFalse(action.apply(scene, render, false))
    }

    @Test
    fun fixedComponentsAndMissingTargetsAreNotSwitched() {
        val scene = scene()
        for (kind in listOf("NameComponent", "PositionComponent", "ParentComponent"))
            assertFalse(kind, action.apply(scene, EnabledTarget.Component(0L, kind), false))
        assertFalse(action.apply(scene, EnabledTarget.Component(0L, "NoSuchComponent"), false))
        assertFalse(action.apply(scene, EnabledTarget.Entity(999L), false))
        assertTrue(action.isTogglable("RenderComponent"))
        assertTrue(action.isTogglable("net.example.PlaneComponent"))
        assertFalse(action.isTogglable("net.nevinsky.abyssus.lib.core.ecs.component.NameComponent"))
    }

    @Test
    fun aGroupEntityIsNotSwitched() {
        assertFalse(action.isTogglable(entityOf(1, EntityType.GROUP)))
        assertTrue(action.isTogglable(entityOf(2, EntityType.OBJECT)))
    }

    @Test
    fun anUnmodeledComponentIsSwitchedInPlaceAndKeepsItsPayload() {
        val text = """{"format":"abyssus","formatVersion":1,"ecs":{"0":{"type":"OBJECT","components":{"WindComponent":{"speed":1.50,"extra":{"a":1}}}}}}"""
        val scene = parseScene(text)
        val target = EnabledTarget.Component(0L, "WindComponent")
        val raw = { scene.entity(0)!!.component(RawComponentsWrapper::class.java)!!.entries.single() }
        val payload = raw().json

        assertTrue(action.apply(scene, target, false))
        assertTrue(raw().disabled)
        assertEquals(payload, raw().json)

        assertTrue(action.apply(scene, target, true))
        assertFalse(raw().disabled)
        // G4: the payload text is read without exact number spelling (1.50 becomes 1.5), so spelling is not asserted
    }
}
