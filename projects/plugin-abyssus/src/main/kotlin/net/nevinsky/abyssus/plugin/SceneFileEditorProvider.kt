/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import java.io.File
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.plugin.dto.SceneReader
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ide.plugins.PluginManager
import net.nevinsky.abyssus.plugin.projectView.AbyssusProjectViewPane
import net.nevinsky.abyssus.plugin.projectView.ProjectSceneViewHost
import net.nevinsky.abyssus.plugin.sceneview.NamedOverlay
import net.nevinsky.abyssus.plugin.sceneview.PlayState
import net.nevinsky.abyssus.plugin.sceneview.RayIntegration
import net.nevinsky.abyssus.plugin.sceneview.SceneFileEditor
import net.nevinsky.abyssus.plugin.sceneview.SceneOverlayHost
import net.nevinsky.abyssus.plugin.sceneview.SceneOverlayProvider
import net.nevinsky.abyssus.plugin.sceneview.SceneParamsSource
import net.nevinsky.abyssus.plugin.sceneview.SceneRenderer
import net.nevinsky.abyssus.plugin.sceneview.SceneSimulationProvider
import net.nevinsky.abyssus.plugin.sceneview.SceneViewHost
import net.nevinsky.abyssus.plugin.sceneview.SceneViewPanel
import net.nevinsky.abyssus.plugin.sceneview.SimulationRequest
import net.nevinsky.abyssus.plugin.sceneview.ViewAssets

import net.nevinsky.abyssus.plugin.sceneview.*

class SceneFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = ProjectLayout.isScene(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val core = service<AbyssusCore>()
        val assets = core.assets
        val reader = service<SceneReader>()
        val host = (com.intellij.ide.projectView.ProjectView.getInstance(project)
            .getProjectViewPaneById(AbyssusProjectViewPane.ID) as? SceneViewHost) ?: ProjectSceneViewHost(project)
        // Ray tracing is optional: a missing service (e.g. a test without the application services) leaves raster only.
        val ray = runCatchingKeepingCancellation { RayIntegration.of(core.ray) }.getOrNull()
        return SceneFileEditor(
            project,
            file,
            core.documents.json,
            project.service<SceneRayControls>(),
            SceneParamsSource.editorText(reader),
            host,
        ) { params ->
            SceneViewPanel(
                params, SceneRenderer(ViewAssets(assets.loading), assets.sceneShaders),
                lightActions = { position -> host.lightActions(file, position) },
                canAddLight = { host.canAddLight(file) },
                assetActions = { position -> host.assetActions(file, position) },
                canAddAsset = { host.canAddAsset(file) },
                ray = ray,
                play = playState(),
                simulationRequest = { selection -> simulationRequest(project, file, selection) },
                overlays = overlays(project, file),
            )
        }
    }

    /** Play for one view, from the first installed simulation provider (none: no play controls). */
    private fun playState(): PlayState {
        val provider = SceneSimulationProvider.EP_NAME.extensionList.firstOrNull()
        return PlayState(
            provider, provider?.let(::pluginName).orEmpty(),
            ui = { ApplicationManager.getApplication().invokeLater(it, ModalityState.any()) },
            logError = { message, error -> thisLogger().error(message, error) },
        )
    }

    /** One overlay per installed provider; a provider that throws while creating it is left out with one error. */
    private fun overlays(project: Project, file: VirtualFile): SceneOverlayHost {
        val created = SceneOverlayProvider.EP_NAME.extensionList.mapNotNull { provider ->
            runCatchingKeepingCancellation { NamedOverlay(pluginName(provider), provider.create(project, file)) }
                .onFailure { thisLogger().error("Scene overlay of ${pluginName(provider)} could not be created", it) }
                .getOrNull()
        }
        return SceneOverlayHost(created) { message, error -> thisLogger().error(message, error) }
    }

    private fun pluginName(extension: Any): String =
        PluginManager.getPluginByClass(extension.javaClass)?.name ?: extension.javaClass.name

    /** The scene as the editor holds it, unsaved text included. */
    private fun simulationRequest(project: Project, file: VirtualFile, selection: String?): SimulationRequest {
        val text = FileDocumentManager.getInstance().getDocument(file)?.text ?: VfsUtilCore.loadText(file)
        val projectDir = ProjectLayout.projectDirFor(file) ?: File(file.parent.parent.path)
        return SimulationRequest(project, file, text, projectDir, selection)
    }

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "abyssus-scene-view"
    }
}

