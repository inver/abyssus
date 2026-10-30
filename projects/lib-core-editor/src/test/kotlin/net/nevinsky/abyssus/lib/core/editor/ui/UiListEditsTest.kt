package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiIssueCode
import net.nevinsky.abyssus.lib.core.ui.model.UiWidgetType
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiListEditsTest {
    private val editor = UiTextEditor()

    private val original: String = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"entryScreen":"main",
        "styles":[{"name":"gold","color":"#ffd700"}],
        "screens":[{"id":"main","name":"Main","root":{"id":"root","type":"column","children":[{"id":"title","type":"label"}]}}]}}}""")!!

    private fun edit(text: String = original, op: (UiDocumentEdits, JsonNode) -> UiEditResult) = editor.apply(text, null, op)
    private fun edited(r: UiTextResult) = r as UiTextResult.Edited
    private fun collection(text: String) = (UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted)

    private val record = SceneJson().parseObject("""{"fields":[{"name":"rank","type":"number"},{"name":"name","type":"string"},{"name":"look","type":"string"}],"sample":[{"rank":1,"name":"Ace","look":"gold"}]}""")

    /** A list with a bound record and one bound label in its row, built only through the edits. */
    private fun withList(): String {
        var text = original
        text = edited(edit(text) { e, m -> e.upsertBinding(m, "scores", "list", record) }).after
        text = edited(edit(text) { e, m -> e.addWidget(m, "root", "list") }).after
        text = edited(edit(text) { e, m -> e.setBinding(m, "list", "items", "scores") }).after
        text = edited(edit(text) { e, m -> e.addWidget(m, "list-row", "label") }).after
        text = edited(edit(text) { e, m -> e.setBinding(m, "label", "text", "item.name") }).after
        text = edited(edit(text) { e, m -> e.setBinding(m, "label", "style", "item.look") }).after
        return text
    }

    @Test fun aNewListGetsARowTemplateAndTheDocumentStaysValid() {
        val r = edited(edit { e, m -> e.addWidget(m, "root", "list") })
        assertEquals(UiEditResult.Added("list"), r.result)
        val c = collection(r.after).collection
        val list = c.screens[0].root.children[1]
        assertEquals(UiWidgetType.LIST, list.type)
        assertEquals("list-row", list.template!!.id)
        assertEquals(UiWidgetType.ROW, list.template!!.type)
        assertNull(list.empty)
        // a list is not a container for ordinary children
        assertEquals(UiEditError.NOT_A_CONTAINER, (edit(r.after) { e, m -> e.addWidget(m, "list", "label") } as UiTextResult.Rejected).error)
    }

    @Test fun theBuiltListAdmitsWithoutWarningsAndItsRowWidgetsBindToItemFields() {
        val text = withList()
        val result = collection(text)
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        val list = result.collection.screens[0].root.children[1]
        assertEquals("scores", list.bindings["items"])
        val label = list.template!!.children.single()
        assertEquals("item.name", label.bindings["text"])
        assertEquals("item.look", label.bindings["style"])
        assertEquals(listOf("rank", "name", "look"), result.collection.binding("scores")!!.fields.map { it.name })
    }

    @Test fun templateAndEmptyAreEditableSubtreesButCannotBeMovedOrDeleted() {
        val text = withList()
        for (op in listOf<(UiDocumentEdits, JsonNode) -> UiEditResult>(
            { e, m -> e.deleteWidget(m, "list-row") }, { e, m -> e.duplicateWidget(m, "list-row") },
            { e, m -> e.reorderWidget(m, "list-row", 0) }, { e, m -> e.reparentWidget(m, "list-row", "root") },
        )) assertEquals(UiEditError.ROOT_WIDGET, (edit(text, op) as UiTextResult.Rejected).error)
        // a widget inside the row is an ordinary widget
        val moved = edited(edit(text) { e, m -> e.addWidget(m, "list-row", "button") })
        assertEquals(listOf("label", "button"), collection(moved.after).collection.screens[0].root.children[1].template!!.children.map { it.id })
        val reordered = edited(edit(moved.after) { e, m -> e.reorderWidget(m, "button", 0) })
        assertEquals(listOf("button", "label"), collection(reordered.after).collection.screens[0].root.children[1].template!!.children.map { it.id })
        assertTrue(edit(reordered.after) { e, m -> e.deleteWidget(m, "button") } is UiTextResult.Edited)
        // and a widget cannot be dragged into a list's own row from the list itself
        assertEquals(UiEditError.HIERARCHY_CYCLE, (edit(text) { e, m -> e.reparentWidget(m, "list", "label") } as UiTextResult.Rejected).error)
    }

    @Test fun anEmptyStateIsAddedAndRemovedExactlyAndMaxRowsIsAnOrdinaryProperty() {
        val text = withList()
        val withEmpty = edited(edit(text) { e, m -> e.addListEmpty(m, "list") })
        assertEquals(UiEditResult.Added("list-empty"), withEmpty.result)
        assertEquals("list-empty", collection(withEmpty.after).collection.screens[0].root.children[1].empty!!.id)
        assertEquals(UiTextResult.Unchanged, edit(withEmpty.after) { e, m -> e.addListEmpty(m, "list") })
        assertEquals("removing it restores the text exactly", text, edited(edit(withEmpty.after) { e, m -> e.removeListEmpty(m, "list") }).after)
        assertEquals(UiEditError.BAD_VALUE, (edit(text) { e, m -> e.addListEmpty(m, "title") } as UiTextResult.Rejected).error)
        val capped = edited(edit(text) { e, m -> e.setProperty(m, "list", "maxRows", IntNode.valueOf(10)) })
        assertEquals(10, collection(capped.after).collection.screens[0].root.children[1].properties["maxRows"]!!.intValue())
    }

    @Test fun duplicatingAListRepairsEveryIdInsideItsTemplateAndEmptyState() {
        val text = edited(edit(withList()) { e, m -> e.addListEmpty(m, "list") }).after
        val copy = edited(edit(text) { e, m -> e.duplicateWidget(m, "list") })
        assertEquals(UiEditResult.Added("list-2"), copy.result)
        val c = collection(copy.after)
        assertTrue(c.warnings.toString(), c.warnings.isEmpty())
        val second = c.collection.screens[0].root.children[2]
        assertEquals("list-row-2", second.template!!.id)
        assertEquals("label-2", second.template!!.children.single().id)
        assertEquals("list-empty-2", second.empty!!.id)
    }

    @Test fun deletingARecordLeavesRowBindingsDiagnosedAndStylesAreCleanedInsideRows() {
        val text = withList()
        val noStyle = edited(edit(text.replace("\"item.look\"", "\"item.look\"")) { e, m -> e.setStyle(m, "label", "gold") })
        val cleaned = edited(edit(noStyle.after) { e, m -> e.deleteStyle(m, "gold") })
        assertNull(collection(cleaned.after).collection.screens[0].root.children[1].template!!.children.single().style)
        val gone = edited(edit(text) { e, m -> e.deleteBinding(m, "scores") })
        val c = collection(gone.after)
        assertNull("the list's items binding is cleared with it", c.collection.screens[0].root.children[1].bindings["items"])
        assertTrue(c.warnings.any { it.code == UiIssueCode.UNRESOLVED_ITEM_FIELD })
    }

    @Test fun anInvalidRecordIsNotWrittenAndNavigationIsSetAndRestoredExactly() {
        val badFields = SceneJson().parseObject("""{"fields":[]}""")
        val bad = edit { e, m -> e.upsertBinding(m, "scores", "list", badFields) } as UiTextResult.Rejected
        assertEquals(UiEditError.INVALID_RESULT, bad.error)
        val arrows = edited(edit { e, m -> e.setScreenNavigation(m, "main", "arrows") })
        assertTrue(arrows.after.contains("\"navigation\": \"arrows\""))
        assertEquals(original, edited(edit(arrows.after) { e, m -> e.setScreenNavigation(m, "main", "tab") }).after)
        assertEquals(UiTextResult.Unchanged, edit { e, m -> e.setScreenNavigation(m, "main", "tab") })
        assertEquals(UiEditError.BAD_VALUE, (edit { e, m -> e.setScreenNavigation(m, "main", "mouse") } as UiTextResult.Rejected).error)
        assertEquals(UiEditError.UNKNOWN_SCREEN, (edit { e, m -> e.setScreenNavigation(m, "nope", "arrows") } as UiTextResult.Rejected).error)
    }

    @Test fun theTreeKnowsTemplateWidgetsAndTheirList() {
        val c = collection(withList()).collection
        val tree = UiTree(c)
        assertEquals("list", tree.parentOf("list-row")!!.id)
        assertEquals("list-row", tree.parentOf("label")!!.id)
        assertEquals("main", tree.screenOf("label")!!.id)
        assertTrue(tree.isInside("label", "list"))
        assertEquals(UiSelection("main", "label"), UiSelection("main", "label").reconcile(c))
    }
}
