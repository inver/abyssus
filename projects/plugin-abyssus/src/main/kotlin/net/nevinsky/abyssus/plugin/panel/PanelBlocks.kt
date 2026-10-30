/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.editor.panel.Badge
import net.nevinsky.abyssus.lib.core.editor.panel.BadgeKind
import net.nevinsky.abyssus.lib.core.editor.panel.Header
import net.nevinsky.abyssus.lib.core.editor.panel.Section
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.projectView.DesignColors
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.AbstractBorder

/** The sizes of the panel's design (at scale 1): a 150 px label column, fields at the look and feel's height, a 26 px Browse, a 34 px section header. */
object PanelMetrics {
    const val LABEL_COLUMN = 150
    const val BROWSE = 26
    const val SECTION_HEADER = 34
    const val HEADER_SIDE = 12
    const val HEADER_GAP = 6
    const val HEADER_ICON = 24

    /** The opacity of a disabled section's body. */
    const val DISABLED_ALPHA = 0.55f

    fun scaled(value: Int): Int = JBUI.scale(value)
}

private const val HAIRLINE_PX = 1

/**
 * Gives a text box or combo the height the look and feel draws it at, at [width], or at the width it already has when
 * negative. Every field of the panel then has one height, a field that was never sized included.
 */
internal fun JComponent.pinFieldHeight(width: Int = -1) {
    val keep = if (isPreferredSizeSet) preferredSize.width else -1
    preferredSize = null
    minimumSize = null
    val height = maxOf(preferredSize.height, minimumSize.height)
    preferredSize = Dimension(if (width >= 0) width else if (keep >= 0) keep else preferredSize.width, height)
    minimumSize = Dimension(minimumSize.width, height)
    maximumSize = Dimension(maximumSize.width, height)
}

/** The monospace font of values, in the label size. */
internal fun monoFont(): Font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)

/** A small outlined tag: `read-only`, `no scene uses this`, `game`. */
class BadgeLabel(text: String, foreground: Color, private val outline: Color) : JBLabel(text) {
    init {
        font = JBFont.small()
        this.foreground = foreground
        border = object : AbstractBorder() {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = outline
                    g2.drawRoundRect(x, y, width - 1, height - 1, JBUI.scale(6), JBUI.scale(6))
                } finally {
                    g2.dispose()
                }
            }

            override fun getBorderInsets(c: Component) = JBUI.insets(1, 6)
        }
    }

    companion object {
        fun of(badge: Badge): BadgeLabel = when (badge.kind) {
            BadgeKind.READ_ONLY -> BadgeLabel(badge.text, DesignColors.SECONDARY, DesignColors.BORDER)
            BadgeKind.UNUSED -> BadgeLabel(badge.text, DesignColors.AMBER, DesignColors.AMBER)
        }.also { it.name = if (badge.kind == BadgeKind.READ_ONLY) "badge-read-only" else "badge-unused" }

        fun game(): BadgeLabel =
            BadgeLabel(AbyssusBundle.message("panelBadgeGame"), DesignColors.GAME, DesignColors.GAME)
                .also { it.name = "badge-game" }
    }
}

/**
 * The top of the panel: the breadcrumb, the 24 px icon, the 15 px title, the subtitle and the badges. Components are
 * named `header-breadcrumb`, `header-title` (or [titleName]), `header-subtitle`, `badge-*` and `shared-asset-note`.
 */
