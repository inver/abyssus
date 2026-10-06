/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.openapi.project.DumbAware
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewRenderer
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.ui.SimpleTextAttributes
import javax.swing.tree.TreeCellRenderer
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ui.tree.TreeVisitor
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.filetype.EyeIcons
import net.nevinsky.abyssus.filetype.SceneIcons
import net.nevinsky.abyssus.filetype.SceneViewIcons
import net.nevinsky.abyssus.sceneview.openSceneView
import javax.swing.ToolTipManager
import com.intellij.ui.render.RenderingUtil
import java.awt.Color
import java.awt.Cursor
import java.util.function.Supplier
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import java.awt.BorderLayout
import javax.swing.tree.DefaultTreeModel
import net.nevinsky.abyssus.filetype.AbyssusSceneEdited
import com.intellij.openapi.components.service
import net.nevinsky.abyssus.AbyssusCore

// Platform project-view implementation APIs supply its native selection, toolbar and tree lifecycle.
// Keep that integration here; the public ViewSettings/node contracts do not construct a custom pane.
class AbyssusProjectViewPane(project: Project) : ProjectViewPane(project),
    net.nevinsky.abyssus.sceneview.SceneViewHost by ProjectSceneViewHost(project) {
    init {
        // a plugin edit of a scene or project file changes what the tree shows (names, toggles, counts)
        project.messageBus.connect(this).subscribe(AbyssusSceneEdited.TOPIC, AbyssusSceneEdited { updateFromRoot(true) })
    }

    override fun getTitle(): String = AbyssusBundle.message("abyssusViewName")

    override fun getId(): String = ID

    override fun getIcon(): Icon = SceneIcons.FILE

    override fun getWeight(): Int = WEIGHT

    override fun createStructure(): ProjectAbstractTreeStructureBase = object : ProjectTreeStructure(myProject, ID) {
        override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
            AbyssusRootNode(project, settings)

        override fun isToBuildChildrenInBackground(element: Any) = true
    }

    // The platform requires the target's minor view id to equal the pane id; the inherited one is "ProjectPane".
    override fun createSelectInTarget(): SelectInTarget = AbyssusSelectInTarget(myProject)

    private var wrapped: JComponent? = null

    /** The platform's tree component with the counts footer under it; the platform caches its own component, so the wrapper is cached too. */
    override fun createComponent(): JComponent {
        val inner = super.createComponent()
        wrapped?.let { return it }
        return JPanel(BorderLayout()).also {
            it.add(inner, BorderLayout.CENTER)
            it.add(AbyssusFooter(myProject, this), BorderLayout.SOUTH)
            wrapped = it
        }
    }

    override fun addToolbarActions(actionGroup: DefaultActionGroup) {
        super.addToolbarActions(actionGroup)
        actionGroup.add(UnusedFilterAction())
    }

    override fun createTree(treeModel: DefaultTreeModel): ProjectViewTree =
        EyeTree(treeModel, myProject).also { publishSelectionOf(it, myProject) }

    companion object {
        const val ID = "Abyssus"
        const val WEIGHT = 100
    }
}

/** Publishes the user object of [tree]'s selected node (null when nothing is selected) as the Abyssus selection of [project]. */
internal fun publishSelectionOf(tree: JTree, project: Project) {
    tree.addTreeSelectionListener { e ->
        AbyssusSelection.of(project).select(e.newLeadSelectionPath?.let { TreeUtil.getUserObject(it.lastPathComponent) })
    }
}

/**
 * The platform repaints every fragment of a selected, focused row in the selection foreground, which
 * hides the gray of a disabled parameter exactly while it is selected (e.g. right after its eye is
 * clicked). Keep the gray for those fragments.
 */
private class GrayKeepingRenderer : ProjectViewRenderer() {
    override fun append(fragment: String, attributes: SimpleTextAttributes, isMainText: Boolean) {
        if (mySelected && attributes.fgColor == SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor) {
            mySelected = false
            try {
                super.append(fragment, attributes, isMainText)
            } finally {
                mySelected = true
            }
        } else {
            super.append(fragment, attributes, isMainText)
        }
    }
}

/** "Select in Abyssus view": selects the current file in this pane. */
private class AbyssusSelectInTarget(project: Project) : ProjectViewSelectInTarget(project), DumbAware {
    override fun toString(): String = AbyssusProjectViewPane.ID

    override fun getMinorViewId(): String = AbyssusProjectViewPane.ID

    override fun getWeight(): Float = AbyssusProjectViewPane.WEIGHT.toFloat()
}

/**
 * Paints clickable icons at the right edge of rows that have actions: the eye on entries gated by an `xxxEnabled`
 * toggle, "View" on scenes, and "..." left of the eye on a project scene's skybox.
 */
