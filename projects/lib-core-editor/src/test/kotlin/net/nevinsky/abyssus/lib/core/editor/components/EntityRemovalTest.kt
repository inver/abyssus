/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import java.io.File
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RawComponentsWrapper
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.entityOf
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.sceneOf
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityRemovalTest {
    private val messages = ResourceEditorMessages()
    private val removal = EntityRemoval(messages)
    private val untitled = File(testProject("Untitled"), "scenes/Main Scene.scene").readText()

    private fun plain(id: Long) = entityOf(id, EntityType.OBJECT, NameComponent("e$id"))

    private fun with(id: Long, vararg parts: Component) = entityOf(id, EntityType.OBJECT, *parts)

    private fun rejected(result: EditResult): String {
        assertTrue("expected Rejected, was $result", result is EditResult.Rejected)
        return (result as EditResult.Rejected).reason
    }

    private fun arrangement(vararg slots: Int) = PathArrangementComponent().also { it.slots = slots.toMutableList() }

    @Test
    fun removesAnUnreferencedEntity() {
        val scene = sceneOf(plain(0), plain(1))
        assertEquals(EditResult.Changed, removal.remove(scene, 0L))
        assertEquals(setOf(1L), scene.ecs.keys)
    }

    @Test
    fun missingTargetIsRejectedAndLeavesTheScene() {
        val scene = sceneOf(plain(0))
        assertTrue(rejected(removal.remove(scene, 7L)).contains("7"))
        assertEquals(setOf(0L), scene.ecs.keys)
    }

    @Test
    fun aLookAtReferenceBlocks() {
        val scene = sceneOf(plain(0), with(1, PositionComponent(lookAtId = 0)))
        val reason = rejected(removal.remove(scene, 0L))
        assertTrue(reason, reason.contains("entity 1") && reason.contains("PositionComponent.lookAtId"))
        assertEquals(setOf(0L, 1L), scene.ecs.keys)
    }

    @Test
    fun unmodeledPayloadsAreNotInterpretedAsReferences() {
        val raw =
            RawComponentsWrapper().also {
                it.entries.add(RawComponent("Mystery", """{"target":0,"lookAtId":0}""", "not modeled", false))
            }
        val scene = sceneOf(plain(0), with(1, raw))
        assertEquals(EditResult.Changed, removal.remove(scene, 0L))
        assertEquals(setOf(1L), scene.ecs.keys)
    }

    @Test
    fun parentBlocks() {
        val scene = sceneOf(plain(0), with(1, ParentComponent(0)))
        assertTrue(rejected(removal.remove(scene, 0L)).contains("ParentComponent.parentEntityId"))
        assertEquals(setOf(0L, 1L), scene.ecs.keys)
    }

    @Test
    fun registeredEntityReferenceFieldBlocks() {
        class Link : Component {
            var other = -1
        }
        val kind =
            ComponentKind(
                "LinkComponent",
                Link::class.java,
                listOf(
                    ComponentField<Link>(
                        "other",
                        FieldKind.ENTITY_REF,
                        { it.other.toString() },
                        { c, t -> c.other = t.toInt() },
                    )
                ),
                { Link() },
            )
        val scene = sceneOf(plain(0), with(1, Link().also { it.other = 0 }))
        val reason = rejected(EntityRemoval(messages, listOf(kind)).remove(scene, 0L))
        assertTrue(reason, reason.contains("entity 1") && reason.contains("Link.other"))
    }

    @Test
    fun arrangementSlotAndOwnedCopiesBlock() {
        val scene = sceneOf(with(0, arrangement(1, -1, 2)), plain(1), plain(2))
        assertTrue(rejected(removal.remove(scene, 1L)).contains("slot"))
        assertTrue(rejected(removal.remove(scene, 0L)).contains("1, 2"))
        assertEquals(setOf(0L, 1L, 2L), scene.ecs.keys)
        // an arrangement whose slots are all empty or dangling owns nothing
        val empty = sceneOf(with(0, arrangement(-1, 9)))
        assertEquals(EditResult.Changed, removal.remove(empty, 0L))
    }

    @Test
    fun clearingTheReferenceThenRemovingWorks() {
        val scene = sceneOf(plain(0), with(1, PositionComponent(lookAtId = 0)))
        rejected(removal.remove(scene, 0L))
        scene.component(1, PositionComponent::class.java)!!.lookAtId = -1
        assertEquals(EditResult.Changed, removal.remove(scene, 0L))
    }

    @Test
    fun removingAnUntitledModelKeepsTheOtherEntities() {
        val scene: SceneDto = parseScene(untitled)
        val before = scene.ecs.size
        assertEquals(EditResult.Changed, removal.remove(scene, 0L))
        assertFalse(scene.ecs.containsKey(0L))
        assertEquals(before - 1, scene.ecs.size)
    }

    @Test
    fun untitledReferencedEntitiesAreRefused() {
        val scene = parseScene(untitled)
        for (id in listOf(3L, 4L)) rejected(removal.remove(scene, id))
        val reason = rejected(removal.remove(scene, 3L))
        assertTrue(reason, reason.contains("entity 4") && reason.contains("lookAtId"))
        rejected(removal.remove(scene, 99L))
    }
}
