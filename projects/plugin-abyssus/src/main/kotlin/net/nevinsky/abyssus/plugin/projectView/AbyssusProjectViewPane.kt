/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.*
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.RelativeFont
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.filetype.EyeIcons
import net.nevinsky.abyssus.plugin.filetype.SceneIcons
import net.nevinsky.abyssus.plugin.filetype.SceneViewIcons
import net.nevinsky.abyssus.plugin.projectView.action.asset.AssetDropdownAction
import net.nevinsky.abyssus.plugin.projectView.scene.SceneDropdownAction
import net.nevinsky.abyssus.plugin.projectView.scene.ScenesDropdownAction
import net.nevinsky.abyssus.plugin.projectView.action.ui.UiDropdownAction
import net.nevinsky.abyssus.plugin.sceneview.SceneViewHost
import net.nevinsky.abyssus.plugin.sceneview.openSceneView
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

// Platform project-view implementation APIs supply its native selection, toolbar and tree lifecycle.
// Keep that integration here; the public ViewSettings/node contracts do not construct a custom pane.
class AbyssusProjectViewPane(project: Project) : ProjectViewPane(project),
    SceneViewHost by ProjectSceneViewHost(project) {
    // Rebuilds the tree after plugin edits, document changes (Undo/Redo, unsaved text) and VFS changes, keeping
    // selection and expansion.
    internal val refresh =
        AbyssusTreeRefresh(project, { tree }, { done -> updateFromRoot(true).doWhenProcessed { done() } }, this)

    override fun getTitle(): String = AbyssusBundle.message("abyssusViewName")

    override fun getId(): String = ID

    override fun getIcon(): Icon = SceneIcons.FILE

    override fun getWeight(): Int = WEIGHT

    override fun createStructure(): ProjectAbstractTreeStructureBase = object : ProjectTreeStructure(myProject, ID) {
        override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
            AbyssusRootNode(project, settings)

        override fun isToBuildChildrenInBackground(element: Any) = true
    }

    /** The platform sorts siblings by name; `Scenes`, `UI`, `Assets` and the type groups keep their specified order. */
    override fun createComparator(): java.util.Comparator<com.intellij.ide.util.treeView.NodeDescriptor<*>> {
        val platform = super.createComparator()
        fun rank(d: com.intellij.ide.util.treeView.NodeDescriptor<*>) =
            (d as? DtoEntryNode)?.value?.let { fixedRank(it.value, it.name) }
        return java.util.Comparator { a, b -> compareFixed(rank(a), rank(b)) ?: platform.compare(a, b) }
    }

    // The platform requires the target's minor view id to equal the pane id; the inherited one is "ProjectPane".
    override fun createSelectInTarget(): SelectInTarget = AbyssusSelectInTarget(myProject)

    private var wrapped: JComponent? = null

    /** The platform's tree component with the counts footer under it; the platform caches its own component,
     *  so the wrapper is cached too. */
    override fun createComponent(): JComponent {
        val inner = super.createComponent()
        wrapped?.let { return it }
        return JPanel(BorderLayout()).also {
            it.add(inner, BorderLayout.CENTER)
            it.add(AbyssusFooter(myProject, this), BorderLayout.SOUTH)
            wrapped = it
        }
    }

    // the platform's own header buttons stay as they are; the unused filter is the first item of the Options menu
    override fun addToolbarActions(actionGroup: DefaultActionGroup) {
        super.addToolbarActions(actionGroup)
        actionGroup.add(UnusedFilterAction(), com.intellij.openapi.actionSystem.Constraints.FIRST)
    }

    override fun createTree(treeModel: DefaultTreeModel): ProjectViewTree =
        EyeTree(treeModel, myProject).also { publishSelectionOf(it, myProject) }

    companion object {
        const val ID = "Abyssus"
        const val WEIGHT = 100
    }
}

/** Publishes the user object of [tree]'s selected node (null when nothing is selected) as the Abyssus selection of
 *  [project].
 */
internal fun publishSelectionOf(tree: JTree, project: Project) {
    tree.addTreeSelectionListener { e ->
        AbyssusSelection.of(project)
            .select(e.newLeadSelectionPath?.let { TreeUtil.getUserObject(it.lastPathComponent) })
    }
}

/**
 * The platform repaints every fragment of a selected, focused row in the selection foreground, which
 * hides the gray of a disabled parameter exactly while it is selected (e.g. right after its eye is
 * clicked). Keep the gray for those fragments.
 */
private class GrayKeepingRenderer : ProjectViewRenderer() {
    override fun customizeCellRenderer(
        tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean,
    ) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
        val entry = (TreeUtil.getUserObject(value) as? DtoEntryNode)?.value
        if (entry != null && entry.isGame) {
            append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(GameBadge.TEXT, GameBadge.ATTRIBUTES, GameBadge.TAG)
        }
    }

    /** The outline of the `game` badge, around the text fragment that [customizeCellRenderer] tagged. */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val index = (0 until fragmentCount).firstOrNull { getFragmentTag(it) === GameBadge.TAG } ?: return
        val xs = (0 until width).filter { findFragmentAt(it) == index }
        if (xs.isEmpty()) return
        val g2 = g.create() as Graphics2D
        try {
            val h = g2.getFontMetrics(RelativeFont.SMALL.derive(font)).height
            GameBadge.paintOutline(g2, xs.first(), (height - h) / 2, xs.last() - xs.first() + 1, h)
        } finally {
            g2.dispose()
        }
    }


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
 * toggle, "View" on scenes; `unused` sits left of the eye.
 */
