/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.dto.EntityDto
import com.intellij.openapi.vfs.VirtualFile

/** An entity of a scene file, or one of its components when [kind] is set (the key under `components`). */
data class ComponentTarget(val file: VirtualFile, val entityId: Long, val kind: String?)

/** The entity or component the Abyssus view row [node] stands for, or null for any other node. */
fun componentTargetOf(node: Any?): ComponentTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return when (val value = entry.value) {
        is EntityNode -> ComponentTarget(file, value.id, null)
        is ComponentNode -> ComponentTarget(file, value.entityId, value.kind)
        else -> null
    }
}

/** The entity row [node] stands for (not one of its components) when [accepts] its entity, else null. */
fun entityTargetOf(node: Any?, accepts: (id: Long, entity: EntityDto) -> Boolean): ComponentTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val entity = (entry.value as? EntityNode)?.entity ?: return null
    val target = componentTargetOf(node)?.takeIf { it.kind == null } ?: return null
    return target.takeIf { accepts(target.entityId, entity) }
}
