/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.defaults.NO_ENTITY
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.hierarchy.SceneHierarchy

/**
 * Removes one entity and its descendants (its children through `ParentComponent`, theirs, and so on) from a scene, in
 * one edit, and nothing else: their assets stay, and another entity is never deleted, detached or retargeted. Removal
 * is refused, leaving the scene as it was, when an entity outside that set still names one of them (built-in
 * references, a path arrangement's slots, or an entity-reference field of one of [kinds]) or when one of them is an
 * arrangement that still owns copies. References from inside the set (a child's link to its parent, a light's look-at
 * at its own child handle) never block. Unknown components are opaque: no reference is guessed from their numbers.
 */
class EntityRemoval(
    private val messages: EditorMessages,
    kinds: List<ComponentKind<*>> = emptyList(),
) {
    private val references = EntityReferences(kinds)

    /** Removes [entityId] from [scene]; the scene is only touched for [EditResult.Changed]. */
    fun remove(scene: SceneDto, entityId: Long): EditResult {
        if (scene.entity(entityId) == null) {
            return reject("entityRemovalMissing", entityId)
        }
        ownedCopies(scene, entityId)?.let {
            return reject("entityRemovalOwnsCopies", entityId, it)
        }
        val removed = linkedSetOf(entityId).apply { addAll(SceneHierarchy().resolve(scene).descendantsOf(entityId)) }
        blocker(scene, removed)?.let {
            return EditResult.Rejected(it)
        }
        removed.forEach { scene.ecs.remove(it) }
        return EditResult.Changed
    }

    /** The reason the entities of [removed] cannot go, or null. */
    private fun blocker(scene: SceneDto, removed: Set<Long>): String? {
        for (target in removed) {
            if (target != removed.first()) {
                ownedCopies(scene, target)?.let {
                    return messages.message("entityRemovalOwnsCopies", target, it)
                }
            }
        }
        for ((id, entity) in scene.entities()) {
            if (id in removed) {
                continue
            }
            for (component in entity.components) {
                for (target in removed) {
                    if (component is PathArrangementComponent && component.slots.any { it.toLong() == target }) {
                        return messages.message("entityRemovalInSlot", target, id.toString())
                    }
                    references.fieldNaming(component, target)?.let {
                        return messages.message(
                            "entityRemovalReferenced",
                            target,
                            id.toString(),
                            "${component.javaClass.simpleName}.$it",
                        )
                    }
                }
            }
        }
        return null
    }

    /** The ids of the copies [entityId] owns as an arrangement, joined for the message, or null. */
    private fun ownedCopies(scene: SceneDto, entityId: Long): String? {
        val arrangement = scene.entity(entityId)?.component(PathArrangementComponent::class.java) ?: return null
        return arrangement.slots
            .filter { it != NO_ENTITY && scene.entity(it.toLong()) != null }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
    }

    private fun reject(key: String, vararg params: Any) = EditResult.Rejected(messages.message(key, *params))
}
