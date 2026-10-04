/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.ASSETS_DIR
import net.nevinsky.abyssus.assets.terrain.generation.SourceSnapshot
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerationDraft
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerationSettings
import net.nevinsky.abyssus.assets.terrain.generation.TerrainGenerator
import net.nevinsky.abyssus.assets.terrain.generation.TerrainPreview
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.random.Random
import net.nevinsky.abyssus.dto.textOf

/** What the New terrain form hands over: a valid folder [name] and the finished [preview] of exactly the chosen inputs. */
class NewTerrainRequest(val name: String, val preview: TerrainPreview)

/**
 * The inputs of a new terrain: folder name, world size, resolution and the generation settings, with a grayscale
 * preview. The name, size and resolution editors are named `new-terrain-name`, `new-terrain-size` and
 * `new-terrain-resolution`, the settings `terrain-gen-<setting>`, the buttons `terrain-preview` and `terrain-randomize`,
 * the picture `terrain-heightmap`, the name/geometry reason `new-terrain-error` and the status `terrain-message`.
 * [request] is non-null only when everything is valid and a preview of exactly these inputs has finished; closing or
 * cancelling the dialog calls [dispose], which discards any draft without writing. [onValidityChanged] runs on the UI
 * thread whenever [request] may have changed.
 */
class NewTerrainForm(
    private val projectDir: File,
    generator: TerrainGenerator,
    background: (Runnable) -> Unit,
    ui: (Runnable) -> Unit,
    private val random: Random = Random.Default,
) : JPanel(VerticalLayout(JBUI.scale(6))) {
    var onValidityChanged: () -> Unit = {}

    private val assetsDir = File(projectDir, ASSETS_DIR)
    private val runner = TerrainPreviewRunner(generator, background, ui)
    private val draft = TerrainGenerationDraft(TerrainGenerationSettings(), DEFAULT_SIZE, DEFAULT_RESOLUTION, NO_SOURCE)

    private val nameField = JBTextField("").apply { name = "new-terrain-name" }
    private val sizeField = JBTextField(DEFAULT_SIZE.toString()).apply { name = "new-terrain-size" }
    private val resolutionField = JBTextField(DEFAULT_RESOLUTION.toString()).apply { name = "new-terrain-resolution" }
    private val settingFields = TerrainSettingField.entries.associateWith { field ->
        JBTextField(field.textOf(draft.settings)).apply {
            name = "terrain-gen-${field.key}"
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
        }
    }
    private val settingErrors = TerrainSettingField.entries.associateWith { JBLabel("").apply { foreground = JBColor.RED; name = "terrain-error-${it.key}" } }
    private val errorLabel = JBLabel("").apply { foreground = JBColor.RED; name = "new-terrain-error" }
    private val messageLabel = JBLabel("").apply { foreground = UIUtil.getContextHelpForeground(); name = "terrain-message" }
    private val heightmap = HeightmapView().apply { name = "terrain-heightmap" }
    private val previewButton = JButton(AbyssusBundle.message("terrainPreview")).apply { name = "terrain-preview" }
    private val randomizeButton = JButton(AbyssusBundle.message("terrainRandomize")).apply { name = "terrain-randomize" }

    private var disposed = false

    init {
        add(row(AbyssusBundle.message("newTerrainName"), nameField, null))
        add(row(AbyssusBundle.message("newTerrainSize"), sizeField, null))
        add(row(AbyssusBundle.message("newTerrainResolution"), resolutionField, null))
        add(errorLabel)
        TerrainSettingField.entries.forEach { add(row(it.label(), settingFields.getValue(it), settingErrors.getValue(it))) }
        add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { add(previewButton); add(randomizeButton) })
        add(heightmap)
        add(messageLabel)

        for (editor in listOf(nameField, sizeField, resolutionField)) {
            editor.document.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
                override fun textChanged(e: javax.swing.event.DocumentEvent) = geometryChanged()
            })
            editor.addActionListener { geometryChanged() }
            editor.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = geometryChanged()
            })
        }
        for ((field, editor) in settingFields) {
            editor.addActionListener { settingChanged(field) }
            editor.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = settingChanged(field)
            })
        }
        previewButton.addActionListener { startPreview() }
        randomizeButton.addActionListener {
            draft.randomizeSeed(random)
            runner.invalidate()
            settingFields.getValue(TerrainSettingField.SEED).text = TerrainSettingField.SEED.textOf(draft.settings)
            refresh(null)
        }
        refresh(null)
    }

    private fun row(label: String, editor: JComponent, error: JComponent?): JComponent = JPanel(GridBagLayout()).apply {
        add(JBLabel(label).apply { preferredSize = Dimension(JBUI.scale(LABEL_WIDTH), preferredSize.height) }, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
        add(editor, GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
        error?.let { add(it, GridBagConstraints().apply { gridx = 2; insets = JBUI.insetsLeft(8) }) }
    }

    private fun settingChanged(field: TerrainSettingField) {
        val editor = settingFields.getValue(field)
        if (editor.text == field.textOf(draft.settings)) return
        when (val parsed = field.parse(draft.settings, editor.text)) {
            is SettingParse.Failed -> {
                settingErrors.getValue(field).text = parsed.message
                editor.text = field.textOf(draft.settings)
            }
            is SettingParse.Parsed -> {
                settingErrors.getValue(field).text = ""
                draft.updateSettings(parsed.settings)
                runner.invalidate()
                refresh(null)
            }
        }
    }

    /** The name, size and resolution as typed; a change to the size or resolution drops the preview. */
    private fun geometryChanged() {
        val size = sizeField.text.trim().toIntOrNull()
        val resolution = resolutionField.text.trim().toIntOrNull()
        val geometry = checkGeometry(size, resolution)
        if (geometry == null) draft.updateGeometry(size!!, resolution!!) else {
            draft.cancel()
            runner.invalidate()
        }
        refresh(null)
    }

    private fun startPreview() {
        if (!canPreview()) return
        val started = runner.start(draft) { outcome ->
            refresh(
                when (outcome) {
                    PreviewOutcome.Ready -> AbyssusBundle.message("terrainPreviewReady")
                    is PreviewOutcome.Failed -> AbyssusBundle.message("terrainPreviewFailed", outcome.message)
                },
            )
        }
        if (started) refresh(AbyssusBundle.message("terrainGenerating"))
    }

    private fun geometryError(): GeometryError? = checkGeometry(sizeField.text.trim().toIntOrNull(), resolutionField.text.trim().toIntOrNull())

    private fun canPreview() = !disposed && geometryError() == null && draft.settingsErrors().isEmpty()

    /** The name's problem for the current text, or null. Reads the assets folder. */
    fun nameError(): FolderNameError? = checkFolderName(assetsDir, nameField.text)

    /** The inputs to create a terrain from, or null until the name, geometry and settings are valid and a matching preview exists. */
    fun request(): NewTerrainRequest? {
        if (disposed || nameError() != null || geometryError() != null) return null
        val preview = draft.applicable(NO_SOURCE) ?: return null
        return NewTerrainRequest(nameField.text.trim(), preview)
    }

    private fun refresh(message: String?) {
        val geometry = geometryError()
        val name = nameError()
        errorLabel.text = (geometry?.message() ?: name?.takeIf { nameField.text.isNotEmpty() }?.message()) ?: ""
        previewButton.isEnabled = canPreview()
        heightmap.image = draft.preview?.image
        messageLabel.text = message ?: when {
            draft.settingsErrors().isNotEmpty() -> "<html>${settingsProblems(draft.settings).joinToString("<br>")}</html>"
            draft.preview == null -> AbyssusBundle.message("newTerrainNeedsPreview")
            else -> AbyssusBundle.message("terrainPreviewReady")
        }
        onValidityChanged()
    }

    /** Discards the draft and any running preview without writing anything. */
    fun dispose() {
        disposed = true
        runner.dispose()
        draft.cancel()
    }

    private companion object {
        /** A new terrain has no source: nothing outside the form can invalidate its preview. */
        val NO_SOURCE = SourceSnapshot(null, null, null, folderExists = false)
        const val DEFAULT_SIZE = 1600
        const val DEFAULT_RESOLUTION = 180
        const val LABEL_WIDTH = 120
    }
}
