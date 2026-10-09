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
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileCommand
import net.nevinsky.abyssus.plugin.assetfiles.LocalAssetFileStore
import net.nevinsky.abyssus.lib.gdx.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.lib.gdx.assets.displayMessage
import net.nevinsky.abyssus.lib.gdx.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.flightgear.FlightGearArchive
import net.nevinsky.abyssus.lib.gdx.editor.flightgear.FlightGearImportRequest
import net.nevinsky.abyssus.lib.gdx.editor.flightgear.FlightGearInspection
import net.nevinsky.abyssus.plugin.terrain.message
import net.nevinsky.abyssus.lib.gdx.editor.terrain.uniqueAssetUuid
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Right-click Import FlightGear Aircraft... on the Assets node of a recognized project: choose a `.zip`, then an
 * aircraft, folder name, size and parts; Create writes one native MODEL asset as an undoable command and selects it.
 * Scenes and the project file are not touched. Reading and converting run off the EDT under a modal progress.
 */
class ImportFlightGearAction : AnAction(), DumbAware {
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
        val title = AbyssusBundle.message("importFlightGearTitle")
        val report = { message: String -> Messages.showErrorDialog(project, message, title) }
        projectRefusal(abss)?.let { report(AbyssusBundle.message("importFlightGearFailed", it)); return }
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("zip").withTitle(title)
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        val archiveFile = File(chosen.path)
        val inspections = runCatchingKeepingCancellation {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<List<FlightGearInspection>, Exception>({
                inspectArchive(archiveFile) { ProgressManager.checkCanceled() }
            }, AbyssusBundle.message("importFlightGearReading"), true, project)
        }
        val list = inspections.getOrElse { report(AbyssusBundle.message("importFlightGearFailed", it.displayMessage())); return }
        val dialog = ImportFlightGearDialog(project, File(abss.parent.path, ASSETS_DIR), list)
        if (!dialog.showAndGet()) return
        val request = dialog.request() ?: return
        importFlightGear(project, abss, archiveFile, request, report)
    }
}

/** Every aircraft of [archiveFile] with what it would import; throws when the archive is unreadable. */
fun inspectArchive(archiveFile: File, cancel: () -> Unit = {}): List<FlightGearInspection> {
    val importer = service<AbyssusCore>().flightGearImport
    return FlightGearArchive(archiveFile).use { archive ->
        archive.aircraft().filter { it.modelPath != null }.map { importer.inspect(archive, it, cancel) }
    }
}

/**
 * Stages [request] from [archiveFile] under a modal progress and writes it under [abss]'s project as one undoable
 * command, then selects the new asset. [report] receives a reason when the project, the archive or the write fails.
 */
fun importFlightGear(
    project: Project,
    abss: VirtualFile,
    archiveFile: File,
    request: FlightGearImportRequest,
    report: (String) -> Unit,
): AssetCommandResult? {
    val core = service<AbyssusCore>()
    projectRefusal(abss)?.let { report(AbyssusBundle.message("importFlightGearFailed", it)); return null }
    val projectDir = File(abss.parent.path)
    val stage = {
        FlightGearArchive(archiveFile).use { archive ->
            core.flightGearImport.stage(archive, request, uniqueAssetUuid(core.json, File(projectDir, ASSETS_DIR)),
                System.currentTimeMillis()) { ProgressManager.checkCanceled() }
        }
    }
    val staged = runCatchingKeepingCancellation {
        if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isUnitTestMode) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<net.nevinsky.abyssus.lib.gdx.editor.flightgear.StagedImport, Exception>(
                stage, AbyssusBundle.message("importFlightGearConverting"), true, project)
        } else stage()
    }.getOrElse { report(AbyssusBundle.message("importFlightGearFailed", it.displayMessage())); return null }
    val result = AssetFileCommand(project, LocalAssetFileStore(projectDir)).execute(
        importTransaction(projectDir, staged),
        onDone = { ApplicationManager.getApplication().invokeLater({ selectAssetInAbyssusView(project, abss, staged.folder) }, ModalityState.nonModal()) },
    )
    val problem = when (result) {
        AssetCommandResult.Done -> null
        is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
        is AssetCommandResult.Conflict -> result.path
        is AssetCommandResult.Blocked -> result.reason
        AssetCommandResult.Cancelled -> AbyssusBundle.message("importFlightGearCancelled")
        is AssetCommandResult.Failed -> result.cause.displayMessage()
    }
    if (problem != null) report(AbyssusBundle.message("importFlightGearFailed", problem))
    return result
}

