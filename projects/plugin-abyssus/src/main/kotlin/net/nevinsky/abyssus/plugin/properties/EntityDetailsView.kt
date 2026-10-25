/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.document.RayDataEdit
import net.nevinsky.abyssus.lib.core.editor.document.RayDataError
import net.nevinsky.abyssus.lib.core.editor.document.RayOpticalField
import net.nevinsky.abyssus.lib.core.editor.meta.OpticalMaterialRow
import net.nevinsky.abyssus.lib.core.editor.meta.RenderOptics
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.panel.NoteRow
import net.nevinsky.abyssus.lib.core.editor.panel.ReadonlyRow
import net.nevinsky.abyssus.lib.core.editor.panel.Section
import net.nevinsky.abyssus.lib.core.editor.util.NumberTextStyle
import net.nevinsky.abyssus.lib.core.editor.util.numberText
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.filetype.SceneRayEdits
import net.nevinsky.abyssus.plugin.filetype.setEnabled
import net.nevinsky.abyssus.plugin.panel.RowHooks
import net.nevinsky.abyssus.plugin.projectView.addComponentGroup
import net.nevinsky.abyssus.plugin.projectView.reportRejection
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.sceneview.PathEditListener
import net.nevinsky.abyssus.plugin.sceneview.PathEditSession
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.ui.showActionGroup
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * An entity (or one component) of a scene with an editor for every field of the components the plugin models. Editors
 * are named `field-<Kind>-<field>` (their message `error-<Kind>-<field>`), a section's Remove button `remove-<Kind>` and
 * the Add button `add-component`, which is how tests reach them. A model's Render section also lists its materials'
 * Ray Tracing overrides: `optics-<field>-<material>` (message `optics-error-<material>`), `optics-unresolved-<material>`
 * and `optics-problem`.
 */
