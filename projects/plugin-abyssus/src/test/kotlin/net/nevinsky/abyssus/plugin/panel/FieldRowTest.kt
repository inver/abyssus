/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import net.nevinsky.abyssus.plugin.ui.CommitOutcome
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

/** The row kinds of a section: label column, field height, units, axes, dim defaults, commit through the hook. */
class FieldRowTest : BasePlatformTestCase() {
    private val saved = mutableListOf<Pair<FieldRef, String>>()
    private var screenEditorOpened = false

    private val hooks = object : RowHooks {
        override fun commit(ref: FieldRef, text: String): CommitOutcome {
            saved += ref to text
            return CommitOutcome.Saved
        }

        override fun custom(key: String): JComponent? = JBLabel("custom-$key").apply { name = "custom-$key" }
        override fun openScreenEditor() { screenEditorOpened = true }
    }

    private fun all(c: Component): List<Component> = allComponents(c)

    private inline fun <reified T : Component> find(root: Component, name: String): T = findNamed(root, name)

    private fun row(row: Row): JComponent = rowComponent(row, hooks)

    // ---- field row kinds ----

    fun testNumberWithUnitShowsTheUnitAfterTheField() {
        val view = row(NumberRow("Mass", "0.5", "kg", false, FieldRef("RigidBodyComponent", "mass")))
        assertEquals("kg", find<JBLabel>(view, "unit").text)
        val field = find<JBTextField>(view, "field-RigidBodyComponent-mass")
        assertEquals("0.5", field.text)
        assertEquals(field.minimumSize.height, field.preferredSize.height)
        assertEquals(JBUI.scale(120), field.preferredSize.width) // INPUT_WIDTH: a component's inputs share one width
    }

    fun testLabelColumnIs150Pixels() {
        val view = row(TextRow("name", "Racer", false, FieldRef("NameComponent", "name")))
        val label = find<JBLabel>(view, "label-NameComponent-name")
        assertEquals(JBUI.scale(PanelMetrics.LABEL_COLUMN), label.preferredSize.width)
    }

    fun testVectorHasOneLabelledBoxPerAxis() {
        val axes = listOf("x", "y", "z", "w").map { Axis(it, "1", FieldRef("PositionComponent", "localRotation.$it")) }
        val view = row(VectorRow("localRotation", axes))
        assertEquals(listOf("x", "y", "z", "w"), all(view).filterIsInstance<JBLabel>().filter { it.name?.startsWith("axis-label-") == true }.map { it.text })
        assertEquals(4, all(view).filterIsInstance<JBTextField>().size)
        find<JBTextField>(view, "field-PositionComponent-localRotation.w").apply { text = "0.5" }
    }

    fun testAxisBoxesAreAsTallAsTextFields() {
        val text = find<JBTextField>(row(TextRow("name", "Racer", false, FieldRef("NameComponent", "name"))), "field-NameComponent-name")
        val view = row(VectorRow("pos", listOf(Axis("x", "1", FieldRef("PositionComponent", "localPosition.x")))))
        val box = find<JBTextField>(view, "field-PositionComponent-localPosition.x").parent as JComponent
        assertEquals(text.preferredSize.height, box.preferredSize.height)
    }

    fun testEditingAFieldCommitsThroughTheHook() {
        val view = row(TextRow("name", "Racer", false, FieldRef("NameComponent", "name")))
        val field = find<JBTextField>(view, "field-NameComponent-name")
        field.text = "Pilot"
        field.postActionEvent()
        assertEquals(listOf(FieldRef("NameComponent", "name") to "Pilot"), saved)
    }

    fun testADimRowIsDrawnInTheSecondaryColorAndStaysEditable() {
        val dim = find<JBTextField>(row(NumberRow("Friction", "0.2", "", true, FieldRef("RigidBodyComponent", "friction"))), "field-RigidBodyComponent-friction")
        val normal = find<JBTextField>(row(NumberRow("Mass", "0.5", "", false, FieldRef("RigidBodyComponent", "mass"))), "field-RigidBodyComponent-mass")
        assertEquals(DesignColors.SECONDARY, dim.foreground)
        assertNotSame(DesignColors.SECONDARY, normal.foreground)
        assertTrue(dim.isEditable && dim.isEnabled)
    }

    fun testChoiceCheckAndColorRows() {
        val choice = find<ComboBox<*>>(row(ChoiceRow("Shape", "SPHERE", listOf("SPHERE", "BOX"), false, FieldRef("ColliderComponent", "shape"))), "field-ColliderComponent-shape")
        assertEquals("SPHERE", choice.selectedItem)
        val check = find<JBCheckBox>(row(CheckRow("alignToPath", true, "", false, FieldRef("PathArrangementComponent", "alignToPath"))), "field-PathArrangementComponent-alignToPath")
        assertTrue(check.isSelected)
        val color = row(ColorRow("color", "#FFF5E0", false, null, listOf(Axis("r", "1", FieldRef("LightComponent", "color.r")))))
        assertEquals(Color.decode("#FFF5E0"), find<JPanel>(color, "swatch").background)
        assertEquals("#FFF5E0", find<JBLabel>(color, "hex").text)
    }

    fun testAssetReferenceHasAnIconAndA26PixelBrowseButton() {
        val view = row(AssetRow("assetName", "model_racer", "model", true, listOf("model_racer", "model_pilot"), false, FieldRef("RenderComponent", "assetName")))
        val browse = find<JButton>(view, "browse-RenderComponent-assetName")
        assertEquals(JBUI.scale(PanelMetrics.BROWSE), browse.preferredSize.width)
        assertEquals(JBUI.scale(PanelMetrics.BROWSE), browse.preferredSize.height)
        assertEquals("Browse", browse.accessibleContext.accessibleName)
        assertEquals("model_racer", find<ComboBox<*>>(view, "field-RenderComponent-assetName").selectedItem)
    }

    fun testAReadOnlyAssetReferenceHasNoCombo() {
        val view = row(AssetRow("splatBase", "texture_grass", "texture"))
        assertTrue(all(view).none { it is ComboBox<*> })
        assertTrue(all(view).filterIsInstance<JBLabel>().any { it.text == "texture_grass" })
    }

    fun testReadonlyLinkNoteSubHeadingAndTable() {
        assertTrue(all(row(ReadonlyRow("version", "1"))).filterIsInstance<JBLabel>().any { it.text == "1" })
        val link = row(LinkRow("Open in Screen Editor", LinkTarget.SCREEN_EDITOR))
        all(link).filterIsInstance<com.intellij.ui.components.ActionLink>().single().doClick()
        assertTrue(screenEditorOpened)
        assertTrue(all(row(NoteRow("hello"))).filterIsInstance<JBLabel>().any { it.text.contains("hello") })
        assertEquals("Layers (6)", find<JBLabel>(row(SubHeadingRow("Layers (6)")), "group-Layers (6)").text)
        val table = row(TableRow(listOf("id", "kind"), listOf(listOf("1", "OBJECT"), listOf("2", "DETAIL"))))
        assertEquals(1, all(table).count { it.name == "table-header" })
        assertEquals(2, all(table).count { it.name == "table-row" })
    }

    fun testACustomRowHostsTheExistingEditor() {
        assertEquals("custom-terrain", find<JBLabel>(row(CustomRow("terrain")), "custom-terrain").name)
    }
}
