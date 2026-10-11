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
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.foliage.FOLIAGE_DATA_FILE
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MASK_RESOLUTION_MAX
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MASK_RESOLUTION_MIN
import net.nevinsky.abyssus.lib.core.editor.foliage.FoliageCreateError
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.AssetReferenceGuard
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransaction
import net.nevinsky.abyssus.plugin.assetfiles.FileChange
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** The folder name New Foliage... starts with, plus the chosen terrain's folder name. */
internal fun foliageFolderName(terrain: String) = FOLIAGE_NAME_PREFIX + terrain

private const val FOLIAGE_NAME_PREFIX = "foliage_"

/**
 * The terrain assets of [projectDir] whose heights are readable: New Foliage... is offered only while there is one,
 * because the new bake's fingerprint is taken over those heights. Folder names only, no generation, so `update` can
 * call this on the EDT. Reads the assets folder, not the VFS, so an unsaved `meta.json` is not seen here.
 */
internal fun foliageTerrains(projectDir: File, json: JsonProcessor): List<String> {
    val files = FileLoader(projectDir)
    val metas = AssetMetaLoader(json, files)
    return File(projectDir, ASSETS_DIR).listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { folder ->
        runCatchingKeepingCancellation {
            val meta = metas.loadBaseMeta(folder.name) ?: return@runCatchingKeepingCancellation null
            if (meta.type != MetaType.TERRAIN) return@runCatchingKeepingCancellation null
            val heights = meta.typedAdditional<TerrainMeta>().terrainFile
            folder.name.takeIf { heights != null && files.findAssetFile(folder.name, heights)?.isFile == true }
        }.getOrNull()
    }.sorted()
}

/** The heights of the terrain [name], or null when the project cannot read them; needed before a bake is planned. */
internal fun foliageTerrainData(projectDir: File, json: JsonProcessor, name: String): TerrainData? =
    runCatchingKeepingCancellation {
        val files = FileLoader(projectDir)
        TerrainLoader(files, AssetMetaLoader(json, files)).prepare(name)?.staged?.data
    }.getOrNull()

/** The localized reason [error] gives for refusing a new foliage asset, as the dialog and Create both show it. */
internal fun FoliageCreateError.message(): String = when (this) {
    is FoliageCreateError.FolderName -> AbyssusBundle.message("newTerrainNameError.${reason.name}")
    FoliageCreateError.MaskResolution -> AbyssusBundle.message(
        "newFoliageResolutionRange", FOLIAGE_MASK_RESOLUTION_MIN, FOLIAGE_MASK_RESOLUTION_MAX,
    )
}

/**
 * Right-click New Foliage on the Assets node of a recognized project with at least one readable terrain: a dialog for
 * the terrain, the folder name and the mask resolution; Create writes the asset with no layers and selects it. No
 * scene, terrain or project file is touched, so the foliage does nothing until a terrain entity names it.
 */