internal class EntityDetailsView @JvmOverloads constructor(
    private val project: Project,
    private val state: PanelState.EntityDetails,
    private val metaFiles: MetaFiles,
    private val collapsed: MutableMap<String, Boolean> = HashMap(),
) : JPanel(BorderLayout()) {
    private val target = state.target

    private val hooks = object : RowHooks {
        override fun commit(ref: FieldRef, text: String): CommitOutcome = commit(ref.kind, ref.field, text)
        override fun custom(key: String): JComponent? = null
        override fun openScreenEditor() {}
    }

    init {
        val model = state.model ?: error("The entity panel needs its model")
        val view = PanelModelView(
            model, hooks, collapsed, titleName = "entity-name",
            onEnabled = { section, checked -> toggle(section, checked) },
            sectionActions = { removeButton(it) },
            sectionExtra = { section ->
                when (section.kind) {
                    "RenderComponent" -> state.optics?.let { opticsView(it) }
                    PathArrangementComponent::class.simpleName -> pathEditBar()
                    else -> null
                }
            },
            after = if (target.kind == null) listOf(addButton()) else emptyList(),
        )
        // the raw JSON of a component the plugin does not edit is reached by `raw-<Kind>`
        view.sections.forEach { frame ->
            frame.section.kind?.let { kind ->
                allComponents(frame).firstOrNull { it.name == "raw" }?.name = "raw-$kind"
            }
        }
        add(view, BorderLayout.CENTER)
    }

    /**
     * Edit and Apply for the spline of a path arrangement: Edit shows the control points in the Scene view (a click on one
     * gives it the move gizmo), Apply ends the editing. The buttons follow the project's [PathEditSession].
     */
    private fun pathEditBar(): JComponent {
        val session = project.service<PathEditSession>()
        val edit = JButton(AbyssusBundle.message("arrangementEditSpline")).apply { name = "arrangement-edit"; isFocusable = false }
        val apply = JButton(AbyssusBundle.message("arrangementApplySpline")).apply { name = "arrangement-apply"; isFocusable = false }
        fun editing() = session.current?.let { it.file == target.file && it.entityId == target.entityId } == true
        fun sync() {
            edit.isEnabled = !editing()
            apply.isEnabled = editing()
        }
        edit.addActionListener { session.start(target.file, target.entityId) }
        apply.addActionListener { session.stop(target.file) }
        sync()
        return object : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))) {
            private var connection: com.intellij.util.messages.MessageBusConnection? = null

            override fun addNotify() {
                super.addNotify()
                sync()
                connection = project.messageBus.connect().also {
                    it.subscribe(PathEditListener.TOPIC, PathEditListener { sync() })
                }
            }

            override fun removeNotify() {
                connection?.disconnect()
                connection = null
                super.removeNotify()
            }
        }.apply {
            isOpaque = false
            add(edit)
            add(apply)
        }
    }

    private fun allComponents(c: java.awt.Component): List<java.awt.Component> =
        listOf(c) + ((c as? java.awt.Container)?.components?.flatMap { allComponents(it) } ?: emptyList())

    private fun secondary() = UIUtil.getContextHelpForeground()

    /** The Remove button of a modeled component's section; an unmodeled one is read only, and the name is not removable. */
    private fun removeButton(section: Section): List<JComponent> {
        val kind = section.kind ?: return emptyList()
        if (kind == NameComponent::class.simpleName) {
            return emptyList()
        }
        if (section.rows.any { it is ReadonlyRow && it.label.isEmpty() } || section.rows.any { it is NoteRow }) {
            return emptyList()
        }
        val label = AbyssusBundle.message("propertiesRemoveComponent")
        return listOf(object : JButton(AllIcons.Actions.GC) {
            /** Square, as wide as the button is high. */
            override fun getPreferredSize(): Dimension =
                super.getPreferredSize().let { Dimension(it.height, it.height) }
        }.apply {
            name = "remove-$kind"
            toolTipText = label
            accessibleContext.accessibleName = label
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            margin = JBUI.emptyInsets()
            addActionListener {
                reportRejection(project, SceneComponentEdits.remove(project, target.file, target.entityId, kind))
            }
        })
    }

    /** The `Enabled` box of a component section: one undoable edit that writes only the flag. */
    private fun toggle(section: Section, checked: Boolean) {
        val toggle = section.enable ?: return
        val component = toggle.target ?: return
        setEnabled(project, target.file, component, checked)
    }

    /** Transmission (in percent) and IOR per PBR material of the model, as overrides of this entity only. */
    private fun opticsView(optics: RenderOptics): JComponent = JPanel(VerticalLayout(JBUI.scale(2))).apply {
        add(JBLabel(AbyssusBundle.message("propertiesOptics")).apply {
            foreground = secondary(); font = JBFont.small().asBold(); border = JBUI.Borders.empty(8, 0, 2, 0)
        })
        add(JBLabel("<html>${AbyssusBundle.message("propertiesOpticsNote")} ${AbyssusBundle.message("propertiesOpticsRange")}</html>").apply {
            foreground = secondary(); font = JBFont.small(); name = "optics-note"
        })
        optics.problem?.let {
            add(JBLabel("<html>$it</html>").apply {
                foreground = JBColor.RED; name = "optics-problem"
            })
        }
        for (row in optics.materials) add(opticsRow(optics, row))
        for (id in optics.unresolved) add(
            JBLabel(
                "<html>${
                    AbyssusBundle.message(
                        "propertiesOpticsUnresolved",
                        id
                    )
                }</html>"
            ).apply {
                foreground = JBColor.RED; name = "optics-unresolved-$id"
            })
    }

    private fun opticsRow(optics: RenderOptics, row: OpticalMaterialRow): JComponent {
        val box = JPanel(VerticalLayout(JBUI.scale(2))).apply { border = JBUI.Borders.emptyTop(4) }
        box.add(JBLabel(row.id ?: AbyssusBundle.message("propertiesOpticsUnnamed")).apply {
            font = JBFont.label().asBold()
        })
        val id = row.id
        if (id == null || row.error != null) {
            box.add(JBLabel("<html>${EditorBundle.message("propertiesRayError${(row.error ?: RayDataError.MATERIAL_ID).name}")}</html>").apply {
                foreground = secondary(); name = "optics-error-${id ?: ""}"
            })
            return box
        }
        val error =
            JBLabel(row.errors.values.firstOrNull()?.let { EditorBundle.message("propertiesRayError${it.name}") }
                ?: "").apply {
                foreground = JBColor.RED; name = "optics-error-$id"
            }
        for (field in RayOpticalField.entries) {
            val stored = row.stored[field]
            val shown = stored?.takeIf { it.isNumber }?.doubleValue() ?: field.default
            val text =
                if (field == RayOpticalField.TRANSMISSION) percent(shown) else stored?.takeIf { it.isNumber }?.asText()
                    ?: number(shown)
            val editor = JBTextField(text).apply { name = "optics-${field.key}-$id"; columns = 6 }
            val save = {
                if (editor.text.trim() != text) {
                    // Transmission is typed in percent; the scene stores the 0..1 fraction
                    val value = if (field == RayOpticalField.TRANSMISSION) editor.text.trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() }?.let { number(it / 100) } else editor.text
                    val result = if (value == null) RayDataEdit.Rejected(RayDataError.NUMBER)
                    else SceneRayEdits.material(
                        project,
                        target.file,
                        target.entityId,
                        id,
                        field,
                        stored,
                        value,
                        optics.identities
                    )
                    when (result) {
                        RayDataEdit.Changed -> error.text = ""
                        RayDataEdit.Unchanged -> {
                            error.text = ""; editor.text = text
                        }

                        RayDataEdit.Conflict -> {
                            error.text = AbyssusBundle.message("propertiesRayConflict"); editor.text = text
                        }

                        is RayDataEdit.Rejected -> {
                            error.text = EditorBundle.message("propertiesRayError${result.error.name}"); editor.text =
                                text
                        }
                    }
                }
            }
            editor.addActionListener { save() }
            editor.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = save()
            })
            val label =
                JBLabel(AbyssusBundle.message(if (field == RayOpticalField.TRANSMISSION) "propertiesOpticsTransmission" else "propertiesOpticsIor")).apply {
                    preferredSize = Dimension(JBUI.scale(170), preferredSize.height)
                }
            box.add(JPanel(GridBagLayout()).apply {
                add(label, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
                add(
                    editor,
                    GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            })
        }
        box.add(error)
        return box
    }

    /** [fraction] as a percentage without float noise (0.07 shows as 7, not 7.000000000000001). */
    private fun percent(fraction: Double) = number(fraction * 100)

    private fun number(value: Double): String =
        numberText(value, NumberTextStyle.DETAIL)

    /** Saves [text] for field [field] of component [kind]; the editor shows a refusal and puts the stored value back. */
    private fun commit(kind: String, field: String, text: String): CommitOutcome =
        when (val result =
            SceneComponentEdits.update(project, target.file, target.entityId, kind, field, text, metaFiles)) {
            is EditResult.Rejected -> CommitOutcome.Refused(result.reason)
            EditResult.Unchanged -> CommitOutcome.Unchanged
            EditResult.Changed -> CommitOutcome.Saved
        }

    private fun addButton(): JComponent = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(12, 16)
        add(JButton(AbyssusBundle.message("propertiesAddComponent")).apply {
            name = "add-component"
            isEnabled = state.addable.isNotEmpty()
            toolTipText = if (state.addable.isEmpty()) AbyssusBundle.message("propertiesNothingToAdd") else null
            addActionListener {
                val group = addComponentGroup(project, target.file, target.entityId, state.addable, metaFiles)
                showActionGroup(DataManager.getInstance().getDataContext(this), group, null, underneath = this)
            }
        }, BorderLayout.WEST)
    }
}
