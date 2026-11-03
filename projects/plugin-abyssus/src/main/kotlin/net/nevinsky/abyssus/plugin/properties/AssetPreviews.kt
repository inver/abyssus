/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.plugin.ui.fitInside
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.lib.core.editor.meta.*
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/** The muted text color of captions and notes in the properties panels. */
internal fun secondaryColor(): Color = UIUtil.getContextHelpForeground()

internal fun facePreviews(faces: List<FaceCell>): JComponent {
    val grid = JPanel(GridLayout(0, 3, JBUI.scale(10), JBUI.scale(10)))
    for (f in faces) grid.add(faceCell(f))
    return JPanel(VerticalLayout(JBUI.scale(8))).apply {
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(12, 16, 16, 16)
        )
        add(JBLabel(AbyssusBundle.message("propertiesFacePreviews").uppercase()).apply {
            foreground = secondaryColor(); font = JBFont.small()
        })
        add(grid)
    }
}

internal fun hdrPreview(cell: HdrCell): JComponent = JPanel(VerticalLayout(JBUI.scale(8))).apply {
    border = BorderFactory.createCompoundBorder(
        JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
        JBUI.Borders.empty(12, 16, 16, 16)
    )
    add(JBLabel(AbyssusBundle.message("propertiesHdrPreview").uppercase()).apply {
        foreground = secondaryColor(); font = JBFont.small()
    })
    add(Thumbnail(cell.image).apply {
        name = "hdr-preview"
        preferredSize = Dimension(JBUI.scale(288), JBUI.scale(144))
    })
    add(JBLabel(cell.label).apply {
        foreground = secondaryColor(); font = Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().size)
    })
}

private fun faceCell(f: FaceCell): JComponent {
    val caption = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
        add(JBLabel(f.face), BorderLayout.WEST)
        add(JBLabel(f.file).apply {
            foreground = secondaryColor(); font =
            Font(Font.MONOSPACED, Font.PLAIN, JBFont.small().size); horizontalAlignment = SwingConstants.RIGHT
        }, BorderLayout.CENTER)
    }
    return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
        add(Thumbnail(f.image).apply { name = "face-${f.face}" }, BorderLayout.CENTER)
        add(caption, BorderLayout.SOUTH)
    }
}

/** A face thumbnail fitted into its cell; a bordered blank cell when the image is missing. */
internal class Thumbnail(val image: BufferedImage?) : JComponent() {
    init {
        preferredSize = Dimension(JBUI.scale(96), JBUI.scale(72))
    }

    override fun paintComponent(g: Graphics) {
        val img = image
        if (img == null) {
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
            return
        }
        val (w, h) = fitInside(img.width, img.height, width, height, upscale = true)
        g.drawImage(img, (width - w) / 2, (height - h) / 2, w, h, null)
    }
}
