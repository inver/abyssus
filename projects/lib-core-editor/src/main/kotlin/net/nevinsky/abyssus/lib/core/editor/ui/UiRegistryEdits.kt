/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.dto.ProjectDto

/**
 * Edits of the project's `uiAssetNames` registry. Each returns the edited project, or null when nothing changes, so they
 * fit [net.nevinsky.abyssus.lib.core.editor.document.ProjectTextEditor]; an emptied list is not written at all.
 */
class UiRegistryEdits {
    fun register(project: ProjectDto, name: String): ProjectDto? {
        if (name.isBlank() || name in project.uiAssetNames) return null
        return project.copy(uiAssetNames = project.uiAssetNames + name)
    }

    fun unregister(project: ProjectDto, name: String): ProjectDto? {
        if (name !in project.uiAssetNames) return null
        return project.copy(uiAssetNames = project.uiAssetNames - name)
    }
}
