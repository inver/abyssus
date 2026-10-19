/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import java.util.concurrent.Executor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JTextField
import javax.swing.tree.DefaultMutableTreeNode

class UiListEditorTest : BasePlatformTestCase() {
    private val source = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main",
        "bindings":[
          {"name":"scores","type":"list","fields":[{"name":"rank","type":"number"},{"name":"name","type":"string"},{"name":"look","type":"string"}],
           "sample":[{"rank":1,"name":"Ace","look":"gold"},{"rank":2,"name":"Bo","look":"plain"}]},
          {"name":"title","type":"string","sample":"T"}],
        "styles":[{"name":"gold","color":"#ffd700"}],
        "screens":[{"id":"main","name":"Main","root":{"id":"root","type":"column","children":[
          {"id":"head","type":"label","bindings":{"text":"title"}},
          {"id":"table","type":"list","bindings":{"items":"scores"},"template":{"id":"row","type":"row","children":[
            {"id":"who","type":"label","bindings":{"text":"item.name","style":"item.look"}}]}}]}}]}}}""")!!

    private lateinit var file: VirtualFile

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("p/P.abss", """{"format":"abyssus","formatVersion":1}""")
        file = myFixture.addFileToProject("p/assets/menus/meta.json", source).virtualFile
    }

    private fun controller(): UiEditorController = UiEditorController(project, file, Executor { it.run() }, { it.run() }).also { it.reload() }
    private val doc get() = FileDocumentManager.getInstance().getDocument(file)!!.text
    private val collection: UiCollection get() = (UiCollectionDecoder().decode(SceneJson().parse(doc)) as UiAdmission.Admitted).collection
    private val model = UiPropertiesModel()

    private fun find(root: java.awt.Container, name: String): java.awt.Component {
        fun walk(c: java.awt.Container): java.awt.Component? = c.components.firstNotNullOfOrNull { ch -> if (ch.name == name) ch else (ch as? java.awt.Container)?.let(::walk) }
        return checkNotNull(walk(root)) { name }
    }

    // ---- header and tree ----

    private fun run(group: com.intellij.openapi.actionSystem.DefaultActionGroup, index: Int) {
        val action = group.getChildren(null)[index]
        action.actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent(action))
    }

    fun testThePaletteAddsAListWithItsRowAndTheTreeShowsRowAndEmptyState() {
        val c = controller()
        val tree = UiWidgetTreePanel(c)
        assertTrue(tree.addKinds.contains("list"))
        c.select("root")
        tree.add("list")
        assertEquals("list", c.selection.widgetId)
        val added = collection.screens[0].root.children.last()
        assertEquals(UiWidgetType.LIST, added.type)
        assertEquals("list-row", added.template!!.id)
        c.select("table")
        run(tree.moreGroup(), 0)
        val labels = (tree.tree.model.root as DefaultMutableTreeNode).depthFirstEnumeration().toList().filterIsInstance<DefaultMutableTreeNode>().mapNotNull { (it.userObject as? UiTreeItem)?.label }
        assertTrue(labels.toString(), labels.any { it.startsWith("Row: ") && it.endsWith("(row)") })
        assertTrue(labels.any { it.startsWith("Empty state: ") })
        c.select("who"); run(tree.moreGroup(), 1)
        assertNull("removing from inside the list's row works too", collection.screens[0].root.children[1].empty)
        c.select("head"); assertFalse("outside a list there is nothing to remove", c.removeListEmpty())
    }

    fun testWidgetsInsideTheRowCanBeAddedSelectedAndNotDeletedAsTheTemplate() {
        val c = controller()
        c.select("row")
        assertTrue(c.addWidget("button"))
        assertEquals(listOf("who", "button"), collection.screens[0].root.children[1].template!!.children.map { it.id })
        c.select("row")
        assertFalse(c.deleteSelected())
        assertEquals(net.nevinsky.abyssus.lib.core.editor.ui.UiEditError.ROOT_WIDGET, c.lastError)
    }

    // ---- properties ----

    private fun binding(id: String, key: String) = model.fieldsFor(collection, id)!!.bindings.first { it.key == key } as UiField.Binding

    fun testAListHasRowsAndItsItemsBindingListsOnlyLists() {
        val groups = model.fieldsFor(collection, "table")!!
        assertEquals(listOf("property.maxRows"), groups.content.map { it.key })
        assertEquals(listOf("scores"), binding("table", "binding.items").options)
        assertEquals("scores", binding("table", "binding.items").binding)
        assertTrue("a list lays its rows out like a column", groups.layout.any { it.key == "layout.spacing" })
        // a list cannot feed a label's text
        assertFalse(binding("head", "binding.text").options.contains("scores"))
    }

    fun testWidgetsInsideARowOfferTheRecordsFieldsAndOthersDoNot() {
        val text = binding("who", "binding.text")
        assertEquals(listOf("title", "item.rank", "item.name", "item.look"), text.options)
        assertNull(text.warning)
        assertEquals("a string binding or field can name a style", listOf("title", "item.name", "item.look"), binding("who", "binding.style").options)
        assertEquals("outside a row there is no item", listOf("title"), binding("head", "binding.text").options)
        assertEquals("the template container is itself a row, so it may bind a row-wide style", listOf("title", "item.name", "item.look"), binding("row", "binding.style").options)
        assertTrue("the list that repeats it is not inside a row", binding("table", "binding.style").options.none { it.startsWith("item.") })
    }

    fun testItemBindingsAreWarnedWhenTheFieldOrTypeIsWrong() {
        val c = controller()
        assertTrue(c.edit("t", model.edit("binding.text", "who", "item.nickname")!!))
        assertEquals(UiFieldWarning.UNKNOWN_BINDING, binding("who", "binding.text").warning)
        assertTrue(c.edit("t", model.edit("binding.style", "who", "item.rank")!!))
        assertEquals("a number field cannot name a style", UiFieldWarning.INCOMPATIBLE_TARGET, binding("who", "binding.style").warning)
        assertTrue(c.edit("t", model.edit("binding.style", "who", "item.look")!!))
        assertNull(binding("who", "binding.style").warning)
    }

    fun testMaxRowsAndTheItemsBindingAreEditedThroughThePanel() {
        val c = controller()
        c.select("table")
        val panel = UiPropertiesPanel(c)
        val rows = panel.components.getValue("property.maxRows") as JTextField
        rows.text = "10"; rows.postActionEvent()
        assertEquals(10, collection.screens[0].root.children[1].properties["maxRows"]!!.intValue())
        rows.text = "ten"; rows.postActionEvent()
        assertEquals("a bad number is not written", 10, collection.screens[0].root.children[1].properties["maxRows"]!!.intValue())
        rows.text = ""; rows.postActionEvent()
        assertNull(collection.screens[0].root.children[1].properties["maxRows"])
        val items = panel.components.getValue("binding.items") as JComboBox<*>
        items.selectedItem = ""
        assertNull(collection.screens[0].root.children[1].bindings["items"])
    }

    // ---- screens ----

    fun testTheScreenPanelTogglesArrowNavigationAndRestoresTheTextExactly() {
        val c = controller()
        val screens = UiScreensMenu(c, null) { _, _ -> null }
        val before = doc
        assertFalse(screens.arrows)
        screens.setArrows(true)
        assertTrue(doc.contains("\"navigation\": \"arrows\""))
        assertTrue(collection.screens[0].navigation == net.nevinsky.abyssus.lib.core.ui.model.UiNavigation.ARROWS)
        assertTrue(screens.arrows)
        screens.setArrows(false)
        assertEquals(before, doc)
    }

    // ---- declarations ----

    fun testListBindingFormsValidateTheRecordAndTheSampleRows() {
        fun members(form: BindingForm) = (bindingMembers(form) as? FormResult.Valid)?.value?.toString()
        assertEquals("""{"fields":[{"name":"rank","type":"number"},{"name":"name","type":"string"}],"sample":[{"rank":1,"name":"A"}]}""",
            members(BindingForm("l", "list", fields = "rank:number, name:string", sample = """[{"rank":1,"name":"A"}]""")))
        assertEquals("""{"fields":[{"name":"a","type":"boolean"}]}""", members(BindingForm("l", "list", access = "write", fields = "a:boolean")))
        fun errors(form: BindingForm) = (bindingMembers(form) as FormResult.Invalid).errors.map { it.field }
        assertEquals(listOf("fields"), errors(BindingForm("l", "list", fields = "")))
        assertEquals(listOf("fields"), errors(BindingForm("l", "list", fields = "a:number, a:string")))
        assertEquals(listOf("fields"), errors(BindingForm("l", "list", fields = "a:list")))
        assertEquals(listOf("fields"), errors(BindingForm("l", "list", fields = "a")))
        assertEquals(listOf("sample"), errors(BindingForm("l", "list", fields = "a:number", sample = """[{"a":"x"}]""")))
        assertEquals(listOf("sample"), errors(BindingForm("l", "list", fields = "a:number", sample = """[{"b":1}]""")))
        assertEquals(listOf("sample"), errors(BindingForm("l", "list", fields = "a:number", sample = "{")))
        val roundTrip = BindingForm.of(collection.binding("scores")!!)
        assertEquals("rank:number, name:string, look:string", roundTrip.fields)
        assertEquals("list", roundTrip.type)
        assertTrue(bindingMembers(roundTrip) is FormResult.Valid)
    }

    fun testADeclaredListGoesThroughTheEditsAndTheDeclarationsListShowsIt() {
        val c = controller()
        val form = BindingForm("times", "list", fields = "t:number", sample = """[{"t":1.5}]""")
        val members = (bindingMembers(form) as FormResult.Valid).value
        assertTrue(c.edit("t") { e, m -> e.upsertBinding(m, "times", "list", members) })
        assertEquals(listOf("t"), collection.binding("times")!!.fields.map { it.name })
        val declarations = UiDeclarationsPanel(project, c)
        val rows = declarations.bindings.model.let { m -> (0 until m.size).map { m.getElementAt(it) } }
        assertTrue(rows.toString(), rows.contains("times : list"))
    }

    // ---- preview ----

    fun testThePreviewEditsListSamplesRowByRowWithTypedFeedback() {
        val c = controller()
        val panel = UiPreviewPanel(c) { emptyList() }
        c.setMode(UiEditorMode.PREVIEW)
        assertEquals(setOf("scores/0/rank", "scores/0/name", "scores/0/look", "scores/1/rank", "scores/1/name", "scores/1/look"), panel.rowFields.keys)
        val rank = panel.rowFields.getValue("scores/0/rank")
        rank.text = "7"; rank.postActionEvent()
        assertEquals(UiValue.Num(7.0), (c.previewState!!.samples["scores"] as UiValue.Rows).items[0]["rank"])
        rank.text = "seven"; rank.postActionEvent()
        assertTrue(panel.sampleStatus.getValue("scores").text.contains("Not a number"))
        assertEquals("a rejected value left the sample", UiValue.Num(7.0), (c.previewState!!.samples["scores"] as UiValue.Rows).items[0]["rank"])
        (find(panel, "add-row-scores") as JButton).doClick()
        assertEquals(9, panel.rowFields.size)
        (find(panel, "remove-row-scores-0") as JButton).doClick()
        assertEquals(6, panel.rowFields.size)
        assertEquals(2, (c.previewState!!.samples["scores"] as UiValue.Rows).items.size)
        val text = doc
        c.resetPreview()
        panel.refresh()
        assertEquals("Ace", (c.previewState!!.samples["scores"] as UiValue.Rows).items[0].getValue("name").let { (it as UiValue.Text).value })
        assertEquals("the document was never touched", text, doc)
    }
}
