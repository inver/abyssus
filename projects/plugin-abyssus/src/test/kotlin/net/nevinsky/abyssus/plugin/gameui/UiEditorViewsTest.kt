/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.ui.UiEditError
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import java.util.concurrent.Executor
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

class UiEditorViewsTest : BasePlatformTestCase() {
    private val source = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main","screens":[
        {"id":"main","name":"Main","root":{"id":"root","type":"panel","children":[
          {"id":"free","type":"button","layout":{"x":50,"y":40,"width":100,"height":40},"properties":{"text":"Go"}},
          {"id":"list","type":"column","children":[{"id":"a","type":"label"},{"id":"b","type":"label"},{"id":"c","type":"label"}]},
          {"id":"odd","type":"hologram"}]}},
        {"id":"pause","name":"Pause","kind":"overlay","root":{"id":"p-root","type":"panel"}}]}}}""")!!

    private lateinit var file: VirtualFile

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        file = myFixture.addFileToProject("p/assets/menus/meta.json", source).virtualFile
    }

    private fun controller(): UiEditorController =
        UiEditorController(project, file, Executor { it.run() }, { it.run() }).also { it.reload() }

    private fun ids(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: com.fasterxml.jackson.databind.JsonNode) { n.get("id")?.takeIf { it.isTextual }?.let { out += it.asText() }; n.get("children")?.forEach(::walk) }
        SceneJson().parse(FileDocumentManager.getInstance().getDocument(file)!!.text).path("additional").path("ui").path("screens").get(0).get("root").let(::walk)
        return out
    }

    private val collection get() = (UiCollectionDecoder().decode(SceneJson().parse(source)) as UiAdmission.Admitted).collection

    // ---- tree drop rules ----

    fun testDroppingNextToAWidgetPicksItsParentAndIndexAmongTheOthers() {
        val c = collection
        assertEquals(TreeDrop("list", 0), treeDrop(c, "free", "a", DropPosition.BEFORE))
        assertEquals(TreeDrop("list", 3), treeDrop(c, "free", "c", DropPosition.AFTER))
        assertEquals("counted without the dragged widget itself", TreeDrop("list", 1), treeDrop(c, "a", "c", DropPosition.BEFORE))
        assertEquals(TreeDrop("list", 2), treeDrop(c, "a", "c", DropPosition.AFTER))
        assertEquals(TreeDrop("root", 0), treeDrop(c, "a", "free", DropPosition.BEFORE))
        assertEquals(TreeDrop("list", null), treeDrop(c, "free", "list", DropPosition.INTO))
    }

    fun testIllegalDropsAreRefused() {
        val c = collection
        assertNull("only containers take INTO", treeDrop(c, "free", "odd", DropPosition.INTO))
        assertNull("onto itself", treeDrop(c, "free", "free", DropPosition.BEFORE))
        assertNull("into its own subtree", treeDrop(c, "list", "a", DropPosition.INTO))
        assertNull("the root does not move", treeDrop(c, "root", "list", DropPosition.INTO))
        assertNull("nothing sits beside the root", treeDrop(c, "free", "root", DropPosition.BEFORE))
        assertNull(treeDrop(c, "free", "nobody", DropPosition.AFTER))
    }

    // ---- views ----

    fun testTheTreeShowsTheDisplayedScreenAndSharesTheSelection() {
        val c = controller()
        val panel = UiWidgetTreePanel(c)
        val root = panel.tree.model.root as DefaultMutableTreeNode
        val items = root.depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>().mapNotNull { (it.userObject as? UiTreeItem)?.id }
        assertEquals(setOf("root", "free", "list", "a", "b", "c", "odd"), items.toSet())
        val labels = root.depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>().mapNotNull { (it.userObject as? UiTreeItem)?.label }
        assertTrue(labels.any { it.contains("\"Go\"") })
        assertTrue("an unknown widget kind is flagged", labels.any { it.startsWith("? hologram") })
        // controller -> tree
        c.select("b")
        assertEquals("b", ((panel.tree.lastSelectedPathComponent as DefaultMutableTreeNode).userObject as UiTreeItem).id)
        // tree -> controller
        val node = root.depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>().first { (it.userObject as? UiTreeItem)?.id == "free" }
        panel.tree.selectionPath = javax.swing.tree.TreePath(node.path)
        assertEquals("free", c.selection.widgetId)
        // another screen
        c.selectScreen("pause")
        val rows = (panel.tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>().mapNotNull { (it.userObject as? UiTreeItem)?.id }
        assertEquals(listOf("p-root"), rows)
    }

    private fun menu(c: UiEditorController, answer: String? = null) = UiScreensMenu(c, null) { _, _ -> answer }

    private fun texts(menu: UiScreensMenu) = menu.group().getChildren(null).filter { it !is com.intellij.openapi.actionSystem.Separator }.map { it.templateText }

    fun testTheScreensMenuMarksTheEntryAndSwitchesScreens() {
        val c = controller()
        val menu = menu(c)
        assertEquals("Main  [entry]", menu.text)
        val items = texts(menu)
        assertEquals("Main  [entry]", items[0])
        assertEquals("Pause (overlay)", items[1])
        val pause = menu.group().getChildren(null)[1] as com.intellij.openapi.actionSystem.ToggleAction
        pause.setSelected(com.intellij.testFramework.TestActionEvent.createTestEvent(pause), true)
        assertEquals("pause", c.screenId)
        assertEquals("Pause (overlay)", menu.text)
        c.selectScreen("main")
        assertEquals("Main  [entry]", menu.text)
    }

    fun testTheScreensMenuAddsRenamesAndDeletesAScreen() {
        val c = controller()
        menu(c, "HUD").addScreen("overlay")
        assertEquals("hud", c.screenId)
        assertEquals(3, c.collection!!.screens.size)
        menu(c, "Heads Up").rename()
        assertEquals("Heads Up", c.collection!!.screen("hud")!!.name)
        menu(c).delete()
        assertEquals(2, c.collection!!.screens.size)
        val m = menu(c)
        assertFalse(m.arrows)
        m.setArrows(true)
        assertTrue(m.arrows)
    }

    fun testAddingNamesItsTargetAndTheOldPaletteGridIsGone() {
        val c = controller()
        val tree = UiWidgetTreePanel(c)
        c.select("list")
        assertEquals("Add to Column  (list)", tree.addTitle())
        c.select("a")
        assertEquals("a leaf names its parent", "Add to Column  (list)", tree.addTitle())
        assertEquals(UiWidgetKinds, tree.addKinds.toSet())
        assertEquals(UiWidgetKinds.size, tree.addGroup().getChildren(null).size)
    }

    fun testTheWidgetsHeaderAddsIntoTheSelectedContainerAndMovesDuplicatesAndDeletes() {
        val c = controller()
        val tree = UiWidgetTreePanel(c)
        c.select("list")
        tree.add("slider")
        assertEquals(listOf("root", "free", "list", "a", "b", "c", "slider", "odd"), ids())
        assertEquals("slider", c.selection.widgetId)
        findButton(tree, "up").doClick()
        assertEquals(listOf("root", "free", "list", "a", "b", "slider", "c", "odd"), ids())
        findButton(tree, "down").doClick()
        assertEquals(listOf("root", "free", "list", "a", "b", "c", "slider", "odd"), ids())
        findButton(tree, "duplicate").doClick()
        assertEquals("slider-2", c.selection.widgetId)
        findButton(tree, "delete").doClick()
        assertEquals(listOf("root", "free", "list", "a", "b", "c", "slider", "odd"), ids())
        c.select("a")
        tree.add("label")
        assertEquals("a leaf selection adds into its parent", 5, c.collection!!.screen("main")!!.root.children[1].children.size)
    }

    private val UiWidgetKinds = net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType.entries.map { it.json }.toSet()

    private fun findButton(panel: java.awt.Container, name: String): javax.swing.JButton {
        fun walk(c: java.awt.Container): javax.swing.JButton? = c.components.firstNotNullOfOrNull { child ->
            if (child is javax.swing.JButton && child.name == name) child else (child as? java.awt.Container)?.let(::walk)
        }
        return checkNotNull(walk(panel)) { name }
    }

    fun testTheModeSwitchAndErrorStatusFollowTheController() {
        val c = controller()
        val editor = UiEditorPanel(null, c, JPanel())
        assertTrue(editor.editButton.isSelected)
        editor.previewButton.doClick()
        assertEquals(UiEditorMode.PREVIEW, c.mode)
        editor.editButton.doClick()
        assertEquals(UiEditorMode.EDIT, c.mode)
        c.select("root")
        assertFalse(c.deleteSelected())
        assertEquals(UiEditError.ROOT_WIDGET, c.lastError)
        val statusText = (editor.components.flatMap { listOf(it) }.let { collectLabels(editor) })
        assertTrue(statusText.any { it.contains("screen's root", ignoreCase = true) })
    }

    private fun collectLabels(c: java.awt.Container): List<String> = c.components.flatMap { child ->
        (if (child is javax.swing.JLabel) listOf(child.text.orEmpty()) else emptyList()) + ((child as? java.awt.Container)?.let(::collectLabels) ?: emptyList())
    }

    fun testAllThreePartsAreVisibleAndTheCanvasIsWidestAt1190By880() {
        val c = controller()
        val canvas = JPanel()
        val editor = UiEditorPanel(null, c, canvas)
        editor.setSize(1190, 880)
        fun layoutAll(x: java.awt.Container) { x.doLayout(); x.components.forEach { (it as? java.awt.Container)?.let(::layoutAll) } }
        layoutAll(editor)
        fun widthOf(x: java.awt.Component) = x.width
        val widgets = widthOf(editor.widgets)
        val side = widthOf(editor.properties.parent)
        val middle = widthOf(canvas)
        assertTrue("widgets $widgets", widgets in 150..260)
        assertTrue("side $side", side in 240..340)
        assertTrue("canvas $middle is the widest", middle > widgets && middle > side)
        assertTrue("tree owns the column: ${editor.widgets.height}", editor.widgets.height >= 800)
    }

    fun testTheResolutionPickerSetsThePreviewViewportAndTheStripFollowsTheMode() {
        val c = controller()
        val editor = UiEditorPanel(null, c, JPanel())
        assertEquals(PREVIEW_PRESETS.size, editor.resolution.itemCount)
        editor.resolution.selectedIndex = 2
        assertEquals(PREVIEW_PRESETS[2], c.previewViewport)
        c.setPreviewViewport(1000, 500)
        assertEquals("a custom size is listed and selected", "1000 \u00d7 500", editor.resolution.selectedItem)
        val strip = collectLabels(editor)
        assertTrue(strip.toString(), strip.contains(net.nevinsky.abyssus.plugin.AbyssusBundle.message("uiKeysEdit")))
        editor.previewButton.doClick()
        assertTrue(collectLabels(editor).contains(net.nevinsky.abyssus.plugin.AbyssusBundle.message("uiKeysPreview")))
        c.reportZoom(0.5f)
        assertTrue(collectLabels(editor).contains(net.nevinsky.abyssus.plugin.AbyssusBundle.message("uiCanvasZoom", 50)))
    }
}
