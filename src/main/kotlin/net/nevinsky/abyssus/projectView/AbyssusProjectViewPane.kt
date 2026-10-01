package net.nevinsky.abyssus.projectView

import com.intellij.icons.AllIcons
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
import net.nevinsky.abyssus.filetype.SceneIcons
import net.nevinsky.abyssus.filetype.SceneViewIcons
import net.nevinsky.abyssus.sceneview.openSceneView
import javax.swing.ToolTipManager
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.tree.DefaultTreeModel

class AbyssusProjectViewPane(project: Project) : ProjectViewPane(project) {
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

    override fun createTree(treeModel: DefaultTreeModel): ProjectViewTree = EyeTree(treeModel, myProject)

    companion object {
        const val ID = "Abyssus"
        const val WEIGHT = 100
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

private class RowAction(val icon: Icon, val tooltip: String?, val run: (row: Int) -> Unit)

/**
 * Paints one clickable icon at the right edge of rows that have an action: the eye on entries gated by an
 * `xxxEnabled` toggle, and "View" on scenes.
 */
private class EyeTree(model: DefaultTreeModel, private val project: Project) : ProjectViewTree(model) {
    override fun createCellRenderer(): TreeCellRenderer = GrayKeepingRenderer()

    private fun actionFor(row: Int): RowAction? {
        val node = TreeUtil.getUserObject(getPathForRow(row)?.lastPathComponent)
        viewableSceneFile(node)?.let { file ->
            return RowAction(SceneViewIcons.VIEW, AbyssusBundle.message("viewSceneTooltip")) { openSceneView(project, file) }
        }
        val entry = (node as? DtoEntryNode)?.value?.takeIf { it.enabled != null } ?: return null
        val icon = if (entry.enabled == true) AllIcons.Actions.Show else AllIcons.Actions.ToggleVisibility
        return RowAction(icon, null) { r ->
            val wasExpanded = isExpanded(getPathForRow(r))
            if (toggleEnabled(project, entry)) reselect(entry.path, wasExpanded)
        }
    }

    private fun iconBounds(row: Int, icon: Icon): Rectangle {
        val bounds = getRowBounds(row)
        val x = visibleRect.let { it.x + it.width } - icon.iconWidth - ICON_GAP
        return Rectangle(x, bounds.y + (bounds.height - icon.iconHeight) / 2, icon.iconWidth, icon.iconHeight)
    }

    private fun rowOf(e: MouseEvent): Int? {
        val row = getClosestRowForLocation(e.x, e.y)
        return row.takeIf { it >= 0 && getRowBounds(it)?.let { b -> e.y in b.y until b.y + b.height } == true }
    }

    private fun actionAt(e: MouseEvent): Pair<Int, RowAction>? {
        val row = rowOf(e) ?: return null
        val action = actionFor(row) ?: return null
        return (row to action).takeIf { iconBounds(row, action.icon).contains(e.point) }
    }

    init {
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                val (row, action) = actionAt(e) ?: return
                e.consume()
                action.run(row)
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
        for (row in first..last) {
            val action = actionFor(row) ?: continue
            val r = iconBounds(row, action.icon)
            action.icon.paintIcon(this, g, r.x, r.y)
        }
    }

    private companion object {
        const val ICON_GAP = 8
    }
}
