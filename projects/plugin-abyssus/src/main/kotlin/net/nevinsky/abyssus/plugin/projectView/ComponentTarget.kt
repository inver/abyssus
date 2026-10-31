/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import net.nevinsky.abyssus.lib.core.editor.document.*
import net.nevinsky.abyssus.lib.core.editor.host.*
import com.intellij.openapi.vfs.VirtualFile

/** An entity of a scene file, or one of its components when [kind] is set (the key under `components`). */
data class ComponentTarget(val file: VirtualFile, val entityId: String, val kind: String?)

/** The entity or component the Abyssus view row [node] stands for, or null for any other node. */
fun componentTargetOf(node: Any?): ComponentTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return when {
        isEntityEntry(entry) -> ComponentTarget(file, entry.name, null)
        isComponentEntry(entry) -> ComponentTarget(file, entry.parentKeys[entry.parentKeys.size - 2], entry.name)
        else -> null
    }
}

/** The entity row [node] stands for (not one of its components) when [accepts] its JSON object, else null. */
fun entityTargetOf(node: Any?, accepts: (name: String, json: Document) -> Boolean): ComponentTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val json = entry.value as? Document ?: return null
    val target = componentTargetOf(node)?.takeIf { it.kind == null } ?: return null
    return target.takeIf { accepts(entry.name, json) }
}
