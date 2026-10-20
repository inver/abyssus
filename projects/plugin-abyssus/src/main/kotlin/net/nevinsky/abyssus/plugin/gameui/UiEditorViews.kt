/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/** One row of the widget tree. */
data class UiTreeItem(val id: String, val label: String)

private fun labelOf(widget: UiWidget, role: String? = null): String {
    val base = labelOfWidget(widget)
    return if (role == null) base else "${AbyssusBundle.message(role)}: $base"
}

private fun labelOfWidget(widget: UiWidget): String {
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
        add(header(), BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        controller.addListener(::rebuild)
        rebuild()
    }

    // ---- header: Add, Duplicate, Delete, Up, Down and the list empty-state actions ----

    private fun iconButton(name: String, icon: Icon, tip: String, run: () -> Unit) = JButton(icon).apply {
        this.name = name
        toolTipText = tip
        margin = Insets(2, 4, 2, 4)
        addActionListener { run() }
    }

    private fun header(): JComponent {
        val add = JButton(AbyssusBundle.message("uiAddWidget"), AllIcons.General.ArrowDown).apply {
            name = "add-widget"
            horizontalTextPosition = SwingConstants.LEADING
            addActionListener { openAddPopup(this) }
        }
        val more = iconButton("more", AllIcons.Actions.More, AbyssusBundle.message("uiMoreWidgetActions")) { }
        more.addActionListener { openMorePopup(more) }
        return JPanel(BorderLayout()).apply {
            add(JBLabel(AbyssusBundle.message("uiWidgets")).apply { border = JBUI.Borders.empty(0, 4) }, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
                add(add)
                add(iconButton("duplicate", AllIcons.Actions.Copy, AbyssusBundle.message("uiDuplicate")) { controller.duplicateSelected() })
                add(iconButton("delete", AllIcons.General.Remove, AbyssusBundle.message("uiDelete")) { controller.deleteSelected() })
                add(iconButton("up", AllIcons.Actions.MoveUp, AbyssusBundle.message("uiMoveUp")) { shift(-1) })
                add(iconButton("down", AllIcons.Actions.MoveDown, AbyssusBundle.message("uiMoveDown")) { shift(1) })
                add(more)
            }, BorderLayout.CENTER)
        }
    }

    /** The Add popup's title: names the container the widget is added into. */
    internal fun addTitle(): String = controller.addTarget()?.let { AbyssusBundle.message("uiAddTo", labelOfWidget(it)) } ?: AbyssusBundle.message("uiAddWidget")

    /** The widget kinds the Add popup offers. */
    internal val addKinds: List<String> get() = UiWidgetType.entries.map { it.json }

    internal fun add(type: String): Boolean = controller.addWidget(type)

    private fun action(text: String, run: () -> Unit): AnAction = object : AnAction(text) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = run()
    }

    internal fun addGroup() = DefaultActionGroup().apply {
        addKinds.forEach { type -> add(action(AbyssusBundle.message("uiWidgetType.$type")) { add(type) }) }
    }

    internal fun moreGroup() = DefaultActionGroup().apply {
        add(action(AbyssusBundle.message("uiAddEmpty")) { controller.addListEmpty() })
        add(action(AbyssusBundle.message("uiRemoveEmpty")) { controller.removeListEmpty() })
    }

    private fun openAddPopup(owner: JComponent) = JBPopupFactory.getInstance()
        .createActionGroupPopup(addTitle(), addGroup(), DataManager.getInstance().getDataContext(owner), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
        .showUnderneathOf(owner)

    private fun openMorePopup(owner: JComponent) = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, moreGroup(), DataManager.getInstance().getDataContext(owner), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
        .showUnderneathOf(owner)

    internal fun shift(delta: Int) {
        val c = controller.collection ?: return
        val id = controller.selection.widgetId ?: return
        val parent = net.nevinsky.abyssus.lib.core.editor.ui.UiTree(c).parentOf(id) ?: return
        val at = parent.children.indexOfFirst { it.id == id }
        controller.reorderSelected(at + delta)
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
                fun add(parent: DefaultMutableTreeNode, w: UiWidget, role: String? = null) {
                    val node = DefaultMutableTreeNode(UiTreeItem(w.id, labelOf(w, role))).also { parent.add(it) }
                    w.children.forEach { add(node, it) }
                    // a list shows the row it repeats and its empty state as parts of itself
                    w.template?.let { add(node, it, "uiTreeRow") }
                    w.empty?.let { add(node, it, "uiTreeEmpty") }
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
