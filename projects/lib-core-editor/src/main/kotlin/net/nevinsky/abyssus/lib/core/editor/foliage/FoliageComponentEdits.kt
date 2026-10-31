/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.foliage

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.FoliageComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent

/** The edits that put foliage on a scene's terrain entities and take entities out of a scene. Stateless. */
class FoliageComponentEdits {

    /**
     * Gives the entity [entityId] a `FoliageComponent` naming [foliage]; false when the entity does not exist or
     * already names it.
     */
    fun attach(scene: SceneDto, entityId: Long, foliage: String): Boolean {
        val entity = scene.entity(entityId) ?: return false
        val component = entity.component(FoliageComponent::class.java)
            ?: FoliageComponent().also(entity::add)
        if (component.assetName == foliage) return false
        component.assetName = foliage
        return true
    }

    /**
     * Removes the entities [ids] from the scene. Nothing changes and the first reference is returned when a remaining
     * entity still refers to a removed one (as its parent or look-at target).
     */
    fun remove(scene: SceneDto, ids: Set<Long>): Removal {
        val present = ids.filter { scene.entity(it) != null }
        if (present.isEmpty()) return Removal(false, null)
        for ((id, entity) in scene.entities()) {
            if (id in present) continue
            for (component in entity.components) {
                val reference = when (component) {
                    is ParentComponent -> "ParentComponent.parentEntityId" to component.parentEntityId
                    is PositionComponent -> "PositionComponent.lookAtId" to component.lookAtId
                    else -> continue
                }
                if (reference.second.toLong() in present) {
                    return Removal(false, "entity $id refers to entity ${reference.second} through ${reference.first}")
                }
            }
        }
        present.forEach { scene.ecs.remove(it) }
        return Removal(true, null)
    }

    /** What [remove] did: whether the scene changed, and the reference that stopped it, if any. */
    class Removal(val changed: Boolean, val blockedBy: String?)
}
