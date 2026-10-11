package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiListBindingTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    private fun collection(widgets: String, bindings: String): UiCollection =
        (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"bindings":$bindings,
            "styles":[{"name":"gold","color":"#ffd700"}],
            "screens":[{"id":"s","root":{"id":"root","type":"column","children":[$widgets]}}]}}}""")) as UiAdmission.Admitted).collection

    private val scores = """{"name":"scores","type":"list","fields":[{"name":"rank","type":"number"},{"name":"name","type":"string"},{"name":"style","type":"string"}],
        "sample":[{"rank":9,"name":"Sample","style":"gold"}]}"""

    private val table = """{"id":"table","type":"list","properties":{"maxRows":3},"bindings":{"items":"scores"},
        "template":{"id":"row","type":"row","children":[{"id":"rank","type":"label","bindings":{"text":"item.rank","style":"item.style"}},{"id":"who","type":"label","bindings":{"text":"item.name"}}]},
        "empty":{"id":"none","type":"label","properties":{"text":"No flights yet"}}}"""

    private fun rows(vararg items: Map<String, UiValue>) = UiValue.Rows(items.toList())
    private fun row(rank: Double, name: String, style: String = "") = mapOf("rank" to UiValue.Num(rank), "name" to UiValue.Text(name), "style" to UiValue.Text(style))

    private fun view(c: UiCollection, vararg published: Pair<String, UiValue>) = UiBindingResolver(c).view(UiSnapshot(mapOf(*published)))

    @Test fun publishedRowsReachWidgetsAsPerRowValues() {
        val c = collection(table, "[$scores]")
        val v = view(c, "scores" to rows(row(1.0, "Ace", "gold"), row(2.0, "Bo")))
        assertEquals(2, v.rows("scores").size)
        assertEquals(UiValue.Num(2.0), v.value(uiRowBinding("scores", 1, "rank")))
        assertEquals(UiValue.Text("Ace"), v.value("scores#0.name"))
        assertTrue(v.diagnostics.toString(), v.diagnostics.isEmpty())
        val expanded = expandLists(c.screens[0].root, v).root.children[0]
        assertEquals(listOf("row#0", "row#1"), expanded.children.map { it.id })
        val first = expanded.children[0]
        assertEquals("rank#0", first.children[0].id)
        assertEquals("1", v.text(first.children[0]))
        assertEquals("Bo", v.text(expanded.children[1].children[1]))
        assertEquals("gold", v.styleName(first.children[0]))
        assertNull("an empty style field falls back to the authored (none) style", v.styleName(expanded.children[1].children[0]))
        assertFalse("the authored tree is untouched", c.screens[0].root.children[0].children.isNotEmpty())
    }

    @Test fun aMissingOrWrongTypeListShowsTheSampleWithADiagnostic() {
        val c = collection(table, "[$scores]")
        val missing = view(c)
        assertEquals(listOf(UiBindingDiagnostic(UiBindingIssue.MISSING_VALUE, "scores")), missing.diagnostics)
        assertEquals(UiValue.Num(9.0), missing.value("scores#0.rank"))
        val wrong = view(c, "scores" to UiValue.Text("nope"))
        assertEquals(UiBindingIssue.INCOMPATIBLE_VALUE, wrong.diagnostics.single().code)
        assertEquals("Sample", wrong.rows("scores").single().getValue("name").let { (it as UiValue.Text).value })
    }

    @Test fun badOrMissingItemFieldsAreZeroedAndReportedWithoutTouchingOtherRows() {
        val c = collection(table, "[$scores]")
        val odd = mapOf("rank" to UiValue.Text("first"), "name" to UiValue.Text("Ace"), "extra" to UiValue.Num(1.0))
        val v = view(c, "scores" to rows(odd, row(2.0, "Bo", "gold")))
        assertEquals(UiValue.Num(0.0), v.value("scores#0.rank"))
        assertEquals(UiValue.Text(""), v.value("scores#0.style"))
        assertNull("undeclared fields are dropped", v.value("scores#0.extra"))
        assertEquals(UiValue.Num(2.0), v.value("scores#1.rank"))
        assertEquals(setOf("0.rank", "0.style"), v.diagnostics.filter { it.code == UiBindingIssue.ITEM_FIELD }.map { it.property }.toSet())
        assertTrue(v.diagnostics.none { it.property?.startsWith("1.") == true })
        val nan = view(c, "scores" to rows(mapOf("rank" to UiValue.Num(Double.NaN), "name" to UiValue.Text("x"), "style" to UiValue.Text(""))))
        assertEquals(UiValue.Num(0.0), nan.value("scores#0.rank"))
    }

    @Test fun anEmptyListShowsOnlyTheEmptyStateAndMaxRowsCaps() {
        val c = collection(table, "[$scores]")
        val none = expandLists(c.screens[0].root, view(c, "scores" to rows())).root.children[0]
        assertEquals(listOf("none"), none.children.map { it.id })
        val five = expandLists(c.screens[0].root, view(c, "scores" to rows(*Array(5) { row(it + 1.0, "n$it") }))).root.children[0]
        assertEquals(listOf("row#0", "row#1", "row#2"), five.children.map { it.id })
    }

    @Test fun theWidgetLimitStopsRowsAndReportsOnce() {
        val c = collection(table.replace("\"maxRows\":3", "\"maxRows\":100"), "[$scores]")
        val v = view(c, "scores" to rows(*Array(50) { row(it + 1.0, "n$it") }))
        val limited = expandLists(c.screens[0].root, v, budget = 20)
        val shown = limited.root.children[0].children.size
        assertTrue("rows that fit: $shown", shown in 1..6)
        assertEquals(listOf(UiBindingIssue.ROW_LIMIT), limited.diagnostics.map { it.code })
        assertEquals("table", limited.diagnostics.single().widgetId)
    }

    @Test fun rowWidgetsAreCheckedAgainstTheRecordsTypes() {
        val wrongType = table.replace("\"text\":\"item.rank\",\"style\":\"item.style\"", "\"text\":\"item.rank\",\"style\":\"item.rank\"")
        val plan = planBindings(collection(wrongType, "[$scores]"))
        assertEquals(UiBindingIssue.INCOMPATIBLE_TARGET, plan.diagnostics.single().code)
        assertTrue(plan.isIgnored("rank#4", "style"))
        val unknownField = planBindings(collection(table.replace("\"item.name\"", "\"item.nickname\""), "[$scores]"))
        assertEquals(UiBindingIssue.UNKNOWN_BINDING, unknownField.diagnostics.single().code)
        assertTrue(planBindings(collection(table, "[$scores]")).diagnostics.isEmpty())
        val itemsNotList = planBindings(collection(table.replace("\"items\":\"scores\"", "\"items\":\"title\""), "[$scores,{\"name\":\"title\",\"type\":\"string\"}]"))
        assertTrue(itemsNotList.diagnostics.any { it.code == UiBindingIssue.INCOMPATIBLE_TARGET && it.widgetId == "table" })
        assertTrue("and its rows then have no record to bind to", itemsNotList.diagnostics.any { it.code == UiBindingIssue.UNKNOWN_BINDING })
        val itemsOnLabel = planBindings(collection("""{"id":"x","type":"label","bindings":{"items":"scores"}}""", "[$scores]"))
        assertEquals(UiBindingIssue.UNSUPPORTED_PROPERTY, itemsOnLabel.diagnostics.single().code)
    }

    @Test fun aListCannotFeedATextOrBeWrittenBack() {
        val onText = planBindings(collection("""{"id":"x","type":"label","bindings":{"text":"scores"}}""", "[$scores]"))
        assertEquals(UiBindingIssue.INCOMPATIBLE_TARGET, onText.diagnostics.single().code)
        val c = collection(table, "[$scores]")
        val session = UiBindingSession(c) { UiWriteResult.Accepted(it.value) }
        val label = expandLists(c.screens[0].root, session.view()).root
        assertTrue(session.commit(label, UiValue.Rows(emptyList())) is UiCommitOutcome.Refused)
    }

    @Test fun aBoundStyleOnAnyWidgetUsesTheBoundNameAndIgnoresAnIncompatibleOne() {
        val bindings = "[$scores,{\"name\":\"look\",\"type\":\"string\"},{\"name\":\"n\",\"type\":\"number\"}]"
        val c = collection("""{"id":"a","type":"label","style":"fixed","bindings":{"style":"look"}},{"id":"b","type":"label","style":"fixed","bindings":{"style":"n"}}""", bindings)
        val v = view(c, "look" to UiValue.Text("gold"), "n" to UiValue.Num(1.0))
        val (a, b) = c.screens[0].root.children
        assertEquals("gold", v.styleName(a))
        assertEquals("a number cannot name a style: the authored one stays", "fixed", v.styleName(b))
        assertEquals(UiBindingIssue.INCOMPATIBLE_TARGET, v.diagnostics.single { it.widgetId == "b" }.code)
        assertEquals("a bound name that is empty uses the authored style", "fixed", view(c, "look" to UiValue.Text("")).styleName(a))
    }
}