class EyeTree(model: DefaultTreeModel, private val project: Project) : ProjectViewTree(model) {

    private val dropdownActions: List<DropdownAction> = listOf(
        AssetDropdownAction(),
        SceneDropdownAction(),
        ScenesDropdownAction(),
        UiDropdownAction(),
    )

    override fun createCellRenderer(): TreeCellRenderer = GrayKeepingRenderer()

    /** Each level's icon starts [LEVEL_INDENT] px right of its parent's, whatever indent the platform's tree UI brings. */
    override fun updateUI() {
        super.updateUI()
        (ui as? javax.swing.plaf.basic.BasicTreeUI)?.let {
            it.rightChildIndent = JBUI.scale(LEVEL_INDENT) - it.leftChildIndent
        }
    }

    companion object {
        const val LEVEL_INDENT = 16
    }

    /** The row's actions, rightmost first. */
    private fun actionsFor(row: Int): List<RowAction> {
        val node = TreeUtil.getUserObject(getPathForRow(row)?.lastPathComponent)
        viewableSceneFile(node)?.let { file ->
            return listOf(IconAction(SceneViewIcons.VIEW, AbyssusBundle.message("viewSceneTooltip")) {
                openSceneView(
                    project,
                    file
                )
            })
        }
        val entry = (node as? DtoEntryNode)?.value ?: return emptyList()
        return listOfNotNull(eyeAction(entry), unusedBadgeFor(entry))
    }

    private fun eyeAction(entry: DtoEntry): RowAction? {
        if (entry.enabled == null) return null
        val icon = if (entry.enabled) EyeIcons.ON else EyeIcons.OFF
        return IconAction(icon, null) { r ->
            val wasExpanded = isExpanded(getPathForRow(r))
            if (toggleRow(project, entry)) reselect(entry.path, wasExpanded)
        }
    }

    /** Where [actions] of [row] sit: from the right edge of the visible area, rightmost first, [ACTION_GAP] apart. */
    private fun actionBounds(row: Int, actions: List<RowAction>): List<Rectangle>? {
        val rowBounds = getRowBounds(row) ?: return null
        return layoutActions(rowBounds, visibleRect.let { it.x + it.width }, actions, this)
    }

    /** The painted actions of [row] as `name=x..x2;...` (`unused`, `icon`), rightmost first; the UI tests read it to check spacing. */
    fun actionLayout(row: Int): String {
        val actions = actionsFor(row)
        val bounds = actionBounds(row, actions) ?: return ""
        return actions.indices.joinToString(";") { i ->
            "${if (actions[i] is UnusedBadge) "unused" else "icon"}=${bounds[i].x}..${bounds[i].x + bounds[i].width - 1}"
        }
    }

    private fun rowOf(e: MouseEvent): Int? {
        val row = getClosestRowForLocation(e.x, e.y)
        return row.takeIf { it >= 0 && getRowBounds(it)?.let { b -> e.y in b.y until b.y + b.height } == true }
    }

    private fun actionAt(e: MouseEvent): Pair<Int, RowAction>? {
        val row = rowOf(e) ?: return null
        val actions = actionsFor(row)
        val bounds = actionBounds(row, actions) ?: return null
        return actions.indices.firstOrNull { actions[it].run != null && bounds[it].contains(e.point) }
            ?.let { row to actions[it] }
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
                cursor = if (actionAt(e) != null) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        })
    }

    /**
     * The Assets row's right-click menu holds only Import Model and New, not the platform's project-view actions;
     * every other row keeps the platform popup.
     */
    override fun processMouseEvent(e: MouseEvent) {
        if (e.isPopupTrigger) {
            val row = rowOf(e)
            if (row != null) {
                val node = TreeUtil.getUserObject(getPathForRow(row)?.lastPathComponent)
                if (dropdownActions.any { it.openDropdown(this, e, row, node) }) {
                    return
                }
            }
        }
        super.processMouseEvent(e)
    }

    override fun getToolTipText(event: MouseEvent): String? =
        actionAt(event)?.second?.tooltip ?: super.getToolTipText(event)

    /**
     * A toggled row changes identity (see [DtoEntry.equals]), so the refresh drops the selection onto
     * its parent; select the row with the same entry path again and restore its expansion.
     */
    private fun reselect(entryPath: String, expand: Boolean) {
        val visitor = TreeVisitor { path ->
            val entry = (TreeUtil.getUserObject(path.lastPathComponent) as? DtoEntryNode)?.value
            val asset = (TreeUtil.getUserObject(path.lastPathComponent) as? AbyssusAssetNode)?.virtualFile?.path
            when (reselectStep(entryPath, entry?.path, asset)) {
                ReselectStep.FOUND -> TreeVisitor.Action.INTERRUPT
                ReselectStep.DESCEND -> TreeVisitor.Action.CONTINUE
                ReselectStep.SKIP -> TreeVisitor.Action.SKIP_CHILDREN
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
