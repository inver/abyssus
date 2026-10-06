/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.terrain.NewTerrainForm
import net.nevinsky.abyssus.terrain.NewTerrainRequest
import java.io.File
import javax.swing.JComponent
import net.nevinsky.abyssus.ui.documentDisplayMessage as displayMessage

/**
 * Right-click New Terrain on the Assets node of a recognized project: a dialog for the folder name, world size,
 * resolution and generation settings with a heightmap preview; Create writes the asset and selects it. The scene
 * files and the project file are not touched, so the terrain is unused until a scene references it.
 */
class NewTerrainAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    private fun owningProject(e: AnActionEvent): VirtualFile? {
        val project = e.project ?: return null
        val pane = ProjectView.getInstance(project).currentProjectViewPane?.takeIf { it.id == AbyssusProjectViewPane.ID } ?: return null
        return assetsNodeProjectFile(TreeUtil.getUserObject(pane.selectedPath?.lastPathComponent))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = owningProject(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val abss = owningProject(e) ?: return
        createInteractively(project, abss)
    }
}

/** The dialog around a [NewTerrainForm]; OK is enabled only while the form has a request to create from. */
internal class NewTerrainDialog(project: Project, val form: NewTerrainForm) : DialogWrapper(project) {
    init {
        title = AbyssusBundle.message("newTerrainTitle")
        setOKButtonText(AbyssusBundle.message("newTerrainCreate"))
        init()
        form.onValidityChanged = { isOKActionEnabled = form.request() != null }
        isOKActionEnabled = false
    }

    override fun createCenterPanel(): JComponent = form

    override fun dispose() {
        form.dispose()
        super.dispose()
    }
}

private fun createInteractively(project: Project, abss: VirtualFile) {
    val core = service<AbyssusCore>()
    val terrain = core.terrain
    val projectDir = File(abss.parent.path)
    val form = NewTerrainForm(
        projectDir, terrain.generator,
        background = { AppExecutorUtil.getAppExecutorService().execute(it) },
        ui = { ApplicationManager.getApplication().invokeLater(it, ModalityState.any()) },
    )
    val dialog = NewTerrainDialog(project, form)
    if (!dialog.showAndGet()) return
    val request = form.request() ?: return
    createTerrain(project, abss, request)
}

/** Writes the terrain of [request] under [abss]'s project as one undoable command, then selects it in the view. */
fun createTerrain(project: Project, abss: VirtualFile, request: NewTerrainRequest, report: (String) -> Unit = { Messages.showErrorDialog(project, it, AbyssusBundle.message("newTerrainTitle")) }): AssetCommandResult? {
    val core = service<AbyssusCore>()
    val terrain = core.terrain
    val accepted = net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation {
        core.documents.format.requireSupported(core.documents.json.readObject(net.nevinsky.abyssus.dto.textOf(abss)), net.nevinsky.abyssus.editor.document.DocumentKind.PROJECT)
    }
    accepted.exceptionOrNull()?.let { report(it.displayMessage()); return null }
    val projectDir = File(abss.parent.path)
    val staged = terrain.newTerrains.stage(projectDir, request.name, request.preview)
    if (staged == null) {
        report(AbyssusBundle.message("newTerrainFailed", AbyssusBundle.message("newTerrainNameError.EXISTS")))
        return null
    }
    val result = AssetFileCommand(project, LocalAssetFileStore(projectDir)).execute(
        staged.transaction,
        onDone = { ApplicationManager.getApplication().invokeLater({ selectAssetInAbyssusView(project, abss, staged.name) }, ModalityState.nonModal()) },
    )
    val problem = when (result) {
        AssetCommandResult.Done -> null
        is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
        is AssetCommandResult.Conflict -> result.path
        is AssetCommandResult.Blocked -> result.reason
        AssetCommandResult.Cancelled -> AbyssusBundle.message("terrainApplyCancelled")
        is AssetCommandResult.Failed -> result.cause.displayMessage()
    }
    if (problem != null) report(AbyssusBundle.message("newTerrainFailed", problem))
    return result
}
