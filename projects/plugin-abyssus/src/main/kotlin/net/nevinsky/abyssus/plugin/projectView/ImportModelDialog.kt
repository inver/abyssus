/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.assets.displayMessage
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assimp.UpAxis
import net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.core.editor.modelimport.LengthUnit
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.projectView.preview.ModelPreviewCanvas
import net.nevinsky.abyssus.plugin.projectView.preview.PreviewModel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The Import Model dialog over [ModelImportForm]: folder name, unit and up axis (marked when read from the file or
 * defined by its format), size, Add to scene (naming the scene, or disabled with the reason), what is left out or
 * approximated, the status line, the animation to play and the live preview. The source is already open; each
 * settings change recomputes only the transform, on a pooled thread, and hands the result to the preview. The
 * preview's GL is released before the window closes, whichever way it closes.
 */
internal class ImportModelDialog(project: Project, private val form: ModelImportForm) : DialogWrapper(project) {
    private val name = JBTextField(form.folderName)
    private val problems = JBLabel().apply { foreground = JBColor.RED }
    private val unit = ComboBox(LengthUnit.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it.id }
    }
    private val unitMark = JBLabel().apply { foreground = JBColor.GRAY }
    private val upAxis = ComboBox(arrayOf(UpAxis.Y, UpAxis.Z))
    private val upMark = JBLabel().apply { foreground = JBColor.GRAY }
    private val original = JBRadioButton(AbyssusBundle.message("importModelSizeOriginal"))
    private val largest = JBRadioButton(AbyssusBundle.message("importModelSizeLargest"))
    private val height = JBRadioButton(AbyssusBundle.message("importModelSizeHeight"))
    private val size = JBTextField(form.sizeText, 6)
    private val addToScene = JBCheckBox()
    private val animation = ComboBox<String>()
    private val status = JBLabel()
    private val notes = JBLabel()
    private val preview = ModelPreviewCanvas()
    private var generation = 0
    private var closed = false
    private var binding = false

    init {
        title = AbyssusBundle.message("importModelTitle")
        setOKButtonText(AbyssusBundle.message("importModelCreate"))
        ButtonGroup().apply { add(original); add(largest); add(height) }
        unit.selectedItem = form.unit
        upAxis.selectedItem = form.upAxis
        when (form.fitMode) {
            FitMode.ORIGINAL -> original.isSelected = true
            FitMode.LARGEST_EXTENT -> largest.isSelected = true
            FitMode.HEIGHT -> height.isSelected = true
        }
        when (val placement = form.placement) {
            is PlacementOption.Available -> {
                addToScene.text = AbyssusBundle.message("importModelAddToScene", placement.sceneName)
                addToScene.isSelected = form.addToScene
            }
            is PlacementOption.Disabled -> {
                addToScene.text = AbyssusBundle.message("importModelAddToSceneOff", placement.reason.message())
                addToScene.isEnabled = false
            }
        }
        form.animations.forEach { animation.addItem(it.name) }
        animation.isEnabled = form.animations.size > 1
        form.animation?.let { animation.selectedItem = it }
        notes.text = notesText()

        name.document.addDocumentListener(changes { form.folderName = name.text; refresh(previewChanged = false) })
        size.document.addDocumentListener(changes { form.sizeText = size.text; refresh() })
        unit.addActionListener { (unit.selectedItem as? LengthUnit)?.let { form.unit = it }; refresh() }
        upAxis.addActionListener { (upAxis.selectedItem as? UpAxis)?.let { form.upAxis = it }; refresh() }
        original.addActionListener { form.fitMode = FitMode.ORIGINAL; refresh() }
        largest.addActionListener { form.fitMode = FitMode.LARGEST_EXTENT; refresh() }
        height.addActionListener { form.fitMode = FitMode.HEIGHT; refresh() }
        addToScene.addActionListener { form.addToScene = addToScene.isSelected }
        animation.addActionListener {
            form.animation = animation.selectedItem as? String
            preview.play(form.animation)
        }
        preview.play(form.animation)
        init()
        refresh()
    }

    private fun changes(block: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = block()
        override fun removeUpdate(e: DocumentEvent) = block()
        override fun changedUpdate(e: DocumentEvent) = block()
    }

    /** Shows the form's state; with [previewChanged], recomputes the transform for the preview. */
    private fun refresh(previewChanged: Boolean = true) {
        if (binding) return
        binding = true
        try {
            unitMark.text = mark(form.unitSource)
            upMark.text = mark(form.upSource) +
                if (form.statesXUp) " " + AbyssusBundle.message("importModelXUp") else ""
            size.isEnabled = form.fitMode != FitMode.ORIGINAL
            problems.text = form.problems().joinToString("<br>", "<html>", "</html>") { it.message(form) }
                .takeIf { form.problems().isNotEmpty() } ?: ""
            isOKActionEnabled = form.createEnabled
        } finally {
            binding = false
        }
        if (previewChanged) updatePreview()
    }

    private fun mark(source: ValueSource): String = when (source) {
        ValueSource.FILE -> AbyssusBundle.message("importModelFromFile")
        ValueSource.FORMAT -> AbyssusBundle.message("importModelFromFormat")
        ValueSource.DEFAULT -> AbyssusBundle.message("importModelDefault")
        ValueSource.CHOSEN -> ""
    }

    private fun notesText(): String {
        val lines = form.leftOut.map { AbyssusBundle.message("importModelLeftOut", it.item, it.reason.id) } +
            form.approximated.map { AbyssusBundle.message("importModelApproximated", it.item, it.reason) }
        return if (lines.isEmpty()) "" else lines.joinToString("<br>", "<html>${AbyssusBundle.message("importModelNotes")}<br>", "</html>")
    }

    /** Converts on a pooled thread from the open source; only the newest result reaches the preview. */
    private fun updatePreview() {
        val error = form.sourceError
        if (error != null) {
            preview.showMessage(AbyssusBundle.message("importModelUnreadable", error))
            status.text = ""
            return
        }
        val settings = form.previewSettings() ?: return
        val file = FileHandle(form.sourceFile)
        val ticket = ++generation
        AppExecutorUtil.getAppExecutorService().execute {
            val result = runCatchingKeepingCancellation {
                val transformed = form.preview(settings) ?: return@runCatchingKeepingCancellation null
                GdxNativesLoader.load()
                PreviewModel(transformed.data, AssimpModelLoader().decodeTextures(transformed.data, file), file, transformed.size)
            }
            ApplicationManager.getApplication().invokeLater({
                val model = result.getOrNull()
                if (closed || ticket != generation) {
                    model?.pixmaps?.values?.forEach(Pixmap::dispose)
                    return@invokeLater
                }
                result.exceptionOrNull()?.let { preview.showMessage(it.displayMessage()) }
                if (model != null) {
                    status.text = form.status(model.size)
                    preview.show(model)
                }
            }, ModalityState.any())
        }
    }

    private fun releasePreview() {
        closed = true
        preview.release()
    }

    override fun doOKAction() {
        releasePreview()
        super.doOKAction()
    }

    override fun doCancelAction() {
        releasePreview()
        super.doCancelAction()
    }

    override fun dispose() {
        releasePreview()
        super.dispose()
    }

    override fun getPreferredFocusedComponent(): JComponent = name

    override fun createCenterPanel(): JComponent {
        fun row(vararg parts: JComponent) = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply { parts.forEach(::add) }
        val settings = FormBuilder.createFormBuilder()
            .addLabeledComponent(AbyssusBundle.message("importModelSource"), JBLabel(form.sourceFile.name))
            .addLabeledComponent(AbyssusBundle.message("newTerrainName"), name)
            .addLabeledComponent(AbyssusBundle.message("importModelUnit"), row(unit, unitMark))
            .addLabeledComponent(AbyssusBundle.message("importModelUpAxis"), row(upAxis, upMark))
            .addLabeledComponent(AbyssusBundle.message("importModelSize"), row(original, largest, height, size))
            .addComponent(addToScene)
            .addLabeledComponent(AbyssusBundle.message("importModelAnimation"), animation)
            .addComponent(problems)
            .addComponent(notes)
            .panel
        val right = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(preview, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            add(settings, BorderLayout.WEST)
            add(right, BorderLayout.CENTER)
        }
    }
}
