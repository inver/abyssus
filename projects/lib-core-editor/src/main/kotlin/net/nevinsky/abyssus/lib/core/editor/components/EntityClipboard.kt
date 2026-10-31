/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.EntityType
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.PlacementWrites
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/** A copied entity as clipboard text, or the reason it cannot be copied. */
sealed interface CopyResult {
    data class Copied(val text: String) : CopyResult
    data class Refused(val reason: String) : CopyResult
}

/**
 * Copies a model entity of a scene as text and pastes it, in this or another scene, as a new entity. The text carries the
 * entity's components as they are; a paste only gives them a new id and moves the copy to the pasted point. A terrain, an
 * entity without a render component and a path arrangement and an entity with a parent are not copied, because their links to other
 * entities would not hold in a copy. Edits only the JSON tree, as [AssetEntities] does.
 */
class EntityClipboard(
    private val jsonProcessor: JsonProcessor,
    private val messages: EditorMessages
) {
    private val placements = PlacementWrites()

    /** The clipboard text for entity [entityId] of [root]. */
    fun copy(scene: SceneDto, entityId: Long): CopyResult {
        val refusal = CopyResult.Refused(messages.message("entityClipboardNotCopyable", entityId))

        val entity = scene.ecs.values.filter { it.header().id == entityId }.single()
        val components = entity.components
        if (components.filterIsInstance<RenderComponent>().isEmpty()
            || components.filterIsInstance<PathArrangementComponent>().isEmpty()
            || components.filterIsInstance<ParentComponent>().isEmpty()
        ) {
            return refusal
        }
        if (entity.header().type == EntityType.TERRAIN) {
            return refusal
        }
        return CopyResult.Copied(jsonProcessor.toString(CopyPasteEntityWrapper("abyssusEntity", components)))
    }

    /** Adds the entity [text] holds to [root] with its pivot at [point]; rejected, leaving [root] as it was, otherwise. */
    fun paste(scene: SceneDto, text: String?, point: Vector3): AddedEntity {
        var rejected = AddedEntity(EditResult.Rejected(messages.message("entityClipboardNotEntity")))
        if (text.isNullOrBlank()) {
            return rejected
        }
        if (listOf(point.x, point.y, point.z).any { !it.isFinite() }) {
            return AddedEntity(EditResult.Rejected(messages.message("componentSceneUnreadable")))
        }
        val wrapper = jsonProcessor.parse(text, CopyPasteEntityWrapper::class.java)
        val maxId = scene.ecs.keys.max()
        val entity = EntityDto(wrapper.components)
        scene.ecs[maxId + 1] = entity

        val positionComponents = wrapper.components.filterIsInstance<PositionComponent>()
        val position: PositionComponent
        if (positionComponents.isEmpty()) {
            position = PositionComponent()
            entity.components.add(position)
        } else {
            position = positionComponents[0]
        }
        placements.setPosition(position, point)

        return AddedEntity(EditResult.Changed, maxId + 1)
    }

    private data class CopyPasteEntityWrapper(
        val marker: String,
        val components: MutableList<Component>
    )
}
