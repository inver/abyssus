/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import net.nevinsky.abyssus.lib.core.ui.model.UiScreenKind
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/** One row of the widget tree. */
data class UiTreeItem(val id: String, val label: String)

private fun labelOf(widget: UiWidget): String {
    val kind = widget.type?.let { AbyssusBundle.message("uiWidgetType.${it.json}") } ?: "? ${widget.rawType}"
    val text = widget.properties["text"]?.takeIf { it.isTextual }?.textValue()?.take(24)
    return if (text != null) "$kind  \"$text\"  (${widget.id})" else "$kind  (${widget.id})"
}

/** The tree of the displayed screen's widgets; its selection and the controller's are one. */
class UiWidgetTreePanel(private val controller: UiEditorController) : JPanel(BorderLayout()) {
    private val model = DefaultTreeModel(DefaultMutableTreeNode())
    internal val tree = Tree(model).apply { isRootVisible = true; showsRootHandles = true; dragEnabled = true }
    private var syncing = false

    init {
        tree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(t: JTree, value: Any?, sel: Boolean, exp: Boolean, leaf: Boolean, row: Int, focus: Boolean): Component {
                val item = (value as? DefaultMutableTreeNode)?.userObject as? UiTreeItem
                return super.getTreeCellRendererComponent(t, item?.label ?: "", sel, exp, leaf, row, focus)
            }
        }
        tree.addTreeSelectionListener {
            if (syncing) return@addTreeSelectionListener
            ((tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? UiTreeItem)?.let { controller.select(it.id) }
        }
        tree.transferHandler = TreeMoveHandler()
        add(JBScrollPane(tree), BorderLayout.CENTER)
        controller.addListener(::rebuild)
        rebuild()
    }

    /** Rebuilds the nodes for the displayed screen, keeping expansion and re-selecting the controller's widget. */
    internal fun rebuild() {
        val c = controller.collection
        val screen = controller.screenId?.let { c?.screen(it) }
        val expanded = expandedIds()
        syncing = true
        try {
            val root = DefaultMutableTreeNode()
            if (screen != null) {
                fun add(parent: DefaultMutableTreeNode, w: UiWidget) {
                    val node = DefaultMutableTreeNode(UiTreeItem(w.id, labelOf(w))).also { parent.add(it) }
                    w.children.forEach { add(node, it) }
                }
                add(root, screen.root)
            }
            model.setRoot(root)
            val all = nodes(root)
            all.forEach { n -> if (n === root.firstChild || (n.userObject as? UiTreeItem)?.id in expanded) tree.expandPath(TreePath(n.path)) }
            selectNode(all, controller.selection.widgetId)
        } finally {
            syncing = false
        }
    }

    private fun nodes(root: DefaultMutableTreeNode): List<DefaultMutableTreeNode> = root.depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>()

    private fun expandedIds(): Set<String> {
        val root = model.root as? DefaultMutableTreeNode ?: return emptySet()
        return nodes(root).filter { tree.isExpanded(TreePath(it.path)) }.mapNotNull { (it.userObject as? UiTreeItem)?.id }.toSet()
    }

    private fun selectNode(all: List<DefaultMutableTreeNode>, id: String?) {
        val node = all.firstOrNull { (it.userObject as? UiTreeItem)?.id == id }
        if (node == null) tree.clearSelection() else TreePath(node.path).let { tree.selectionPath = it; tree.scrollPathToVisible(it) }
    }

    /** Dragging a row onto another row: above its middle drops before it, below after, and onto a container's middle into it. */
    private inner class TreeMoveHandler : TransferHandler() {
        override fun getSourceActions(c: JComponent) = MOVE
        override fun createTransferable(c: JComponent): java.awt.datatransfer.Transferable? {
            val id = controller.selection.widgetId ?: return null
            return java.awt.datatransfer.StringSelection(id)
        }

        override fun canImport(support: TransferSupport): Boolean = support.isDrop && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor) && drop(support) != null

        override fun importData(support: TransferSupport): Boolean {
            val target = drop(support) ?: return false
            return controller.moveSelected(target.parentId, target.index)
        }

        private fun drop(support: TransferSupport): TreeDrop? {
            val c = controller.collection ?: return null
            val dragged = controller.selection.widgetId ?: return null
            val location = (support.dropLocation as? JTree.DropLocation) ?: return null
            val path = location.path ?: return null
            val over = ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? UiTreeItem)?.id ?: return null
            val row = tree.getRowForPath(path)
            val bounds = tree.getRowBounds(row) ?: return null
            val y = location.dropPoint.y - bounds.y
            val position = when {
                y < bounds.height / 4 -> DropPosition.BEFORE
                y > bounds.height * 3 / 4 -> DropPosition.AFTER
                else -> DropPosition.INTO
            }
            return treeDrop(c, dragged, over, position)
        }
    }
}

