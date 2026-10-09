/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.assets.displayMessage
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.content.RenderAsset
import net.nevinsky.abyssus.lib.gdx.editor.content.Vec3
import net.nevinsky.abyssus.lib.gdx.editor.components.EditResult
import net.nevinsky.abyssus.lib.gdx.editor.terrain.uniqueAssetUuid
import net.nevinsky.abyssus.lib.gdx.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ImportSettings
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSource
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.SourceFormat
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.StagedModelImport
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.isBlenderFile
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.sourceFormatOf
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileUndoAction
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.PlacedEntity
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.sceneview.SceneFileEditor
import java.io.File

/**
 * Right-click Import Model... on the Assets node of a recognized project: choose an OBJ, FBX, 3DS, DAE, glTF or GLB
 * file, check it in a dialog with a live preview, and Create writes one native MODEL asset (and, with Add to scene, the
 * entity placing it in the selected scene view) as one undoable command. A Blender file is refused with a hint to
 * export glTF. Reading and converting run off the EDT; nothing is written next to the source.
 */
class ImportModelAction : AnAction(), DumbAware {
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
        val title = AbyssusBundle.message("importModelTitle")
        val report = { message: String -> Messages.showErrorDialog(project, message, title) }
        projectRefusal(abss)?.let { report(AbyssusBundle.message("importModelFailed", it)); return }
        val extensions = SourceFormat.entries.map { it.extension }.toTypedArray()
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false).withTitle(title)
            .withExtensionFilter(AbyssusBundle.message("importModelFilter"), *extensions)
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        sourceRefusal(chosen.name)?.let { report(it); return }

        val placement = selectedScenePlacement(project)
        val form = ModelImportForm(File(chosen.path), assetFolderNames(abss), placementOption(project, placement))
        try {
            // read once, off the EDT and cancellable; a failure is shown in the dialog, which then cannot Create
            val opened = try {
                runCatchingKeepingCancellation {
                    ProgressManager.getInstance().runProcessWithProgressSynchronously<ModelSource, Exception>({
                        service<AbyssusCore>().modelSources.open(form.sourceFile) { ProgressManager.checkCanceled() }
                    }, AbyssusBundle.message("importModelReading"), true, project)
                }
            } catch (e: ProcessCanceledException) {
                return
            }
            form.accept(opened)
            if (!ImportModelDialog(project, form).showAndGet()) return
            val source = form.source ?: return
            val settings = form.settings() ?: return
            importModel(project, abss, source, settings, placement.takeIf { form.addToScene }, report)
        } finally {
            form.close()
        }
    }
}

/** Why a chosen file cannot be imported, or null: a Blender file gets the export hint, any other unknown file a refusal. */
fun sourceRefusal(fileName: String): String? = when {
    isBlenderFile(fileName) -> AbyssusBundle.message("importModelBlender")
    sourceFormatOf(fileName) == null -> AbyssusBundle.message("importModelUnsupported", fileName)
    else -> null
}

/** The names under the project's `assets` folder; a new asset folder must differ from each. */
internal fun assetFolderNames(abss: VirtualFile): Set<String> =
    File(abss.parent.path, ASSETS_DIR).list()?.toSet().orEmpty()

/** Where Create may place the model: [sceneFile] of a scene view, the view's orbit target and whether it plays, read when asked. */
class ScenePlacement(val sceneFile: VirtualFile, val point: () -> Vec3?, val playing: () -> Boolean)

/** The scene of the selected scene view (the first Abyssus scene tab among the selected editors), or null. */
fun selectedScenePlacement(project: Project): ScenePlacement? {
    val editor = FileEditorManager.getInstance(project).selectedEditors.filterIsInstance<SceneFileEditor>().firstOrNull() ?: return null
    return ScenePlacement(editor.sceneFile, { editor.sceneView?.placementPoint() }, { editor.sceneView?.playing == true })
}

/** Whether [placement]'s scene can take the model now, by name, or why not. */
fun placementOption(project: Project, placement: ScenePlacement?): PlacementOption = when {
    placement == null -> PlacementOption.Disabled(PlacementBlock.NO_SCENE_VIEW)
    placement.playing() -> PlacementOption.Disabled(PlacementBlock.PLAYING)
    !canAddAsset(placement.sceneFile, SceneDocumentCache.of(project)) -> PlacementOption.Disabled(PlacementBlock.UNREADABLE)
    else -> PlacementOption.Available(placement.sceneFile.name)
}