open class NewFoliageAction : AnAction(), DumbAware {
    init {
        templatePresentation.text = AbyssusBundle.message("newFoliageAction")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    /** The recognized project's Assets node under the selection, or null when the selection is not one. Open for tests. */
    internal open fun owningProject(e: AnActionEvent): VirtualFile? {
        val project = e.project ?: return null
        val pane = ProjectView.getInstance(project).currentProjectViewPane?.takeIf { it.id == AbyssusProjectViewPane.ID } ?: return null
        return assetsNodeProjectFile(TreeUtil.getUserObject(pane.selectedPath?.lastPathComponent))
    }

    private fun terrainsOf(abss: VirtualFile) = foliageTerrains(File(abss.parent.path), service<AbyssusCore>().json)

    override fun update(e: AnActionEvent) {
        val abss = owningProject(e)
        e.presentation.isEnabledAndVisible = abss != null && terrainsOf(abss).isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val abss = owningProject(e) ?: return
        val terrains = terrainsOf(abss)
        if (terrains.isEmpty()) return
        val dialog = NewFoliageDialog(project, File(abss.parent.path), terrains)
        if (!dialog.showAndGet()) return
        createFoliage(project, abss, dialog.terrain, dialog.name, dialog.maskResolution)
    }
}

/** The dialog around the three choices of New Foliage...; Create is enabled only while all of them hold. */
internal class NewFoliageDialog(
    project: Project,
    private val projectDir: File,
    private val terrains: List<String>,
) : DialogWrapper(project) {
    private val writer = service<AbyssusCore>().newFoliages
    val terrainCombo = ComboBox(terrains.toTypedArray())
    val nameField = JBTextField(foliageFolderName(terrains.first()))
    val resolutionField = JBTextField(FOLIAGE_MASK_RESOLUTION_DIALOG_DEFAULT.toString(), 8)
    val reason = JBLabel()

    private var suggested = foliageFolderName(terrains.first())

    val terrain: String get() = (terrainCombo.selectedItem as? String) ?: terrains.first()
    val name: String get() = nameField.text
    val maskResolution: Int get() = resolutionField.text.trim().toIntOrNull() ?: Int.MIN_VALUE

    init {
        title = AbyssusBundle.message("newFoliageTitle")
        setOKButtonText(AbyssusBundle.message("newFoliageCreate"))
        init()
        nameField.document.addDocumentListener(changes(::revalidate))
        resolutionField.document.addDocumentListener(changes(::revalidate))
        terrainCombo.addActionListener {
            val fresh = foliageFolderName(terrain)
            // the name is still the one this dialog suggested: follow the terrain it was suggested for
            if (nameField.text == suggested) nameField.text = fresh
            suggested = fresh
            revalidate()
        }
        revalidate()
    }

    private fun changes(block: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = block()
        override fun removeUpdate(e: DocumentEvent) = block()
        override fun changedUpdate(e: DocumentEvent) = block()
    }

    private fun revalidate() {
        val error = writer.check(projectDir, nameField.text, maskResolution)
        reason.text = error?.message()
        isOKActionEnabled = error == null
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(AbyssusBundle.message("newFoliageTerrain"), terrainCombo)
        .addLabeledComponent(AbyssusBundle.message("newFoliageName"), nameField)
        .addLabeledComponent(AbyssusBundle.message("newFoliageResolution"), resolutionField)
        .addComponent(reason)
        .panel

    override fun getPreferredFocusedComponent(): JComponent = nameField
}

/**
 * Writes the foliage of [terrainFolder] named [rawName] under [abss]'s project as one undoable command, then selects
 * it in the view. The project document is re-read first, the name and the resolution are re-checked against the
 * assets folder, and only a terrain the project can read is planned against; every refusal goes to [report] and
 * writes nothing.
 */
fun createFoliage(
    project: Project,
    abss: VirtualFile,
    terrainFolder: String,
    rawName: String,
    maskResolution: Int,
    report: (String) -> Unit = { Messages.showErrorDialog(project, it, AbyssusBundle.message("newFoliageTitle")) },
    select: (String) -> Unit = { created ->
        ApplicationManager.getApplication().invokeLater(
            { if (!project.isDisposed) selectAssetInAbyssusView(project, abss, created) }, ModalityState.nonModal(),
        )
    },
): AssetCommandResult? {
    val core = service<AbyssusCore>()
    val projectDir = File(abss.parent.path)
    val accepted = runCatchingKeepingCancellation {
        core.documents.format.requireSupported(core.documents.json.readObject(textOf(abss)), DocumentKind.PROJECT)
    }
    accepted.exceptionOrNull()?.let { report(it.documentDisplayMessage()); return null }

    val name = rawName.trim()
    core.newFoliages.check(projectDir, name, maskResolution)?.let {
        report(AbyssusBundle.message("newFoliageFailed", it.message()))
        return null
    }
    val terrain = foliageTerrainData(projectDir, core.json, terrainFolder) ?: run {
        report(AbyssusBundle.message("newFoliageFailed", AbyssusBundle.message("newFoliageUnreadable", terrainFolder)))
        return null
    }
    // re-checked inside plan: the folder may have appeared since the dialog was opened
    val staged = core.newFoliages.plan(projectDir, terrainFolder, name, maskResolution, terrain) ?: run {
        report(AbyssusBundle.message("newFoliageFailed", AbyssusBundle.message("newTerrainNameError.EXISTS")))
        return null
    }

    val base = "$ASSETS_DIR/${staged.folder}"
    val transaction = AssetTransaction(
        AbyssusBundle.message("commandNewFoliage"),
        changes = listOf(
            FileChange("$base/$META_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(staged.metaText.toByteArray())),
            FileChange("$base/$FOLIAGE_DATA_FILE", FileSnapshot.Absent, FileSnapshot.Bytes(staged.dataBytes)),
        ),
        createdDirs = (if (File(projectDir, ASSETS_DIR).isDirectory) emptyList() else listOf(ASSETS_DIR)) + base,
        guard = AssetReferenceGuard(projectDir).let { guard -> { guard.blocker(staged.folder, staged.uuid) } },
    )
    val result = AssetFileCommand(project, LocalAssetFileStore(projectDir)).execute(
        transaction,
        onDone = { select(staged.folder) },
    )
    val problem = when (result) {
        AssetCommandResult.Done -> null
        is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
        is AssetCommandResult.Conflict -> result.path
        is AssetCommandResult.Blocked -> result.reason
        AssetCommandResult.Cancelled -> AbyssusBundle.message("newFoliageCancelled")
        is AssetCommandResult.Failed -> result.cause.documentDisplayMessage()
    }
    problem?.let { report(AbyssusBundle.message("newFoliageFailed", it)) }
    return result
}
