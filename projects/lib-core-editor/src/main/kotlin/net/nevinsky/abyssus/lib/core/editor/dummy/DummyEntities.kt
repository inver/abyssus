/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.dummy

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.arrange.arrangementOwnerOf
import net.nevinsky.abyssus.lib.core.editor.components.AddedEntity
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.format.EntityType

/**
 * Adds a dummy (OpenSpec `scene-dummies`): a `DUMMY` entity with a `NameComponent`, an empty `PositionComponent` (the
 * parent's origin) and a `ParentComponent` to the parent, under the next free id. Its name is `Dummy`, or `Dummy 2`,
 * `Dummy 3`... when a sibling already has that name. Edits only the [SceneDto]; callers write it through
 * `editSceneDocument` as one undoable command.
 */
class DummyEntities(private val messages: EditorMessages) {
    /** Whether a dummy can be added under [parentId]: it exists and is neither a path arrangement nor a linked copy. */
    fun canAdd(scene: SceneDto, parentId: Long): Boolean {
        val parent = scene.entity(parentId) ?: return false
        return parent.component(PathArrangementComponent::class.java) == null && arrangementOwnerOf(scene,
                parentId) == null
    }

    /**
     * Adds a dummy under [parentId]. Unchanged for a parent the scene does not have; Rejected for an arrangement or one
     * of its linked copies.
     */
    fun add(scene: SceneDto, parentId: Long): AddedEntity {
        if (scene.entity(parentId) == null) {
            return AddedEntity(EditResult.Unchanged)
        }
        if (!canAdd(scene, parentId)) {
            return AddedEntity(EditResult.Rejected(messages.message("dummyRefusedOnArrangement", parentId)))
        }
        val name = uniqueName(scene, parentId)
        val id = scene.add(EntityType.DUMMY) {
            listOf(NameComponent(name), PositionComponent(), ParentComponent(parentId.toInt()))
        }
        return AddedEntity(EditResult.Changed, id)
    }

    private fun uniqueName(scene: SceneDto, parentId: Long): String {
        val taken = scene.ecs.values
            .filter { it.component(ParentComponent::class.java)?.parentEntityId?.toLong() == parentId }
            .mapNotNull { it.component(NameComponent::class.java)?.name }
            .toSet()
        val base = messages.message("dummyName")
        if (base !in taken) {
            return base
        }
        return generateSequence(2) { it + 1 }
            .map { messages.message("dummyNameNumbered", it.toString()) }
            .first { it !in taken }
    }
}
