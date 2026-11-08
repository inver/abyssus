/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.dummy

import com.badlogic.gdx.math.Vector3
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.arrange.ArrangementEdits
import net.nevinsky.abyssus.lib.core.editor.arrange.GroundHeight
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyEntitiesTest {
    private val messages = ResourceEditorMessages()
    private val dummies = DummyEntities(messages)
    private fun untitled() = parseScene(File(testProject("Untitled"), "scenes/Main Scene.scene").readText())

    @Test
    fun aDummyUnderModelNine() {
        val scene = untitled()
        val next = scene.nextId()
        val added = dummies.add(scene, 9L)
        assertEquals(EditResult.Changed, added.result)
        assertEquals(next, added.entityId)
        val dummy = scene.entity(next)!!
        assertEquals(EntityType.DUMMY, dummy.header().type)
        assertEquals("Dummy", dummy.component(NameComponent::class.java)!!.name)
        assertEquals(Vector3(), dummy.component(PositionComponent::class.java)!!.localPosition)
        assertEquals(9, dummy.component(ParentComponent::class.java)!!.parentEntityId)
        assertEquals(null, dummy.component(ParentComponent::class.java)!!.node)
    }

    @Test
    fun aSecondDummyIsNumbered() {
        val scene = untitled()
        dummies.add(scene, 9L)
        val second = dummies.add(scene, 9L).entityId!!
        assertEquals("Dummy 2", scene.entity(second)!!.component(NameComponent::class.java)!!.name)
        val third = dummies.add(scene, 9L).entityId!!
        assertEquals("Dummy 3", scene.entity(third)!!.component(NameComponent::class.java)!!.name)
        // names are unique among siblings only
        val other = dummies.add(scene, 0L).entityId!!
        assertEquals("Dummy", scene.entity(other)!!.component(NameComponent::class.java)!!.name)
    }

    @Test
    fun anUnknownParentChangesNothing() {
        val scene = untitled()
        val before = scene.ecs.keys.toSet()
        assertEquals(EditResult.Unchanged, dummies.add(scene, 99L).result)
        assertEquals(before, scene.ecs.keys)
    }

    @Test
    fun notOnAnArrangementOrItsCopies() {
        val scene = untitled()
        val edits = ArrangementEdits(messages)
        edits.create(scene, BaseMeta(MetaType.MODEL, "tree"), listOf(Vector3(0f, 0f, 0f), Vector3(40f, 0f, 0f)), false,
            GroundHeight { _, _, y -> y })
        val arrangement = 11L
        val copy = 12L
        assertFalse(dummies.canAdd(scene, arrangement))
        assertFalse(dummies.canAdd(scene, copy))
        assertTrue(dummies.add(scene, arrangement).result is EditResult.Rejected)
        assertTrue(dummies.canAdd(scene, 9L))
    }
}