class HeaderPanel(header: Header, icon: Icon, titleName: String = "header-title", dim: Boolean = false) :
    JPanel(BorderLayout()) {
    init {
        name = "header"
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.emptyBottom(4),
            BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(Color.BLACK, 0, 0, 1, 0),
                JBUI.Borders.empty(0, 12, 8, 12),
            ),
        )
        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val crumbs = header.breadcrumb.filter { it.isNotBlank() }
        if (crumbs.isNotEmpty()) column.add(
            JBLabel(crumbs.joinToString("  ›  ")).apply {
                name = "header-breadcrumb"; font = JBFont.small(); foreground = DesignColors.SECONDARY
                alignmentX = LEFT_ALIGNMENT; border = JBUI.Borders.emptyBottom(6)
            },
        )
        // The icon spans the title and subtitle rows; the text sits to its right.
        val text = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val title = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false; alignmentX = LEFT_ALIGNMENT
            add(JBLabel(header.title).apply {
                name = titleName; font = JBFont.label().asBold().biggerOn(2f)
                if (dim) foreground = DesignColors.SECONDARY
            })
            header.badges.forEach {
                add(Box.createHorizontalStrut(JBUI.scale(8)))
                add(BadgeLabel.of(it))
            }
        }
        text.add(title)
        text.add(
            JBLabel(header.subtitle).apply {
                name = "header-subtitle"; foreground = DesignColors.SECONDARY; alignmentX = LEFT_ALIGNMENT
                border = JBUI.Borders.emptyTop(2)
            },
        )
        if (header.note.isNotEmpty()) text.add(
            JBLabel(header.note).apply {
                name = "shared-asset-note"; font = JBFont.small(); foreground = DesignColors.SECONDARY
                alignmentX = LEFT_ALIGNMENT; border = JBUI.Borders.emptyTop(2)
            },
        )
        val row = JPanel(BorderLayout()).apply {
            isOpaque = false; alignmentX = LEFT_ALIGNMENT
            add(
                JBLabel(sized(icon, PanelMetrics.HEADER_ICON)).apply {
                    name = "header-icon"; border = JBUI.Borders.emptyRight(10)
                    verticalAlignment = SwingConstants.CENTER
                },
                BorderLayout.WEST,
            )
            add(text, BorderLayout.CENTER)
        }
        column.add(row)
        add(column, BorderLayout.CENTER)
    }

    private fun sized(icon: Icon, px: Int): Icon {
        val target = JBUI.scale(px).toFloat()
        val width = icon.iconWidth.takeIf { it > 0 } ?: return icon
        return IconUtil.scale(icon, null, target / width)
    }
}

/** A panel that draws its children at [alpha]; a disabled section's body, which stays editable. */
class DimPanel(layout: java.awt.LayoutManager, private var alpha: Float = 1f) : JPanel(layout) {
    fun setDim(dim: Boolean) {
        alpha = if (dim) PanelMetrics.DISABLED_ALPHA else 1f
        repaint()
    }

    override fun paint(g: Graphics) {
        if (alpha >= 1f) return super.paint(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            super.paint(g2)
        } finally {
            g2.dispose()
        }
    }
}

/** A header group: its children in a row, 6 px apart and centered on the header's height. */
private fun centeredRow(): JPanel = object : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
    }

    override fun add(comp: Component): Component {
        if (componentCount > 0) {
            super.add(Box.createHorizontalStrut(JBUI.scale(PanelMetrics.HEADER_GAP)))
        }
        (comp as? JComponent)?.alignmentY = CENTER_ALIGNMENT
        return super.add(comp)
    }

    override fun getPreferredSize(): Dimension =
        Dimension(super.getPreferredSize().width, PanelMetrics.scaled(PanelMetrics.SECTION_HEADER))
}

/** The gray line that fills the section header between the title and the right-hand controls, at mid height. */
private class TitleRule : JComponent() {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        g.color = DesignColors.HAIRLINE
        val y = height / 2
        g.fillRect(0, y, width, 1)
    }
}

/**
 * One framed section: the collapse chevron, the kind icon, the title, a `game` badge and the class hint, the `Enabled`
 * check box when the section has one, a hairline, and the [body] indented under it. Components are named
 * `section-<key>`, `section-chevron-<key>`, `section-title-<key>`, `section-hint-<key>` and `section-enabled-<key>`.
 * [actions] go after the check box (the Remove button of a component).
 */