/** What an import did: how the folder write ended, and the entity it placed, if any. */
class ModelImportOutcome(val result: AssetCommandResult, val entityId: String?)

/**
 * Stages [source] with [settings] (under a modal progress on the EDT) and writes it under [abss]'s project. With
 * [placement], the same command adds the model to that scene at the view's orbit target, as Add Asset does: the
 * folder is written first, then the entity; the undo action is registered only when both succeeded, otherwise the
 * folder is taken back and no undo step is recorded. Afterwards the new entity, or else the new asset, is selected.
 * [report] receives a reason when the project, the scene, the staging or the write fails; nothing is then left behind.
 */
fun importModel(
    project: Project,
    abss: VirtualFile,
    source: ModelSource,
    settings: ImportSettings,
    placement: ScenePlacement?,
    report: (String) -> Unit,
): ModelImportOutcome? {
    val core = service<AbyssusCore>()
    val fail = { reason: String -> report(AbyssusBundle.message("importModelFailed", reason)) }
    projectRefusal(abss)?.let { fail(it); return null }
    val cache = SceneDocumentCache.of(project)
    if (placement != null) {
        placementOption(project, placement).let { if (it is PlacementOption.Disabled) { fail(it.reason.message()); return null } }
    }
    val projectDir = File(abss.parent.path)
    val stage = {
        core.modelImport.stage(source, settings, projectDir, uniqueAssetUuid(core.json, File(projectDir, ASSETS_DIR)),
            System.currentTimeMillis()) { ProgressManager.checkCanceled() }
    }
    val staged = runCatchingKeepingCancellation {
        if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isUnitTestMode) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<StagedModelImport, Exception>(
                stage, AbyssusBundle.message("importModelConverting"), true, project)
        } else stage()
    }.getOrElse { fail(it.displayMessage()); return null }

    val command = AssetFileCommand(project, LocalAssetFileStore(projectDir))
    var placed: PlacedEntity? = null
    val txn = assetFolderTransaction(projectDir, AbyssusBundle.message("commandImportModel"), staged.folder,
        staged.uuid.toString(), staged.files) { placed }
    var result: AssetCommandResult = AssetCommandResult.Cancelled
    var rejection: String? = null
    CommandProcessor.getInstance().executeCommand(project, {
        var undo: AssetFileUndoAction? = null
        result = command.execute(txn, handBack = { undo = it })
        if (result != AssetCommandResult.Done) return@executeCommand
        if (placement != null) {
            val point = placement.point() ?: Vec3(0f, 0f, 0f)
            val added = SceneComponentEdits.addAsset(project, placement.sceneFile, RenderAsset(MetaType.MODEL.name, staged.folder),
                point, cache, core.assets.metaFiles)
            val id = added.entityId
            if (added.result != EditResult.Changed || id == null) {
                rejection = (added.result as? EditResult.Rejected)?.reason ?: AbyssusBundle.message("importModelPlacement.UNREADABLE")
                val reverted = command.revert(txn)
                if (reverted != AssetCommandResult.Done) rejection += " ($reverted)"
                return@executeCommand
            }
            placed = PlacedEntity(File(placement.sceneFile.path), id)
        }
        UndoManager.getInstance(project).undoableActionPerformed(undo!!)
    }, AbyssusBundle.message("commandImportModel"), null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION)
    // the VFS learns of the files after the command, so the platform records nothing for them itself
    command.flush()

    rejection?.let { fail(it); return ModelImportOutcome(AssetCommandResult.Cancelled, null) }
    val problem = when (val r = result) {
        AssetCommandResult.Done -> null
        is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
        is AssetCommandResult.Conflict -> r.path
        is AssetCommandResult.Blocked -> r.reason
        AssetCommandResult.Cancelled -> AbyssusBundle.message("importModelCancelled")
        is AssetCommandResult.Failed -> r.cause.displayMessage()
    }
    if (problem != null) {
        fail(problem)
        return ModelImportOutcome(result, null)
    }
    val entity = placed
    ApplicationManager.getApplication().invokeLater({
        if (entity != null) selectCreatedEntity(project, placement!!.sceneFile, entity.entityId)
        else selectAssetInAbyssusView(project, abss, staged.folder)
    }, ModalityState.nonModal())
    return ModelImportOutcome(result, entity?.entityId)
}
