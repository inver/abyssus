/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.filetype.TreeActionIcons

/** The "Show Only Unused Assets" choice of a project's Abyssus view; off by default and remembered per project. */
object UnusedFilter {
    private const val KEY = "net.nevinsky.abyssus.showOnlyUnusedAssets"

    fun isOn(project: Project): Boolean = PropertiesComponent.getInstance(project).getBoolean(KEY, false)

    fun set(project: Project, on: Boolean) = PropertiesComponent.getInstance(project).setValue(KEY, on, false)

    /**
     * [rows] of a project with the assets of its `UI` and `Assets` folders cut down to the unused ones while the filter is on.
     * It runs on the folders, before they group by type, so a group without an unused asset is not built and counts follow what is listed.
     */
    fun apply(project: Project, rows: List<DtoRow>): List<DtoRow> {
        if (!isOn(project)) return rows
        return rows.map { row ->
            val folder = row.value as? FolderRow
            if (folder != null) row.copy(value = folder.copy(assets = folder.assets.filter { it.unused })) else row
        }
    }
}

class UnusedFilterAction : ToggleAction(
    AbyssusBundle.message("unusedFilterText"),
    AbyssusBundle.message("unusedFilterDescription"),
    TreeActionIcons.UNUSED_FILTER,
), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    /** The pressed state is amber: the selected glyph replaces the plain one while the filter is on. */
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.icon = if (isSelected(e)) TreeActionIcons.UNUSED_FILTER_SELECTED else TreeActionIcons.UNUSED_FILTER
    }

    override fun isSelected(e: AnActionEvent): Boolean = e.project?.let(UnusedFilter::isOn) ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        UnusedFilter.set(project, state)
        ProjectView.getInstance(project).refresh()
    }
}
