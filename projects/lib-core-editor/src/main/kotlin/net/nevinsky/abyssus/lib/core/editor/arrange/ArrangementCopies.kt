/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.arrange

import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent

/** The entries of the arrangement's `slots` that name an entity (not `-1`); empty for an entity that is no arrangement. */
private fun slotIds(entity: EntityDto?): List<Int> =
    entity?.components?.filterIsInstance<PathArrangementComponent>()?.firstOrNull()?.slots?.filter { it >= 0 }.orEmpty()

private fun parentOf(entity: EntityDto?): Int? =
    entity?.components?.filterIsInstance<ParentComponent>()?.firstOrNull()?.parentEntityId

/**
 * How many copies the arrangement [entity] lists in its `slots` (`-1` marks an empty slot); 0 for an entity that has no
 * `PathArrangementComponent`.
 */
fun arrangementCopyCount(entity: EntityDto): Int = slotIds(entity).size

/**
 * The ids of the linked copies of every path arrangement in [scene]: the entities that an arrangement lists in `slots`
 * and that point back to it with a `ParentComponent`. A detached copy (no `ParentComponent`, or one to another entity)
 * is an ordinary entity and is not in the set.
 */
fun arrangementCopyIds(scene: SceneDto): Set<String> {
    val copies = HashSet<String>()
    for ((id, entity) in scene.ecs) {
        for (slot in slotIds(entity)) {
            if (parentOf(scene.entity(slot.toLong())) == id.toInt()) copies += slot.toString()
        }
    }
    return copies
}

/** The id of the arrangement that [entityId] is a linked copy of (see [arrangementCopyIds]), or null for any other entity. */
fun arrangementOwnerOf(scene: SceneDto, entityId: String): String? {
    val id = entityId.toLongOrNull() ?: return null
    val parent = parentOf(scene.entity(id)) ?: return null
    return parent.toString().takeIf { id.toInt() in slotIds(scene.entity(parent.toLong())) }
}
