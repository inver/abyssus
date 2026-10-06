/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.terrain

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.terrain.generation.HeightmapImage
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/** A square grayscale picture of a heightmap; a bordered blank square when there is no preview. */
internal class HeightmapView(image: HeightmapImage? = null) : JComponent() {
    var image: HeightmapImage? = image
        set(value) {
            field = value
            picture = value?.let { img ->
                BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_GRAY).also { out ->
                    out.raster.setDataElements(0, 0, img.width, img.height, img.pixels)
                }
            }
            repaint()
        }

    private var picture: BufferedImage? = null

    init {
        this.image = image
    }

    init {
        preferredSize = Dimension(JBUI.scale(SIZE), JBUI.scale(SIZE))
        minimumSize = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val p = picture
        if (p == null) {
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
            return
        }
        g.drawImage(p, 0, 0, width, height, null)
    }

    private companion object {
        const val SIZE = 160
    }
}

/**
 * The controls of a terrain's regeneration: resolution (read only), recipe status, the seven settings, Regenerate preview,
 * Randomize seed, Apply, Cancel and the grayscale preview. Editors are named `terrain-gen-<setting>`, the buttons
 * `terrain-preview`, `terrain-randomize`, `terrain-apply`, `terrain-cancel`, the picture `terrain-heightmap`, and the
 * status and message labels `terrain-recipe-status` and `terrain-message`, which is how tests reach them. The view holds no
 * state of its own: it is rebuilt from the [controller] whenever that changes.
 */
internal class TerrainGenerationSection(private val controller: TerrainGenerationController) : JPanel(BorderLayout()) {
    init {
        val box = JPanel(VerticalLayout(JBUI.scale(6)))
        box.border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0), JBUI.Borders.empty(12, 16, 16, 16))
        box.add(JBLabel(AbyssusBundle.message("terrainSection").uppercase()).apply { foreground = UIUtil.getContextHelpForeground(); font = JBFont.small() })
        box.add(JBLabel(AbyssusBundle.message("terrainResolution", controller.resolution.toString())).apply { name = "terrain-resolution" })
        box.add(JBLabel("<html>${controller.recipeText()}</html>").apply { name = "terrain-recipe-status"; foreground = UIUtil.getContextHelpForeground() })
        TerrainSettingField.entries.forEach { box.add(settingRow(it)) }
        val problems = controller.problems
        if (problems.isNotEmpty()) box.add(JBLabel("<html>${problems.joinToString("<br>")}</html>").apply { name = "terrain-problems"; foreground = JBColor.RED })
        box.add(buttons())
        box.add(HeightmapView(controller.draft.preview?.image).apply { name = "terrain-heightmap" })
        controller.message?.let { text ->
            box.add(JBLabel("<html>$text</html>").apply { name = "terrain-message"; foreground = UIUtil.getContextHelpForeground() })
        }
        add(box, BorderLayout.NORTH)
    }

    private fun settingRow(field: TerrainSettingField): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "terrain-error-${field.key}" }
        val editor = JBTextField(field.textOf(controller.settings)).apply {
            name = "terrain-gen-${field.key}"
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
        }
        val save = {
            if (editor.text != field.textOf(controller.settings)) {
                when (val parsed = field.parse(controller.settings, editor.text)) {
                    is SettingParse.Failed -> {
                        error.text = parsed.message
                        editor.text = field.textOf(controller.settings)
                    }
                    is SettingParse.Parsed -> controller.update(parsed.settings)
                }
            }
        }
        editor.addActionListener { save() }
        editor.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = save()
        })
        val label = JBLabel(field.label()).apply { preferredSize = Dimension(JBUI.scale(LABEL_WIDTH), preferredSize.height) }
        return JPanel(GridBagLayout()).apply {
            add(label, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
            add(editor, GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            add(error, GridBagConstraints().apply { gridx = 2; insets = JBUI.insetsLeft(8) })
        }
    }

    private fun buttons(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
        fun button(name: String, key: String, enabled: Boolean, action: () -> Unit) = JButton(AbyssusBundle.message(key)).also {
            it.name = name
            it.isEnabled = enabled
            it.addActionListener { action() }
            add(it)
        }
        button("terrain-preview", "terrainPreview", controller.canPreview) { controller.preview() }
        button("terrain-randomize", "terrainRandomize", true) { controller.randomizeSeed() }
        button("terrain-apply", "terrainApply", controller.canApply) { controller.apply() }
        button("terrain-cancel", "terrainCancel", true) { controller.cancel() }
    }

    private companion object {
        const val LABEL_WIDTH = 120
    }
}

/** Shown instead of the controls when the terrain cannot be regenerated. */
internal fun terrainUnusableNote(reason: String): JComponent = JPanel(BorderLayout()).apply {
    border = BorderFactory.createCompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0), JBUI.Borders.empty(12, 16, 16, 16))
    add(JBLabel("<html>${AbyssusBundle.message("terrainDataInvalid", reason)}</html>").apply { name = "terrain-unusable"; foreground = JBColor.RED }, BorderLayout.NORTH)
}
