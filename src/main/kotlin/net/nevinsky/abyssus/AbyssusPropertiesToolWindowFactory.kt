/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import net.nevinsky.abyssus.properties.PanelServices
import net.nevinsky.abyssus.properties.AssetPropertiesPanel

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.editor.ray.RayMaterialIdentity
import net.nevinsky.abyssus.SceneRayControls

class AbyssusPropertiesToolWindowFactory : ToolWindowFactory, DumbAware {
    private val tables = java.util.concurrent.ConcurrentHashMap<java.io.File, Pair<Long, List<RayMaterialIdentity>>>()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val core = service<AbyssusCore>()
        val assets = core.assets
        val terrain = core.terrain
        val services = PanelServices(
            assets.metaFiles, assets.hdrPreviews, core.documents.json, assets.fields, assets.editor,
            terrain.generator, terrain.heightEncoder, terrain.recipes, project.service<SceneRayControls>(),
            net.nevinsky.abyssus.schema.ComponentSchemas.of(project),
        ) { dir, name ->
            // the panel re-reads an entity on every scene edit; parse each model file once per modification
            assets.loading.modelFile(dir, name)?.let { model ->
                val stamp = model.lastModified()
                tables[model.canonicalFile]?.takeIf { it.first == stamp }?.second
                    ?: assets.loading.rayModelMaterials(dir, name)?.map { RayMaterialIdentity(it.id, it.pbr) }
                        ?.also { tables[model.canonicalFile] = stamp to it }
            }
        }
        val panel = AssetPropertiesPanel(project, toolWindow.disposable, services)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "", false))
    }
}

