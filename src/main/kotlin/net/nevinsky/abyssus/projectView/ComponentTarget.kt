/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.vfs.VirtualFile

/** An entity of a scene file, or one of its components when [kind] is set (the key under `components`). */
data class ComponentTarget(val file: VirtualFile, val entityId: String, val kind: String?)

/** The entity or component the Abyssus view row [node] stands for, or null for any other node. */
fun componentTargetOf(node: Any?): ComponentTarget? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val file = entry.source?.takeIf { it.isValid } ?: return null
    return when {
        isEntityEntry(entry) -> ComponentTarget(file, entry.name, null)
        isComponentEntry(entry) -> ComponentTarget(file, entry.parentKeys[2], entry.name)
        else -> null
    }
}
