/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.editor.components.EnabledTarget
import net.nevinsky.abyssus.lib.core.editor.panel.AssetRow
import net.nevinsky.abyssus.lib.core.editor.panel.Axis
import net.nevinsky.abyssus.lib.core.editor.panel.Badge
import net.nevinsky.abyssus.lib.core.editor.panel.BadgeKind
import net.nevinsky.abyssus.lib.core.editor.panel.CheckRow
import net.nevinsky.abyssus.lib.core.editor.panel.ChoiceRow
import net.nevinsky.abyssus.lib.core.editor.panel.ColorRow
import net.nevinsky.abyssus.lib.core.editor.panel.CustomRow
import net.nevinsky.abyssus.lib.core.editor.panel.EnableToggle
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.panel.Header
import net.nevinsky.abyssus.lib.core.editor.panel.LinkRow
import net.nevinsky.abyssus.lib.core.editor.panel.LinkTarget
import net.nevinsky.abyssus.lib.core.editor.panel.NoteRow
import net.nevinsky.abyssus.lib.core.editor.panel.NumberRow
import net.nevinsky.abyssus.lib.core.editor.panel.ReadonlyRow
import net.nevinsky.abyssus.lib.core.editor.panel.Row
import net.nevinsky.abyssus.lib.core.editor.panel.Section
import net.nevinsky.abyssus.lib.core.editor.panel.SubHeadingRow
import net.nevinsky.abyssus.lib.core.editor.panel.TableRow
import net.nevinsky.abyssus.lib.core.editor.panel.TextRow
import net.nevinsky.abyssus.lib.core.editor.panel.VectorRow
import net.nevinsky.abyssus.plugin.projectView.DesignColors
import java.awt.Color
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/** The section frame, the header and the color pairs. */
class PropertySectionTest : BasePlatformTestCase() {
    private fun all(c: Component): List<Component> = allComponents(c)

    private inline fun <reified T : Component> find(root: Component, name: String): T = findNamed(root, name)

    // ---- section frame ----

    private fun section(game: Boolean = false, enable: EnableToggle? = null, disabled: Boolean = false) = Section(
        "3.Collider", "Collider", "collider", emptyList(), "ColliderComponent", game, "ColliderComponent", enable, disabled,
    )

    private fun frame(model: Section, body: JComponent = JBLabel("body"), collapsed: Boolean = false,
                      onCollapse: (Boolean) -> Unit = {}, onEnabled: ((Boolean) -> Unit)? = null) =
        PropertySection(model, panelIcon("collider"), body, collapsed, onCollapse, onEnabled)

    fun testASectionCollapsesAndExpandsAndReportsIt() {
        val states = mutableListOf<Boolean>()
        val body = JBLabel("body")
        val section = frame(section(), body, onCollapse = { states += it })
        assertTrue(body.isVisible)
        section.setCollapsed(true)
        assertFalse(body.isVisible)
        section.setCollapsed(false)
        assertTrue(body.isVisible)
        assertEquals(listOf(true, false), states)
    }

    fun testASectionStartsCollapsedWhenItWasLeftCollapsed() {
        val body = JBLabel("body")
        val section = frame(section(), body, collapsed = true)
        assertFalse(body.isVisible)
        assertTrue(section.isCollapsed)
    }

    fun testAnAccessibleNameDescribesTheChevronAndTheCheckBox() {
        val section = frame(section(enable = EnableToggle(true, EnabledTarget.Component("3", "ColliderComponent"))))
        assertEquals("Collapse Collider", find<JBLabel>(section, "section-chevron-3.Collider").accessibleContext.accessibleName)
        assertEquals("Enable Collider", section.enabledBox!!.accessibleContext.accessibleName)
        section.setCollapsed(true)
        assertEquals("Expand Collider", find<JBLabel>(section, "section-chevron-3.Collider").accessibleContext.accessibleName)
    }

    fun testTheEnabledBoxReflectsTheFlagAndReportsAChange() {
        val changes = mutableListOf<Boolean>()
        val section = frame(section(enable = EnableToggle(false, EnabledTarget.Component("3", "ColliderComponent")), disabled = true), onEnabled = { changes += it })
        assertFalse(section.enabledBox!!.isSelected)
        section.enabledBox.doClick()
        assertEquals(listOf(true), changes)
    }

