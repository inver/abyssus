/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages

/**
 * Removes one entity from a scene and nothing else: its assets stay, and another entity is never deleted, detached or
 * retargeted. Removal is refused, leaving the scene as it was, when a remaining entity still names the target
 * (built-in references, a path arrangement's slots, or an entity-reference field of one of [kinds]) or when the target
 * is an arrangement that still owns copies. Unknown components are opaque: no reference is guessed from their numbers.
 */
class EntityRemoval(
    private val messages: EditorMessages,
    kinds: List<ComponentKind<*>> = emptyList(),
) {
    private val references = EntityReferences(kinds)

    /** Removes [entityId] from [scene]; the scene is only touched for [EditResult.Changed]. */
    fun remove(scene: SceneDto, entityId: Long): EditResult {
        if (scene.entity(entityId) == null) return reject("entityRemovalMissing", entityId)
        blocker(scene, entityId)?.let { return EditResult.Rejected(it) }
        scene.ecs.remove(entityId.toLong())
        return EditResult.Changed
    }

    /** The reason [entityId] cannot go, or null. */
    private fun blocker(scene: SceneDto, entityId: Long): String? {
        ownedCopies(scene, entityId)?.let { return messages.message("entityRemovalOwnsCopies", entityId, it) }
        for ((id, entity) in scene.entities()) {
            if (id == entityId) continue
            for (component in entity.components) {
                if (component is PathArrangementComponent && component.slots.any { it.toLong() == entityId }) {
                    return messages.message("entityRemovalInSlot", entityId, id.toString())
                }
                references.fieldNaming(component, entityId)?.let {
                    return messages.message("entityRemovalReferenced", entityId, id.toString(), "${component.javaClass.simpleName}.$it")
                }
            }
        }
        return null
    }

    /** The ids of the copies [entityId] owns as an arrangement, joined for the message, or null. */
    private fun ownedCopies(scene: SceneDto, entityId: Long): String? {
        val arrangement = scene.entity(entityId)?.components?.filterIsInstance<PathArrangementComponent>()?.firstOrNull()
            ?: return null
        return arrangement.slots.filter { it != NO_ENTITY && scene.entity(it.toLong()) != null }
            .takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun reject(key: String, vararg params: Any) = EditResult.Rejected(messages.message(key, *params))
}
