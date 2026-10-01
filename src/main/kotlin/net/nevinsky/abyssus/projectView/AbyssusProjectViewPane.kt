package net.nevinsky.abyssus.projectView

import com.intellij.icons.AllIcons
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

    override fun getWeight(): Int = 100

    override fun createStructure(): ProjectAbstractTreeStructureBase = object : ProjectTreeStructure(myProject, ID) {
        override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
            AbyssusRootNode(project, settings)

        override fun isToBuildChildrenInBackground(element: Any) = true
    }

    override fun createTree(treeModel: DefaultTreeModel): ProjectViewTree = EyeTree(treeModel, myProject)

    companion object {
        const val ID = "Abyssus"
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

/** Paints a clickable eye at the right edge of every row whose entry is gated by an `xxxEnabled` toggle. */
private class EyeTree(model: DefaultTreeModel, private val project: Project) : ProjectViewTree(model) {
    override fun createCellRenderer(): TreeCellRenderer = GrayKeepingRenderer()

    private fun eyeEntry(row: Int): DtoEntry? =
        (TreeUtil.getUserObject(getPathForRow(row)?.lastPathComponent) as? DtoEntryNode)?.value?.takeIf { it.enabled != null }

    private fun eyeIcon(entry: DtoEntry) = if (entry.enabled == true) AllIcons.Actions.Show else AllIcons.Actions.ToggleVisibility

    private fun eyeBounds(row: Int, icon: Icon): Rectangle {
        val bounds = getRowBounds(row)
        val x = visibleRect.let { it.x + it.width } - icon.iconWidth - EYE_GAP
        return Rectangle(x, bounds.y + (bounds.height - icon.iconHeight) / 2, icon.iconWidth, icon.iconHeight)
    }

    private fun eyeAt(e: MouseEvent): DtoEntry? {
        val row = getClosestRowForLocation(e.x, e.y)
        if (row < 0 || getRowBounds(row)?.let { e.y in it.y until it.y + it.height } != true) return null
        val entry = eyeEntry(row) ?: return null
        return entry.takeIf { eyeBounds(row, eyeIcon(it)).contains(e.point) }
    }

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                val entry = eyeAt(e) ?: return
                e.consume()
                val wasExpanded = isExpanded(getPathForRow(getClosestRowForLocation(e.x, e.y)))
                if (toggleEnabled(project, entry)) reselect(entry.path, wasExpanded)
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                cursor = if (eyeAt(e) != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        })
    }

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
            val entry = eyeEntry(row) ?: continue
            val icon = eyeIcon(entry)
            val r = eyeBounds(row, icon)
            icon.paintIcon(this, g, r.x, r.y)
        }
    }

    private companion object {
        const val EYE_GAP = 8
    }
}
