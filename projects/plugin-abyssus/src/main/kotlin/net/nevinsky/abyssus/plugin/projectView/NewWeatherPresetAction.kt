/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.AssetMetaReader
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.terrain.checkFolderName
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.assetfiles.*
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.awt.BorderLayout
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class WeatherPresetTarget(val abss: VirtualFile, val sky: VirtualFile)

/** Resolves only an asset row, never one of its child property rows; current text wins over the cached DTO. */
internal fun weatherPresetTarget(node: Any?, reader: AssetMetaReader): WeatherPresetTarget? = runCatchingKeepingCancellation {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val asset = entry.value as? Asset<*> ?: return null
    val abss = entry.source?.takeIf { it.extension == "abss" } ?: return null
    val sky = LocalFileSystem.getInstance().findFileByIoFile(File(asset.baseDir, "meta.json")) ?: return null
    if (sky.parent.parent != abss.parent.findChild("assets")) return null
    val meta = reader.read(SceneJson().parseObject(textOf(sky)))
    val reference = meta.json["additional"]?.get("clouds")
    if (meta.type != MetaType.SKYBOX_PROCEDURAL || reference?.isTextual != true || reference.textValue().isBlank()) return null
    WeatherPresetTarget(abss, sky)
}.getOrNull()

open class NewWeatherPresetAction : AbyssusTreeAction<WeatherPresetTarget>() {
    init { templatePresentation.text = AbyssusBundle.message("newWeatherPresetAction") }
    override fun targetOf(node: Any?) = weatherPresetTarget(node, AssetMetaReader(service<AbyssusCore>().documents.json))

    override fun perform(project: Project, target: WeatherPresetTarget, e: AnActionEvent) {
        val factory = NewWeatherPresetFactory(service<AbyssusCore>().documents.json)
        val valid = runCatchingKeepingCancellation { factory.validateSource(target.abss, target.sky) }
        valid.exceptionOrNull()?.let { reportWeatherFailure(project, it.documentDisplayMessage()); return }
        val dialog = NewWeatherPresetDialog(project, File(target.abss.parent.path, "assets"), target.sky.parent.name)
        if (dialog.showAndGet()) createWeatherPreset(project, target.abss, target.sky, dialog.nameField.text)
    }
}

internal class NewWeatherPresetDialog(project: Project, private val assetsDir: File, skyName: String) : DialogWrapper(project) {
    val nameField = JTextField("weather_$skyName", 28)
    private val reason = JLabel(" ")
    init {
        title = AbyssusBundle.message("newWeatherPresetTitle")
        setOKButtonText(AbyssusBundle.message("newWeatherPresetCreate"))
        nameField.name = "weather-preset-name"
        init()
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = validateName()
            override fun removeUpdate(e: DocumentEvent) = validateName()
            override fun changedUpdate(e: DocumentEvent) = validateName()
        })
        validateName()
    }
    private fun validateName() {
        val error = checkFolderName(assetsDir, nameField.text)
        isOKActionEnabled = error == null
        reason.text = error?.let { AbyssusBundle.message("newTerrainNameError.$it") } ?: " "
    }
    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        add(JLabel(AbyssusBundle.message("newWeatherPresetName")), BorderLayout.NORTH)
        add(nameField, BorderLayout.CENTER)
        add(reason, BorderLayout.SOUTH)
    }
    override fun getPreferredFocusedComponent(): JComponent = nameField
}

private fun reportWeatherFailure(project: Project, reason: String) = Messages.showErrorDialog(
    project, AbyssusBundle.message("newWeatherPresetFailed", reason), AbyssusBundle.message("newWeatherPresetTitle"))

/** Revalidates source and name, executes one asset transaction, and selects only after a successful refresh. EDT. */
fun createWeatherPreset(
    project: Project, abss: VirtualFile, sky: VirtualFile, name: String,
    report: (String) -> Unit = { reportWeatherFailure(project, it) },
    select: (String) -> Unit = { created -> ApplicationManager.getApplication().invokeLater(
        { if (!project.isDisposed) selectAssetInAbyssusView(project, abss, created) }, ModalityState.nonModal()) },
): AssetCommandResult? {
    val staged = runCatchingKeepingCancellation {
        NewWeatherPresetFactory(service<AbyssusCore>().documents.json).stage(abss, sky, name)
    }.getOrElse { report(it.documentDisplayMessage()); return null }
    val result = AssetFileCommand(project, LocalAssetFileStore(File(abss.parent.path))).execute(staged.transaction, onDone = { select(staged.name) })
    val reason = when (result) {
        AssetCommandResult.Done -> null
        is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
        is AssetCommandResult.Conflict -> AbyssusBundle.message("weatherConflict", result.path)
        is AssetCommandResult.Blocked -> result.reason
        AssetCommandResult.Cancelled -> AbyssusBundle.message("weatherCancelled")
        is AssetCommandResult.Failed -> result.cause.documentDisplayMessage()
    }
    reason?.let(report)
    return result
}
