/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import net.nevinsky.abyssus.lib.core.util.numberTextOrNull
import net.nevinsky.abyssus.lib.core.util.numberOrNull
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent
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
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.editor.panel.FieldRef
import net.nevinsky.abyssus.lib.core.editor.panel.NoteRow
import net.nevinsky.abyssus.lib.core.editor.panel.ReadonlyRow
import net.nevinsky.abyssus.lib.core.editor.panel.Section
import net.nevinsky.abyssus.lib.core.editor.util.NumberTextStyle
import net.nevinsky.abyssus.lib.core.editor.util.numberText
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.dto.MetaFiles
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache
import net.nevinsky.abyssus.plugin.filetype.setEnabled
import net.nevinsky.abyssus.plugin.panel.RowHooks
import net.nevinsky.abyssus.plugin.projectView.addComponentGroup
import net.nevinsky.abyssus.plugin.ui.reportRejection
import net.nevinsky.abyssus.plugin.projectView.scene.SceneComponentEdits
import net.nevinsky.abyssus.plugin.sceneview.PathEditListener
import net.nevinsky.abyssus.plugin.sceneview.BrushRequestListener
import net.nevinsky.abyssus.plugin.sceneview.PaintTextureRequests
import net.nevinsky.abyssus.plugin.sceneview.SculptRequests
import net.nevinsky.abyssus.plugin.sceneview.PathEditSession
import net.nevinsky.abyssus.plugin.ui.CommitOutcome
import net.nevinsky.abyssus.plugin.ui.showActionGroup
import com.intellij.util.messages.MessageBusConnection
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * An entity (or one component) of a scene with an editor for every field of the components the plugin models. Editors
 * are named `field-<Kind>-<field>` (their message `error-<Kind>-<field>`), a section's Remove button `remove-<Kind>` and
 * the Add button `add-component`, which is how tests reach them..
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
                    "RenderComponent" -> if (isTerrain()) sculptBar() else null
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

    /** Whether the entity renders a terrain asset, which can be sculpted. */
    private fun isTerrain(): Boolean {
        val scene = SceneDocumentCache.of(project).read(target.file)?.scene ?: return false
        return scene.component(target.entityId, RenderComponent::class.java)?.type == MetaType.TERRAIN
    }

    /**
     * The Sculpt Terrain and Paint Texture toggles: each puts its brush on the terrain in the Scene view; the project's
     * [SculptRequests] and [PaintTextureRequests] follow them. Turning one on ends the other (the view holds one brush).
     */
    private fun sculptBar(): JComponent {
        val sculpt = project.service<SculptRequests>()
        val paint = project.service<PaintTextureRequests>()
        val sculptToggle = JToggleButton(AbyssusBundle.message("sculptTitle")).apply { name = "sculpt-terrain"; isFocusable = false }
        val paintToggle = JToggleButton(AbyssusBundle.message("paintTextureToggle")).apply { name = "paint-texture"; isFocusable = false }
        fun here(file: com.intellij.openapi.vfs.VirtualFile?, entity: Long?) = file == target.file && entity == target.entityId
        var syncing = false
        fun sync() {
            syncing = true
            sculptToggle.isSelected = sculpt.current?.let { here(it.file, it.entityId) } == true
            paintToggle.isSelected = paint.current?.let { here(it.file, it.entityId) } == true
            syncing = false
        }
        sculptToggle.addActionListener {
            if (syncing) return@addActionListener
            if (sculptToggle.isSelected) { paint.stop(target.file); sculpt.start(target.file, target.entityId) } else sculpt.stop(target.file)
        }
        paintToggle.addActionListener {
            if (syncing) return@addActionListener
            if (paintToggle.isSelected) { sculpt.stop(target.file); paint.start(target.file, target.entityId) } else paint.stop(target.file)
        }
        sync()
        return syncedBar(::sync) { connection ->
            connection.subscribe(SculptRequests.TOPIC, BrushRequestListener { sync() })
            connection.subscribe(PaintTextureRequests.TOPIC, BrushRequestListener { sync() })
        }.apply {
            isOpaque = false
            add(sculptToggle)
            add(paintToggle)
        }
    }

    /** A button bar that runs [sync] and calls [subscribe] with a project bus connection while it is on screen. */
    private fun syncedBar(sync: () -> Unit, subscribe: (MessageBusConnection) -> Unit): JPanel =
        object : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))) {
            private var connection: MessageBusConnection? = null

            override fun addNotify() {
                super.addNotify()
                sync()
                connection = project.messageBus.connect().also(subscribe)
            }

            override fun removeNotify() {
                connection?.disconnect()
                connection = null
                super.removeNotify()
            }
        }

    /**
     * Edit for the spline of a path arrangement shows the control points in the Scene view (a click on one gives it the
     * move gizmo); while it is on, Drop path to terrain, Close (drops the changes) and Apply (writes them) replace Edit.
     * The buttons follow the project's [PathEditSession].
     */
    private fun pathEditBar(): JComponent {
        val session = project.service<PathEditSession>()
        val edit = JButton(AbyssusBundle.message("arrangementEditSpline")).apply { name = "arrangement-edit"; isFocusable = false }
        val drop = JButton(AbyssusBundle.message("arrangementDropSpline")).apply { name = "arrangement-drop"; isFocusable = false }
        val close = JButton(AbyssusBundle.message("arrangementCloseSpline")).apply { name = "arrangement-close"; isFocusable = false }
        val apply = JButton(AbyssusBundle.message("arrangementApplySpline")).apply { name = "arrangement-apply"; isFocusable = false }
        fun editing() = session.current?.let { it.file == target.file && it.entityId == target.entityId } == true
        fun sync() {
            val on = editing()
            edit.isVisible = !on
            drop.isVisible = on
            close.isVisible = on
            apply.isVisible = on
        }
        edit.addActionListener { session.start(target.file, target.entityId) }
        drop.addActionListener { session.drop(target.file) }
        close.addActionListener { session.stop(target.file) }
        apply.addActionListener { session.apply(target.file) }
        sync()
        return syncedBar(::sync) { connection ->
            connection.subscribe(PathEditListener.TOPIC, PathEditListener { sync() })
        }.apply {
            isOpaque = false
            add(edit)
            add(drop)
            add(close)
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

// G7: the Ray Tracing optical overrides panel (opticsView) is disabled; overrides are no longer saved in scenes.

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