internal class EyeTree(model: DefaultTreeModel, private val project: Project) : ProjectViewTree(model) {
    override fun createCellRenderer(): TreeCellRenderer = GrayKeepingRenderer()

    /** The row's actions, rightmost first. */
    private fun actionsFor(row: Int): List<RowAction> {
        val node = TreeUtil.getUserObject(getPathForRow(row)?.lastPathComponent)
        viewableSceneFile(node)?.let { file ->
            return listOf(IconAction(SceneViewIcons.VIEW, AbyssusBundle.message("viewSceneTooltip")) { openSceneView(project, file) })
        }
        val entry = (node as? DtoEntryNode)?.value ?: return emptyList()
        return listOfNotNull(eyeAction(entry), skyboxAction(entry), unusedBadgeFor(entry))
    }

    private fun eyeAction(entry: DtoEntry): RowAction? {
        if (entry.enabled == null) return null
        val icon = if (entry.enabled) EyeIcons.ON else EyeIcons.OFF
        return IconAction(icon, null) { r ->
            val wasExpanded = isExpanded(getPathForRow(r))
            if (toggleEnabled(project, entry)) reselect(entry.path, wasExpanded)
        }
    }

    private fun skyboxAction(entry: DtoEntry): RowAction? {
        val abss = skyboxProjectOf(entry) ?: return null
        return ChooseButton(AbyssusBundle.message("skyboxChooserTooltip")) {
            val assets = service<AbyssusCore>().assets
            if (chooseSkybox(project, entry, abss, assets.metaFiles, assets.hdrPreviews)) reselect(entry.path, false)
        }
    }

    /** Where [actions] of [row] sit: from the right edge of the visible area, rightmost first, [ACTION_GAP] apart. */
    private fun actionBounds(row: Int, actions: List<RowAction>): List<Rectangle>? {
        val rowBounds = getRowBounds(row) ?: return null
        return layoutActions(rowBounds, visibleRect.let { it.x + it.width }, actions, this)
    }

    private fun rowOf(e: MouseEvent): Int? {
        val row = getClosestRowForLocation(e.x, e.y)
        return row.takeIf { it >= 0 && getRowBounds(it)?.let { b -> e.y in b.y until b.y + b.height } == true }
    }

    private fun actionAt(e: MouseEvent): Pair<Int, RowAction>? {
        val row = rowOf(e) ?: return null
        val actions = actionsFor(row)
        val bounds = actionBounds(row, actions) ?: return null
        return actions.indices.firstOrNull { actions[it].run != null && bounds[it].contains(e.point) }?.let { row to actions[it] }
    }

    init {
        // the design's selection highlight; the platform paints it (rounded in the new UI) with this colour
        // Internal rendering key: Swing's selection color does not override the platform renderer's row background.
        // This exact use is the project-tree finding in the recorded verifier baseline (design D9).
        putClientProperty(RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier<Color> { DesignColors.SELECTION })
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                val (row, action) = actionAt(e) ?: return
                e.consume()
                action.run?.invoke(row)
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                cursor = if (actionAt(e) != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        })
    }

    override fun getToolTipText(event: MouseEvent): String? = actionAt(event)?.second?.tooltip ?: super.getToolTipText(event)

    /**
     * A toggled row changes identity (see [DtoEntry.equals]), so the refresh drops the selection onto
     * its parent; select the row with the same entry path again and restore its expansion.
     */
    private fun reselect(entryPath: String, expand: Boolean) {
        val visitor = TreeVisitor { path ->
            val entry = (TreeUtil.getUserObject(path.lastPathComponent) as? DtoEntryNode)?.value
            val asset = (TreeUtil.getUserObject(path.lastPathComponent) as? AbyssusAssetNode)?.virtualFile?.path
            when {
                entry?.path == entryPath -> TreeVisitor.Action.INTERRUPT
                entry != null && entryPath.startsWith(entry.path + "/") -> TreeVisitor.Action.CONTINUE
                asset != null && entryPath.startsWith("$asset/") -> TreeVisitor.Action.CONTINUE
                entry == null && asset == null -> TreeVisitor.Action.CONTINUE // view root
                else -> TreeVisitor.Action.SKIP_CHILDREN
            }
        }
        TreeUtil.promiseSelect(this, visitor).onSuccess { path ->
            if (expand) com.intellij.openapi.application.invokeLater { expandPath(path) }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val clip = g.clipBounds ?: return
        val first = getClosestRowForLocation(0, clip.y)
        val last = getClosestRowForLocation(0, clip.y + clip.height)
        if (first < 0) return
        for (row in first..last) {
            val actions = actionsFor(row)
            val bounds = actionBounds(row, actions) ?: continue
            val g2 = g.create() as Graphics2D
            try {
                actions.forEachIndexed { i, action -> action.paint(g2, bounds[i], this) }
            } finally {
                g2.dispose()
            }
        }
    }

}