    fun testASectionWithoutAFlagHasNoCheckBox() {
        assertNull(frame(section()).enabledBox)
    }

    fun testAGameSectionShowsTheBadgeAndTheClassHint() {
        val section = frame(section(game = true))
        assertEquals("game", find<BadgeLabel>(section, "badge-game").text)
        assertEquals("ColliderComponent", find<JBLabel>(section, "section-hint-3.Collider").text)
        assertTrue(all(frame(section(game = false))).none { it.name == "badge-game" })
    }

    fun testADisabledSectionDimsItsTitleButKeepsTheBodyEditable() {
        val field = JBTextField("1")
        val section = frame(section(disabled = true), field)
        assertEquals(DesignColors.SECONDARY, find<JBLabel>(section, "section-title-3.Collider").foreground)
        assertTrue(field.isEnabled && field.isEditable)
    }

    fun testTheSectionHeaderIs34PixelsHigh() {
        val head = (frame(section()).layout as java.awt.BorderLayout).getLayoutComponent(java.awt.BorderLayout.NORTH)
        assertEquals(JBUI.scale(PanelMetrics.SECTION_HEADER), head.preferredSize.height)
    }

    // ---- header ----

    fun testTheHeaderShowsBreadcrumbTitleSubtitleAndBadges() {
        val header = Header(
            listOf("ControlLine.abss", "Field", "ecs"), "object", "Racer", "Entity 3 · OBJECT",
            listOf(Badge(BadgeKind.READ_ONLY, "read-only"), Badge(BadgeKind.UNUSED, "no scene uses this")),
            note = "Edits change every instance that uses this asset.",
        )
        val view = HeaderPanel(header, panelIcon("object"))
        assertEquals("ControlLine.abss  ›  Field  ›  ecs", find<JBLabel>(view, "header-breadcrumb").text)
        assertEquals("Racer", find<JBLabel>(view, "header-title").text)
        assertEquals("Entity 3 · OBJECT", find<JBLabel>(view, "header-subtitle").text)
        assertEquals("read-only", find<BadgeLabel>(view, "badge-read-only").text)
        assertEquals("no scene uses this", find<BadgeLabel>(view, "badge-unused").text)
        assertEquals("Edits change every instance that uses this asset.", find<JBLabel>(view, "shared-asset-note").text)
    }

    fun testAnEmptyBreadcrumbIsLeftOut() {
        val view = HeaderPanel(Header(emptyList(), "project", "P.abss", "Project · P.abss"), panelIcon("project"))
        assertTrue(all(view).none { it.name == "header-breadcrumb" })
    }

    // ---- colors ----

    private fun luminance(c: Color): Double {
        fun channel(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { x -> luminance(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun rgb(name: String, dark: Boolean): Color =
        DesignColors.PAIRS.getValue(name).let { Color(if (dark) it.second else it.first) }

    fun testLightColorPairsHaveFourAndAHalfToOneContrast() {
        val panel = Color(0xF7F8FA)
        val field = Color(0xFFFFFF)
        for (name in listOf("secondary", "amber", "game", "blue")) for (background in listOf(panel, field)) {
            val value = contrast(rgb(name, dark = false), background)
            assertTrue("$name on #${Integer.toHexString(background.rgb)}: $value", value >= 4.5)
        }
    }

    fun testDarkColorPairsAreTheBoardsAndAtLeastThreeToOneOnTheSurface() {
        // the board's dim value (#868a91) is 3.9:1 on its #2b2d30 field; it is kept as drawn, so the floor here is 3:1
        val surface = rgb("surface", dark = true)
        for (name in listOf("secondary", "amber", "game", "blue")) {
            val value = contrast(rgb(name, dark = true), surface)
            assertTrue("$name: $value", value >= 3.0)
        }
        assertEquals(Color(0x868A91), rgb("secondary", dark = true))
        assertEquals(Color(0x4E5157), rgb("border", dark = true))
        assertEquals(Color(0x2B2D30), surface)
        assertEquals(Color(0xF47FB0), rgb("game", dark = true))
        assertEquals(Color(0xE0A458), rgb("amber", dark = true))
    }
}
