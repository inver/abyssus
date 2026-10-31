/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import net.nevinsky.abyssus.plugin.properties.PanelServices
import net.nevinsky.abyssus.plugin.properties.AssetPropertiesPanel

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import net.nevinsky.abyssus.plugin.schema.ComponentSchemas

class AbyssusPropertiesToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val core = service<AbyssusPluginCtx>()
        val assets = core.assets
        val terrain = core.terrain
        val services = PanelServices(
            assets.metaFiles, assets.hdrPreviews, core.documents.json, assets.fields, assets.editor,
            terrain.generator, terrain.heightEncoder, terrain.recipes, terrain.sculpt, project.service<SceneRayControls>(),
            ComponentSchemas.of(project),
            meshes = { dir, name -> assets.loading.modelMeshes(dir, name)?.map { it.id } },
        )
        val panel = AssetPropertiesPanel(project, toolWindow.disposable, services)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "", false))
    }
}

