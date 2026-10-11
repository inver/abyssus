/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import net.nevinsky.abyssus.lib.core.editor.ui.SampleResult
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/** The preview's size choices; the first entry is the default. */
internal val PREVIEW_PRESETS = listOf(UiViewport(1280, 720), UiViewport(1920, 1080), UiViewport(1024, 768), UiViewport(800, 600), UiViewport(1366, 768), UiViewport(2560, 1440))

/**
 * Controls of the interactive preview: the size it lays out for, the scene shown behind it, a reset, editable sample values
 * and the log of what the UI did. Everything here is session-local or an editor preference; none of it is written to
 * the document. [scenes] lists the project's scene files for the backdrop choice.
 */
class UiPreviewPanel(private val controller: UiEditorController, private val scenes: () -> List<String>) : JPanel(BorderLayout()) {
    private val sizeBox = JComboBox<String>().apply { name = "preview-size" }
    internal val widthField = JTextField(5).apply { name = "preview-width" }
    internal val heightField = JTextField(5).apply { name = "preview-height" }
    private val sizeStatus = JBLabel(" ").apply { foreground = JBColor.RED; name = "preview-size-status" }
    private val backdropBox = JComboBox<String>().apply { name = "preview-backdrop" }
    private val sampleForm = JPanel(GridBagLayout())
    private val logModel = DefaultListModel<String>()
    internal val log = JBList(logModel).apply { name = "preview-log" }
    private val hint = JBLabel(AbyssusBundle.message("uiPreviewHint"))
    internal val sampleFields = LinkedHashMap<String, JTextField>()
    internal val sampleStatus = LinkedHashMap<String, JBLabel>()
    internal val rowFields = LinkedHashMap<String, JTextField>()
    private var building = false
    private var shownBindings: List<String>? = null
    private val timer = Timer(250) { refreshLive() }