class PropertySection(
    val section: Section,
    icon: Icon?,
    private val body: JComponent,
    collapsed: Boolean,
    private val onCollapse: (Boolean) -> Unit,
    onEnabled: ((Boolean) -> Unit)?,
    actions: List<JComponent> = emptyList(),
) : JPanel(BorderLayout()) {
    var isCollapsed: Boolean = collapsed
        private set

    private var indented: JComponent = body

    private val chevron = JBLabel(chevronIcon(collapsed)).apply {
        name = "section-chevron-${section.key}"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        accessibleContext.accessibleName =
            AbyssusBundle.message(if (collapsed) "panelExpand" else "panelCollapse", section.title)
    }
    val enabledBox: JBCheckBox? = section.enable?.let { toggle ->
        JBCheckBox(AbyssusBundle.message("panelEnabled"), toggle.checked).apply {
            name = "section-enabled-${section.key}"
            isOpaque = false
            accessibleContext.accessibleName = AbyssusBundle.message("panelEnabledFor", section.title)
            if (onEnabled != null) addActionListener { onEnabled(isSelected) }
        }
    }

    init {
        name = "section-${section.key}"
        isOpaque = false
        val dim = section.disabled
        val head = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, PanelMetrics.HEADER_SIDE)
            preferredSize = Dimension(0, PanelMetrics.scaled(PanelMetrics.SECTION_HEADER))
        }
        val left = centeredRow().apply {
            add(chevron)
            icon?.let {
                add(
                    JBLabel(
                        if (dim) {
                            dimmed(it)
                        } else it
                    ).apply { name = "section-icon-${section.key}" })
            }
            add(JBLabel(section.title).apply {
                name = "section-title-${section.key}"; font = JBFont.label().asBold()
                if (dim) foreground = DesignColors.SECONDARY
            })
            if (section.game) {
                add(BadgeLabel.game())
            }
            if (section.classHint.isNotEmpty()) {
                add(JBLabel(section.classHint).apply {
                    name = "section-hint-${section.key}"; font =
                    monoFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
                    foreground = DesignColors.SECONDARY
                })
            }
        }
        val right = centeredRow().apply {
            actions.forEach { add(it) }
            enabledBox?.let { add(it) }
        }
        head.add(left, BorderLayout.WEST)
        head.add(right, BorderLayout.EAST)
        head.add(TitleRule().apply { name = "section-rule-${section.key}" }, BorderLayout.CENTER)
        val toggle = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = setCollapsed(!isCollapsed)
        }
        chevron.addMouseListener(toggle)
        add(head, BorderLayout.NORTH)
        val indented = DimPanel(BorderLayout()).apply {
            isOpaque = false
            // the labels start where the kind icon does: past the header's side padding, the chevron and the gap
            val indent = JBUI.scale(PanelMetrics.HEADER_SIDE + PanelMetrics.HEADER_GAP) + chevron.preferredSize.width
            border = BorderFactory.createEmptyBorder(0, indent, JBUI.scale(6), JBUI.scale(16))
            add(body, BorderLayout.CENTER)
            setDim(dim)
        }
        body.isVisible = !collapsed
        indented.isVisible = !collapsed
        this.indented = indented
        add(indented, BorderLayout.CENTER)
    }

    /** Collapses or expands the body; tells the listener so the state survives a refresh of the same node. */
    fun setCollapsed(collapsed: Boolean) {
        if (collapsed == isCollapsed) {
            return
        }
        isCollapsed = collapsed
        indented.isVisible = !collapsed
        body.isVisible = !collapsed
        chevron.icon = chevronIcon(collapsed)
        chevron.accessibleContext.accessibleName =
            AbyssusBundle.message(
                if (collapsed) {
                    "panelExpand"
                } else {
                    "panelCollapse"
                }, section.title
            )
        onCollapse(collapsed)
        revalidate()
        repaint()
    }

    private fun chevronIcon(collapsed: Boolean): Icon = if (collapsed) {
        AllIcons.General.ArrowRight
    } else AllIcons.General.ArrowDown
}

/** Hosts [view] in a scroll pane that follows the viewport's width, so a section never needs a horizontal scroll bar. */
class WidthTrackingPanel(view: JComponent) : JPanel(BorderLayout()), javax.swing.Scrollable {
    init {
        add(view, BorderLayout.CENTER)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int) =
        JBUI.scale(16)

    override fun getScrollableBlockIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int) =
        visibleRect.height

    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}
