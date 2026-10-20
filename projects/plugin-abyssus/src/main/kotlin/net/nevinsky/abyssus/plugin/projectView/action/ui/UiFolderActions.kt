/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView.action.ui

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.projectView.*

class UiDropdownAction : DropdownAction {
    override val actionIds = listOf("Abyssus.NewUiScreen", "Abyssus.NewUiOverlay")

    override fun appliesTo(node: Any?) = uiFolder(node) != null
}

/** The UI folder row of the Abyssus view, with the project file that owns it and the names of its UI assets. */
class UiFolder(val abss: VirtualFile, val assetNames: List<String>)

fun uiFolder(node: Any?): UiFolder? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val row = (entry.value as? FolderRow)?.takeIf { it.kind == FolderKind.UI } ?: return null
    val abss = entry.source?.takeIf { it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION } ?: return null
    return UiFolder(abss, row.assets.map { it.name }.sorted())
}