    init {
        val top = JPanel(GridBagLayout())
        fun row(y: Int, label: String, vararg parts: JComponent) {
            top.add(JBLabel(label), GridBagConstraints().apply { gridx = 0; gridy = y; anchor = GridBagConstraints.WEST; insets = Insets(2, 4, 2, 4) })
            top.add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply { parts.forEach(::add) }, GridBagConstraints().apply { gridx = 1; gridy = y; anchor = GridBagConstraints.WEST })
        }
        val apply = JButton(AbyssusBundle.message("uiPreviewApply")).apply { name = "preview-apply"; addActionListener { applyCustomSize() } }
        row(0, AbyssusBundle.message("uiPreviewResolution"), sizeBox)
        row(1, AbyssusBundle.message("uiPreviewCustom"), widthField, JBLabel("×"), heightField, apply)
        top.add(sizeStatus, GridBagConstraints().apply { gridx = 1; gridy = 2; anchor = GridBagConstraints.WEST })
        row(3, AbyssusBundle.message("uiPreviewBackdrop"), backdropBox)
        row(4, "", JButton(AbyssusBundle.message("uiPreviewReset")).apply { name = "preview-reset"; addActionListener { controller.resetPreview() } })
        sizeBox.addActionListener { if (!building) (sizeBox.selectedIndex.takeIf { it in PREVIEW_PRESETS.indices })?.let { PREVIEW_PRESETS[it].let { v -> controller.setPreviewViewport(v.width, v.height) } } }
        backdropBox.addActionListener { if (!building) controller.setBackdropScene((backdropBox.selectedItem as? String)?.takeIf { it != none }) }
        val centre = JPanel(BorderLayout()).apply {
            add(JBLabel(AbyssusBundle.message("uiPreviewSamples")), BorderLayout.NORTH)
            add(JBScrollPane(sampleForm), BorderLayout.CENTER)
        }
        val bottom = JPanel(BorderLayout()).apply {
            add(JBLabel(AbyssusBundle.message("uiPreviewLog")), BorderLayout.NORTH)
            add(JBScrollPane(log).apply { preferredSize = java.awt.Dimension(200, 120) }, BorderLayout.CENTER)
        }
        add(top, BorderLayout.NORTH)
        add(centre, BorderLayout.CENTER)
        add(bottom, BorderLayout.SOUTH)
        controller.addListener(::refresh)
        refresh()
    }

    private val none = AbyssusBundle.message("uiPreviewNoBackdrop")

    private fun applyCustomSize() {
        val w = widthField.text.trim().toIntOrNull()
        val h = heightField.text.trim().toIntOrNull()
        sizeStatus.text = if (w != null && h != null && controller.setPreviewViewport(w, h)) " " else AbyssusBundle.message("uiPreviewBadSize")
    }

    /** Rebuilds what depends on the document or the controller's settings. */
    internal fun refresh() {
        building = true
        try {
            val v = controller.previewViewport
            val labels = PREVIEW_PRESETS.map { "${it.width} × ${it.height}" }
            val items = if (v in PREVIEW_PRESETS) labels else labels + "${v.width} × ${v.height}"
            if ((0 until sizeBox.itemCount).map { sizeBox.getItemAt(it) } != items) { sizeBox.removeAllItems(); items.forEach(sizeBox::addItem) }
            sizeBox.selectedIndex = items.indexOf("${v.width} × ${v.height}")
            if (!widthField.hasFocus()) widthField.text = v.width.toString()
            if (!heightField.hasFocus()) heightField.text = v.height.toString()
            val sceneNames = scenes()
            val backdrops = listOf(none) + sceneNames + listOfNotNull(controller.backdropScene?.takeIf { it !in sceneNames })
            if ((0 until backdropBox.itemCount).map { backdropBox.getItemAt(it) } != backdrops) { backdropBox.removeAllItems(); backdrops.forEach(backdropBox::addItem) }
            backdropBox.selectedItem = controller.backdropScene ?: none
        } finally {
            building = false
        }
        rebuildSamples()
        refreshLive()
    }

    /** What decides which sample controls exist: the bindings and, for a list, how many rows it has. */
    private fun shapeOf(state: net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState): List<String> = state.collection.bindings.map { b ->
        if (b.type == net.nevinsky.abyssus.lib.core.ui.model.UiBindingType.LIST) "${b.name}:${(state.samples[b.name] as? UiValue.Rows)?.items?.size ?: 0}" else b.name
    }

    private fun rebuildSamples() {
        val state = controller.previewState
        val shape = state?.let(::shapeOf)
        if (shape == shownBindings && state != null) return
        shownBindings = shape
        sampleForm.removeAll(); sampleFields.clear(); sampleStatus.clear(); rowFields.clear()
        if (state == null) {
            sampleForm.add(hint, GridBagConstraints().apply { gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = Insets(4, 4, 4, 4) })
        } else {
            var y = 0
            for (b in state.collection.bindings) {
                if (b.type == net.nevinsky.abyssus.lib.core.ui.model.UiBindingType.LIST) { y = listEditor(state, b, y); continue }
                val field = JTextField(10).apply { name = "sample-${b.name}" }
                val status = JBLabel(" ").apply { foreground = JBColor.RED; name = "sample-status-${b.name}" }
                fun commit() {
                    when (val r = state.setSample(b.name, field.text)) {
                        is SampleResult.Applied -> status.text = " "
                        is SampleResult.Rejected -> status.text = AbyssusBundle.message("uiSampleProblem.${r.reason.name}")
                    }
                }
                field.addActionListener { commit() }
                field.addFocusListener(object : java.awt.event.FocusAdapter() { override fun focusLost(e: java.awt.event.FocusEvent) { if (field.text != show(state.samples[b.name])) commit() } })
                sampleFields[b.name] = field; sampleStatus[b.name] = status
                sampleForm.add(JBLabel("${b.name} (${b.type.json})"), GridBagConstraints().apply { gridx = 0; gridy = y; anchor = GridBagConstraints.WEST; insets = Insets(2, 4, 2, 4) })
                sampleForm.add(field, GridBagConstraints().apply { gridx = 1; gridy = y; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
                sampleForm.add(status, GridBagConstraints().apply { gridx = 2; gridy = y; anchor = GridBagConstraints.WEST })
                y++
            }
        }
        sampleForm.revalidate(); sampleForm.repaint()
    }

    /** The rows of a list sample, one line per row with a box per field, and Add / Remove; returns the next free grid row. */
    private fun listEditor(state: net.nevinsky.abyssus.lib.core.editor.ui.UiPreviewState, b: net.nevinsky.abyssus.lib.core.ui.model.UiBinding, start: Int): Int {
        var y = start
        sampleForm.add(JBLabel("<html><b>${b.name}</b> (list)</html>"), GridBagConstraints().apply { gridx = 0; gridy = y++; gridwidth = 3; anchor = GridBagConstraints.WEST; insets = Insets(6, 4, 2, 4) })
        val rows = (state.samples[b.name] as? UiValue.Rows)?.items.orEmpty()
        val status = JBLabel(" ").apply { foreground = JBColor.RED; name = "list-status-${b.name}" }
        sampleStatus[b.name] = status
        fun report(r: SampleResult) { status.text = (r as? SampleResult.Rejected)?.let { AbyssusBundle.message("uiSampleProblem.${it.reason.name}") } ?: " " }
        rows.forEachIndexed { i, row ->
            val line = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
            for (f in b.fields) {
                val box = JTextField(show(row[f.name]), 7).apply { name = "row-${b.name}-$i-${f.name}"; toolTipText = "${f.name} (${f.type.json})" }
                fun commit() { if (box.text != show(state.samples.let { (it[b.name] as? UiValue.Rows)?.items?.getOrNull(i)?.get(f.name) })) report(state.setRowField(b.name, i, f.name, box.text)) }
                box.addActionListener { commit() }
                box.addFocusListener(object : java.awt.event.FocusAdapter() { override fun focusLost(e: java.awt.event.FocusEvent) { commit() } })
                rowFields["${b.name}/$i/${f.name}"] = box
                line.add(box)
            }
            line.add(JButton("−").apply { name = "remove-row-${b.name}-$i"; addActionListener { report(state.removeRow(b.name, i)); controller.resetView() } })
            sampleForm.add(line, GridBagConstraints().apply { gridx = 0; gridy = y++; gridwidth = 3; anchor = GridBagConstraints.WEST })
        }
        sampleForm.add(JButton("+").apply { name = "add-row-${b.name}"; addActionListener { report(state.addRow(b.name)); controller.resetView() } }, GridBagConstraints().apply { gridx = 0; gridy = y++; anchor = GridBagConstraints.WEST; insets = Insets(2, 4, 2, 4) })
        sampleForm.add(status, GridBagConstraints().apply { gridx = 1; gridy = y - 1; gridwidth = 2; anchor = GridBagConstraints.WEST })
        return y
    }

    private fun show(value: UiValue?): String = when (value) {
        is UiValue.Num -> if (value.value == Math.rint(value.value)) value.value.toLong().toString() else value.value.toString()
        is UiValue.Bool -> value.value.toString()
        is UiValue.Text -> value.value
        is UiValue.Rows -> "${value.items.size} rows"
        null -> ""
    }

    /** The parts that change while the preview runs: sample values moved by controls, and the action log. */
    internal fun refreshLive() {
        val state = controller.previewState ?: run { if (logModel.size() > 0) logModel.clear(); return }
        for ((name, field) in sampleFields) if (!field.hasFocus()) show(state.samples[name]).let { if (field.text != it) field.text = it }
        for ((key, box) in rowFields) if (!box.hasFocus()) {
            val (list, i, field) = key.split('/')
            show((state.samples[list] as? UiValue.Rows)?.items?.getOrNull(i.toInt())?.get(field)).let { if (box.text != it) box.text = it }
        }
        if (shapeOf(state) != shownBindings) rebuildSamples()
        val lines = state.log.map { "#${it.sequence}  ${it.text}" }
        if ((0 until logModel.size()).map { logModel.getElementAt(it) } != lines) { logModel.clear(); lines.forEach(logModel::addElement) }
    }

    override fun addNotify() { super.addNotify(); timer.start() }
    override fun removeNotify() { timer.stop(); super.removeNotify() }
}
