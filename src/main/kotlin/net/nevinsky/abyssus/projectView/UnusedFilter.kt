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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.files.Asset

/** The "Show Only Unused Assets" choice of a project's Abyssus view; off by default and remembered per project. */
object UnusedFilter {
    private const val KEY = "net.nevinsky.abyssus.showOnlyUnusedAssets"

    fun isOn(project: Project): Boolean = PropertiesComponent.getInstance(project).getBoolean(KEY, false)

    fun set(project: Project, on: Boolean) = PropertiesComponent.getInstance(project).setValue(KEY, on, false)

    /** [rows] of a project with its `assets` list cut down to the unused assets while the filter is on. */
    fun apply(project: Project, rows: List<DtoRow>): List<DtoRow> {
        if (!isOn(project)) return rows
        return rows.map { row ->
            val assets = row.value as? List<*>
            if (row.name == "assets" && assets != null) row.copy(value = assets.filter { (it as? Asset<*>)?.unused == true }) else row
        }
    }
}

class UnusedFilterAction : ToggleAction(
    AbyssusBundle.message("unusedFilterText"),
    AbyssusBundle.message("unusedFilterDescription"),
    AllIcons.Actions.ToggleVisibility,
), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = e.project?.let(UnusedFilter::isOn) ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        UnusedFilter.set(project, state)
        ProjectView.getInstance(project).refresh()
    }
}