/** The dialog over [FlightGearImportSettings]: aircraft, licence, folder name, size, parts and what is skipped. */
internal class ImportFlightGearDialog(project: Project, private val assetsDir: File, private val inspections: List<FlightGearInspection>) : DialogWrapper(project) {
    private var settings: FlightGearImportSettings? = inspections.firstOrNull()?.let { FlightGearImportSettings(assetsDir, it) }
    private val aircraft = ComboBox(inspections.map { it.aircraft.description ?: it.aircraft.id }.toTypedArray())
    private val details = JBLabel()
    private val license = JBLabel()
    private val name = JBTextField()
    private val nameError = JBLabel().apply { foreground = JBColor.RED }
    private val original = JBRadioButton(AbyssusBundle.message("importFlightGearOriginal"))
    private val scaled = JBRadioButton(AbyssusBundle.message("importFlightGearSpan"))
    private val span = JBTextField(6)
    private val parts = CheckBoxList<String>()
    private val skipped = JBLabel()

    init {
        title = AbyssusBundle.message("importFlightGearTitle")
        setOKButtonText(AbyssusBundle.message("importFlightGearCreate"))
        ButtonGroup().apply { add(original); add(scaled) }
        aircraft.isEnabled = inspections.size > 1
        aircraft.addActionListener { settings = inspections.getOrNull(aircraft.selectedIndex)?.let { FlightGearImportSettings(assetsDir, it) }; showAircraft() }
        name.document.addDocumentListener(changes { settings?.folderName = name.text; validateNow() })
        span.document.addDocumentListener(changes { settings?.spanText = span.text; validateNow() })
        original.addActionListener { settings?.originalSize = true; validateNow() }
        scaled.addActionListener { settings?.originalSize = false; validateNow() }
        parts.setCheckBoxListListener { index, value -> settings?.tick(parts.getItemAt(index) ?: return@setCheckBoxListListener, value); validateNow() }
        init()
        showAircraft()
    }

    private fun changes(block: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = block()
        override fun removeUpdate(e: DocumentEvent) = block()
        override fun changedUpdate(e: DocumentEvent) = block()
    }

    /** Shows the selected aircraft's settings. */
    private fun showAircraft() {
        val s = settings
        if (s == null) {
            details.text = AbyssusBundle.message("importFlightGearNoAircraft")
            validateNow()
            return
        }
        val a = s.inspection.aircraft
        details.text = "<html><b>${a.description ?: a.id}</b><br>${a.authors ?: ""}</html>"
        license.text = if (s.licenseUnknown) AbyssusBundle.message("importFlightGearLicenseUnknown")
        else AbyssusBundle.message("importFlightGearLicense", s.inspection.license!!)
        license.foreground = if (s.licenseUnknown) JBColor.ORANGE else JBColor.foreground()
        name.text = s.folderName
        span.text = s.spanText
        original.isSelected = s.originalSize
        scaled.isSelected = !s.originalSize
        parts.clear()
        s.parts.forEach { parts.addItem(it, it, s.isTicked(it)) }
        skipped.text = if (s.inspection.skipped.isEmpty()) "" else s.inspection.skipped.joinToString(
            "<br>", "<html>${AbyssusBundle.message("importFlightGearSkipped")}<br>", "</html>") { "${it.item}: ${it.reason}" }
        validateNow()
    }

    private fun validateNow() {
        val s = settings
        nameError.text = s?.nameError()?.message() ?: s?.sizeError()?.let { AbyssusBundle.message("importFlightGearSpanError") } ?: ""
        span.isEnabled = s?.originalSize == false
        isOKActionEnabled = s?.request() != null
    }

    fun request(): FlightGearImportRequest? = settings?.request()

    override fun createCenterPanel(): JComponent {
        val size = JPanel(BorderLayout()).apply {
            add(original, BorderLayout.WEST)
            add(JPanel(BorderLayout()).apply { add(scaled, BorderLayout.WEST); add(span, BorderLayout.CENTER) }, BorderLayout.EAST)
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(AbyssusBundle.message("importFlightGearAircraft"), aircraft)
            .addComponent(details)
            .addComponent(license)
            .addLabeledComponent(AbyssusBundle.message("newTerrainName"), name)
            .addComponent(nameError)
            .addLabeledComponent(AbyssusBundle.message("importFlightGearSize"), size)
            .addLabeledComponentFillVertically(
                AbyssusBundle.message("importFlightGearParts"),
                JBScrollPane(parts).apply { preferredSize = Dimension(JBUI.scale(360), JBUI.scale(220)) })
            .addComponent(skipped)
            .panel
    }
}
