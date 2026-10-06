/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import java.io.File
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.filetype.editSceneJson
import net.nevinsky.abyssus.projectView.AddAssetGroup
import net.nevinsky.abyssus.projectView.AddLightGroup
import net.nevinsky.abyssus.projectView.canAddAsset
import net.nevinsky.abyssus.projectView.hasRenderAssets
import net.nevinsky.abyssus.projectView.canAddLight
import net.nevinsky.abyssus.projectView.AbyssusSelectionListener
import net.nevinsky.abyssus.projectView.componentTargetOf
import net.nevinsky.abyssus.projectView.selectEntityInAbyssusView
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.ui.documentDisplayMessage as displayMessage
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.dto.SceneReader
import net.nevinsky.abyssus.dto.SceneDocumentCache
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ide.plugins.PluginManager
import net.nevinsky.abyssus.filetype.AbyssusSceneEdited

import net.nevinsky.abyssus.sceneview.*
import net.nevinsky.abyssus.projectView.AbyssusProjectViewPane
import net.nevinsky.abyssus.projectView.ProjectSceneViewHost

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
            project, file, core.documents.json, project.service<SceneRayControls>(), SceneParamsSource.editorText(reader), host,
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

