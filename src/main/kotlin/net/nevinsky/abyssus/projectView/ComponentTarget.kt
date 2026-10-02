/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
