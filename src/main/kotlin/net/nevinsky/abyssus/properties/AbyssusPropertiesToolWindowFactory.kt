/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.properties

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.sceneview.SceneRayControls

class AbyssusPropertiesToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val core = service<AbyssusCore>()
        val services = PanelServices(
            core.metaFiles, core.hdrPreviews, core.json, core.assetFields, core.assetEditor,
            core.terrainGenerator, core.heightEncoder, core.terrainRecipes, project.service<SceneRayControls>(),
            net.nevinsky.abyssus.schema.ComponentSchemas.of(project),
        )
        val panel = AssetPropertiesPanel(project, toolWindow.disposable, services)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "", false))
    }
}