/** The screens of the asset, with add, rename and delete; the entry screen is marked. */
class UiScreensPanel(private val project: Project?, private val controller: UiEditorController) : JPanel(BorderLayout()) {
    private val model = DefaultListModel<String>()
    internal val list = JBList(model)
    private var ids: List<String> = emptyList()
    private var syncing = false

    init {
        list.addListSelectionListener {
            if (syncing || it.valueIsAdjusting) return@addListSelectionListener
            ids.getOrNull(list.selectedIndex)?.let(controller::selectScreen)
        }
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(JButton(AbyssusBundle.message("uiAddScreen")).apply { name = "add-screen"; addActionListener { askName("uiScreenNamePrompt", "")?.let { n -> controller.addScreen(n, "screen") } } })
            add(JButton(AbyssusBundle.message("uiAddOverlay")).apply { name = "add-overlay"; addActionListener { askName("uiScreenNamePrompt", "")?.let { n -> controller.addScreen(n, "overlay") } } })
            add(JButton(AbyssusBundle.message("uiRenameScreen")).apply { name = "rename-screen"; addActionListener {
                controller.screenId?.let { id -> askName("uiScreenRenamePrompt", controller.collection?.screen(id)?.name.orEmpty())?.let { n -> controller.renameScreen(id, n) } } } })
            add(JButton(AbyssusBundle.message("uiDeleteScreen")).apply { name = "delete-screen"; addActionListener { controller.screenId?.let(controller::deleteScreen) } })
        }
        add(JBLabel(AbyssusBundle.message("uiScreens")), BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(buttons, BorderLayout.SOUTH)
        controller.addListener(::rebuild)
        rebuild()
    }

    private fun askName(prompt: String, initial: String): String? =
        Messages.showInputDialog(project, AbyssusBundle.message(prompt), AbyssusBundle.message("uiScreens"), null, initial, null)?.trim()?.takeIf { it.isNotEmpty() }

    internal fun rebuild() {
        val c = controller.collection
        syncing = true
        try {
            ids = c?.screens?.map { it.id }.orEmpty()
            model.clear()
            c?.screens?.forEach { s ->
                val mark = if (s.id == c.entryScreen || (c.entryScreen == null && s === c.screens.firstOrNull { it.kind == UiScreenKind.SCREEN })) "  [${AbyssusBundle.message("uiEntryScreen")}]" else ""
                model.addElement("${s.name}${if (s.kind == UiScreenKind.OVERLAY) " (overlay)" else ""}$mark")
            }
            list.selectedIndex = ids.indexOf(controller.screenId)
        } finally {
            syncing = false
        }
    }
}

/** Buttons that add a widget of each kind to the selected container, and act on the selected widget. */
class UiPalettePanel(private val controller: UiEditorController) : JPanel(BorderLayout()) {
    internal val buttons: Map<String, JButton>

    init {
        val grid = JPanel(GridLayout(0, 2, 2, 2))
        buttons = UiWidgetType.entries.associate { type ->
            type.json to JButton(AbyssusBundle.message("uiWidgetType.${type.json}")).apply {
                name = "add-${type.json}"
                addActionListener { controller.addWidget(type.json) }
            }.also(grid::add)
        }
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(JButton(AbyssusBundle.message("uiDuplicate")).apply { name = "duplicate"; addActionListener { controller.duplicateSelected() } })
            add(JButton(AbyssusBundle.message("uiDelete")).apply { name = "delete"; addActionListener { controller.deleteSelected() } })
            add(JButton(AbyssusBundle.message("uiMoveUp")).apply { name = "up"; addActionListener { shift(-1) } })
            add(JButton(AbyssusBundle.message("uiMoveDown")).apply { name = "down"; addActionListener { shift(1) } })
        }
        add(JBLabel(AbyssusBundle.message("uiPalette")), BorderLayout.NORTH)
        add(grid, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
    }

    private fun shift(delta: Int) {
        val c = controller.collection ?: return
        val id = controller.selection.widgetId ?: return
        val parent = net.nevinsky.abyssus.lib.core.editor.ui.UiTree(c).parentOf(id) ?: return
        val at = parent.children.indexOfFirst { it.id == id }
        controller.reorderSelected(at + delta)
    }
}
