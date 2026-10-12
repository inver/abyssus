package net.nevinsky.abyssus.lib.core.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiListAdmissionTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val decoder = UiCollectionDecoder()

    private val record = """[{"name":"rank","type":"number"},{"name":"name","type":"string"},{"name":"style","type":"string"}]"""
    private val scores = """{"name":"scores","type":"list","fields":$record,"sample":[{"rank":1,"name":"Ace","style":"gold"},{"rank":2,"name":"Bo","style":"plain"}]}"""

    private fun asset(widgets: String, bindings: String = "[$scores]", screenExtra: String = ""): JsonNode =
        json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"bindings":$bindings,
            "screens":[{"id":"s"$screenExtra,"root":{"id":"root","type":"panel","children":[$widgets]}}]}}}""")

    private val list = """{"id":"table","type":"list","layout":{"spacing":2},"properties":{"maxRows":10},"bindings":{"items":"scores"},
        "template":{"id":"row","type":"row","children":[
          {"id":"rank","type":"label","bindings":{"text":"item.rank","style":"item.style"}},{"id":"who","type":"label","bindings":{"text":"item.name"}}]},
        "empty":{"id":"none","type":"label","properties":{"text":"No flights yet"}}}"""

    private fun admitted(n: JsonNode) = decoder.decode(n) as UiAdmission.Admitted
    private fun refusal(n: JsonNode) = (decoder.decode(n) as UiAdmission.Refused).issues

    @Test fun aListWithItsRecordTemplateAndEmptyStateIsAdmitted() {
        val result = admitted(asset(list, screenExtra = ",\"navigation\":\"arrows\""))
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        val c = result.collection
        assertEquals(UiNavigation.ARROWS, c.screens[0].navigation)
        val binding = c.binding("scores")!!
        assertEquals(UiBindingType.LIST, binding.type)
        assertEquals(listOf("rank" to UiBindingType.NUMBER, "name" to UiBindingType.STRING, "style" to UiBindingType.STRING), binding.fields.map { it.name to it.type })
        val widget = c.screens[0].root.children[0]
        assertEquals(UiWidgetType.LIST, widget.type)
        assertEquals("row", widget.template!!.id)
        assertEquals(2, widget.template!!.children.size)
        assertEquals("none", widget.empty!!.id)
        assertEquals("item.rank", widget.template!!.children[0].bindings["text"])
        assertEquals(UiNavigation.TAB, admitted(asset(list)).collection.screens[0].navigation)
    }

    @Test fun unknownMembersOfAListSurviveInTheRawTree() {
        val extended = list.replace("\"properties\"", "\"futureThing\":{\"a\":1.000},\"properties\"")
        val node = asset(extended)
        val before = node.toString()
        val widget = admitted(node).collection.screens[0].root.children[0]
        assertEquals(1.0, widget.raw.get("futureThing").get("a").doubleValue(), 0.0)
        assertEquals(before, node.toString())
    }

    @Test fun badRecordsAreRefused() {
        fun bindingIssue(binding: String) = refusal(asset(list, "[$binding]")).first { it.code == UiIssueCode.BAD_BINDING }
        bindingIssue("""{"name":"scores","type":"list"}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[]}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[{"name":"a","type":"number"},{"name":"a","type":"string"}]}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[{"name":"a","type":"list"}]}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[{"name":"a","type":"vector"}]}""")
        bindingIssue("""{"name":"scores","type":"list","access":"write","fields":[{"name":"a","type":"number"}]}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[{"name":"a","type":"number"}],"sample":[{"a":"x"}]}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[{"name":"a","type":"number"}],"sample":[{"b":1}]}""")
        bindingIssue("""{"name":"scores","type":"list","fields":[{"name":"a","type":"number"}],"sample":{"a":1}}""")
        assertTrue(decoder.decode(asset(list, """[{"name":"scores","type":"list","fields":[{"name":"a","type":"number"}],"sample":[]}]""")) is UiAdmission.Admitted)
    }

    @Test fun aListNeedsATemplateAndTemplateIdsStayUnique() {
        val noTemplate = list.substringBefore("\"template\"").trimEnd().trimEnd(',') + "}"
        assertEquals(UiIssueCode.MISSING_TEMPLATE, refusal(asset(noTemplate)).single().code)
        val clash = list.replace("\"id\":\"who\"", "\"id\":\"rank\"")
        assertEquals(UiIssueCode.DUPLICATE_WIDGET_ID, refusal(asset(clash)).single().code)
        val hash = list.replace("\"id\":\"who\"", "\"id\":\"who#1\"")
        assertEquals(UiIssueCode.MALFORMED, refusal(asset(hash)).single().code)
    }

    @Test fun rowWidgetsMustNameFieldsTheRecordDeclaresAndOnlyInsideRows() {
        val bad = list.replace("\"item.name\"", "\"item.nickname\"")
        assertEquals(listOf(UiIssue(UiIssueCode.UNRESOLVED_ITEM_FIELD, "additional.ui.screens[0].root.children[0].template.children[1].bindings.text")), admitted(asset(bad)).warnings)
        val outside = """{"id":"lone","type":"label","bindings":{"text":"item.rank"}}"""
        assertEquals(UiIssueCode.ITEM_OUTSIDE_LIST, admitted(asset(outside)).warnings.single().code)
        val inEmpty = list.replace("\"properties\":{\"text\":\"No flights yet\"}", "\"bindings\":{\"text\":\"item.rank\"}")
        assertEquals(UiIssueCode.ITEM_OUTSIDE_LIST, admitted(asset(inEmpty)).warnings.single().code)
        val undeclaredList = list.replace("\"items\":\"scores\"", "\"items\":\"ghost\"")
        val codes = admitted(asset(undeclaredList)).warnings.map { it.code }
        assertTrue(UiIssueCode.UNRESOLVED_BINDING in codes && UiIssueCode.UNRESOLVED_ITEM_FIELD in codes)
        assertTrue("a template may also use ordinary bindings", admitted(asset(list.replace("\"item.name\"", "\"scores2\""), "[$scores,{\"name\":\"scores2\",\"type\":\"string\"}]")).warnings.isEmpty())
    }

    @Test fun templateAndEmptyCountTowardTheDepthAndWidgetLimits() {
        fun nest(n: Int) = (1..n).joinToString("") { """{"id":"n$it","type":"panel","children":[""" } + (1..n).joinToString("") { "]}" }
        // list at depth 2, template at 3, then the nest adds n more: 63 nested panels still fit the 64 limit, 64 do not
        val fits = list.replace("\"template\":{\"id\":\"row\",\"type\":\"row\",\"children\":[", "\"template\":{\"id\":\"row\",\"type\":\"row\",\"children\":[${nest(61)},")
        assertTrue(decoder.decode(asset(fits)) is UiAdmission.Admitted)
        val tooDeep = list.replace("\"template\":{\"id\":\"row\",\"type\":\"row\",\"children\":[", "\"template\":{\"id\":\"row\",\"type\":\"row\",\"children\":[${nest(64)},")
        assertEquals(UiIssueCode.TOO_DEEP, refusal(asset(tooDeep)).single().code)
        val many = list.replace("\"children\":[\n          {\"id\":\"rank\"", "\"children\":[" + (1..UI_MAX_WIDGETS).joinToString(",") { """{"id":"c$it","type":"label"}""" } + ",\n          {\"id\":\"rank\"")
        assertEquals(UiIssueCode.TOO_MANY_WIDGETS, refusal(asset(many)).single().code)
    }

    @Test fun anUnknownNavigationSchemeIsRefused() {
        assertEquals(UiIssueCode.MALFORMED, refusal(asset(list, screenExtra = ",\"navigation\":\"mouse\"")).single().code)
    }

    @Test fun boundStylesAreAcceptedOnAnyWidgetButNotNonStrings() {
        val ok = admitted(asset("""{"id":"l","type":"label","bindings":{"style":"scores2"}}""", "[$scores,{\"name\":\"scores2\",\"type\":\"string\"}]"))
        assertTrue(ok.warnings.isEmpty())
        assertEquals("scores2", ok.collection.screens[0].root.children[0].bindings["style"])
    }
}
