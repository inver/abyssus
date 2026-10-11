package net.nevinsky.abyssus.lib.core.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.assets.AssetMetaBinder
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiDocumentAdmissionTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val decoder = UiCollectionDecoder()

    private fun meta(ui: String, header: String = """"format":"abyssus","formatVersion":1,"type":"UI""""): JsonNode =
        json.readObject("""{$header,"additional":{"ui":$ui}}""")

    private fun screen(root: String, id: String = "main") =
        """{"id":"$id","name":"Main","kind":"screen","root":$root}"""

    private fun ui(vararg screens: String, extra: String = "") =
        """{"schemaVersion":1,"entryScreen":"main","screens":[${screens.joinToString()}]$extra}"""

    private fun refusal(node: JsonNode): List<UiIssue> = (decoder.decode(node) as UiAdmission.Refused).issues
    private fun admitted(node: JsonNode) = decoder.decode(node) as UiAdmission.Admitted

    private val minimal = ui(screen("""{"id":"root","type":"panel"}"""))

    @Test fun validMetadataDecodesScreensWidgetsBindingsAndResources() {
        val node = meta(ui(
            screen("""{"id":"root","type":"panel","layout":{"anchor":"topRight","x":-8,"y":8,"width":200},"children":[
                {"id":"title","type":"label","properties":{"text":"Hi"},"bindings":{"text":"score"}},
                {"id":"go","type":"button","actions":{"click":{"type":"openScreen","screenId":"settings"}}}]}"""),
            """{"id":"settings","name":"Settings","kind":"overlay","modal":true,"referenceSize":{"width":1024,"height":768},
                "root":{"id":"s-root","type":"column","layout":{"padding":4,"spacing":2,"align":"stretch"}}}""",
            extra = ""","bindings":[{"name":"score","type":"number","access":"readOnly","sample":3,"min":0,"max":10}],
                "resources":[{"id":"logo","kind":"image","path":"logo.png"}],"styles":[{"name":"big","fontSize":32}]""",
        ))
        val result = admitted(node)
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        val c = result.collection
        assertEquals(listOf("main", "settings"), c.screens.map { it.id })
        assertEquals(UiScreenKind.OVERLAY, c.screens[1].kind)
        assertTrue(c.screens[1].modal)
        assertEquals(1024f, c.screens[1].referenceWidth)
        assertEquals(UI_DEFAULT_REFERENCE_WIDTH, c.screens[0].referenceWidth)
        val root = c.screens[0].root
        assertEquals(UiAnchor.TOP_RIGHT, root.layout.anchor)
        assertEquals(-8f, root.layout.x)
        assertEquals(200f, root.layout.width)
        assertEquals(listOf(UiWidgetType.LABEL, UiWidgetType.BUTTON), root.children.map { it.type })
        assertEquals("score", root.children[0].bindings["text"])
        assertEquals(UiActionType.OPEN_SCREEN, root.children[1].actions.getValue("click").type)
        assertEquals(UiAlign.STRETCH, c.screens[1].root.layout.align)
        assertEquals(UiBindingType.NUMBER, c.binding("score")!!.type)
        assertEquals("logo.png", c.resources.single().path)
        assertEquals("big", c.styles.single().name)
    }

    @Test fun missingOrForeignMarkersAreRefusedWithoutTouchingTheTree() {
        val noMarker = json.readObject("""{"formatVersion":1,"type":"UI","additional":{"ui":$minimal}}""")
        assertEquals(UiIssueCode.FORMAT_MARKER, refusal(noMarker).single().code)
        val foreign = meta(minimal, """"format":"other","formatVersion":1,"type":"UI"""")
        assertEquals(UiIssueCode.FORMAT_MARKER, refusal(foreign).single().code)
        val before = foreign.toString()
        refusal(foreign)
        assertEquals(before, foreign.toString())
        assertEquals(UiIssueCode.NOT_UI_ASSET, refusal(meta(minimal, """"format":"abyssus","formatVersion":1,"type":"MODEL"""")).single().code)
        assertEquals(UiIssueCode.MISSING_UI_BLOCK, refusal(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{}}""")).single().code)
    }

    @Test fun fractionalAndFutureVersionsAreRefused() {
        assertEquals(UiIssueCode.FORMAT_VERSION, refusal(meta(minimal, """"format":"abyssus","formatVersion":1.5,"type":"UI"""")).single().code)
        assertEquals(UiIssueCode.FORMAT_VERSION, refusal(meta(minimal, """"format":"abyssus","formatVersion":2,"type":"UI"""")).single().code)
        for (version in listOf("2", "1.0", "1.5", "\"1\"")) {
            val node = meta(minimal.replace(""""schemaVersion":1""", """"schemaVersion":$version"""))
            assertEquals(version, UiIssueCode.UI_VERSION, refusal(node).single().code)
        }
        assertEquals(UiIssueCode.UI_VERSION, refusal(meta("""{"screens":[]}""")).single().code)
    }

    @Test fun duplicateIdsAreErrors() {
        val twoScreens = meta(ui(screen("""{"id":"a","type":"panel"}"""), screen("""{"id":"b","type":"panel"}""")))
        assertEquals(UiIssueCode.DUPLICATE_SCREEN_ID, refusal(twoScreens).single().code)
        val dupWidget = meta(ui(screen("""{"id":"root","type":"panel","children":[{"id":"x","type":"label"},{"id":"x","type":"label"}]}""")))
        assertEquals(listOf(UiIssue(UiIssueCode.DUPLICATE_WIDGET_ID, "additional.ui.screens[0].root.children[1].id")), refusal(dupWidget))
        val acrossScreens = meta(ui(screen("""{"id":"root","type":"panel"}"""), screen("""{"id":"root","type":"panel"}""", "other")))
        assertEquals(UiIssueCode.DUPLICATE_WIDGET_ID, refusal(acrossScreens).single().code)
        val dupBinding = meta(ui(screen("""{"id":"r","type":"panel"}"""),
            extra = ""","bindings":[{"name":"v","type":"number"},{"name":"v","type":"string"}]"""))
        assertEquals(UiIssueCode.DUPLICATE_BINDING, refusal(dupBinding).single().code)
    }

    @Test fun unknownWidgetsAreInertPlaceholdersThatKeepTheirObject() {
        val node = meta(ui(screen("""{"id":"root","type":"panel","children":[
            {"id":"fancy","type":"hologram","depth":1.000,"children":[{"id":"inner","type":"label"}]},
            {"id":"ok","type":"button"}]}""")))
        val before = node.toString()
        val result = admitted(node)
        val fancy = result.collection.screens[0].root.children[0]
        assertNull(fancy.type)
        assertEquals("hologram", fancy.rawType)
        assertTrue(fancy.children.isEmpty())
        assertEquals(1, fancy.raw.get("children").size())
        assertEquals(UiIssue(UiIssueCode.UNKNOWN_WIDGET, "additional.ui.screens[0].root.children[0]"), result.warnings.single())
        assertEquals(UiWidgetType.BUTTON, result.collection.screens[0].root.children[1].type)
        assertEquals(before, node.toString())
        assertEquals(1.0, fancy.raw.get("depth").doubleValue(), 0.0)
    }

    @Test fun unknownWidgetsKeepTheirAuthoredLayout() {
        val result = admitted(meta(ui(screen("""{"id":"root","type":"panel","children":[
            {"id":"odd","type":"hologram","layout":{"anchor":"center","x":4,"width":30,"height":20}}]}"""))))
        val layout = result.collection.screens[0].root.children[0].layout
        assertEquals(UiAnchor.CENTER, layout.anchor)
        assertEquals(4f, layout.x)
        assertEquals(30f, layout.width)
        // and malformed geometry on an unknown widget is still an error
        assertEquals(UiIssueCode.BAD_GEOMETRY, refusal(meta(ui(screen("""{"id":"root","type":"panel","children":[
            {"id":"odd","type":"hologram","layout":{"width":-1}}]}""")))).first().code)
    }

    @Test fun malformedGeometryIsRefused() {
        fun geometry(layout: String) = refusal(meta(ui(screen("""{"id":"r","type":"panel","layout":$layout}"""))))
        for (layout in listOf("""{"width":-1}""", """{"height":-0.5}""", """{"grow":-2}""", """{"padding":-1}""", """{"spacing":-1}""",
            """{"x":"left"}""", """{"width":"wide"}""", """{"anchor":"middleish"}""", """{"align":"sideways"}""", "5"))
            assertEquals(layout, UiIssueCode.BAD_GEOMETRY, geometry(layout).first().code)
        val nonFinite = json.readObject(meta(minimal).toString())
        (nonFinite.get("additional").get("ui").get("screens").get(0).get("root") as com.fasterxml.jackson.databind.node.ObjectNode)
            .set<JsonNode>("layout", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("width", Double.NaN))
        assertEquals(UiIssueCode.BAD_GEOMETRY, refusal(nonFinite).first().code)
        val badRef = refusal(meta(minimal.replace(""""kind":"screen",""", "").replace(""""root":""", """"referenceSize":{"width":0,"height":720},"root":""")))
        assertEquals(UiIssueCode.BAD_GEOMETRY, badRef.first().code)
    }

    @Test fun depthAndWidgetCountLimitsAreCheckedBeforeBuilding() {
        fun nested(depth: Int): String =
            (1..depth).joinToString("") { """{"id":"w$it","type":"panel","children":[""" } + (1..depth).joinToString("") { "]}" }
        assertTrue(decoder.decode(meta(ui(screen(nested(UI_MAX_DEPTH))))) is UiAdmission.Admitted)
        assertEquals(UiIssueCode.TOO_DEEP, refusal(meta(ui(screen(nested(UI_MAX_DEPTH + 1))))).single().code)
        fun flat(count: Int) = """{"id":"root","type":"panel","children":[${(1 until count).joinToString(",") { """{"id":"c$it","type":"label"}""" }}]}"""
        assertTrue(decoder.decode(meta(ui(screen(flat(UI_MAX_WIDGETS))))) is UiAdmission.Admitted)
        assertEquals(UiIssueCode.TOO_MANY_WIDGETS, refusal(meta(ui(screen(flat(UI_MAX_WIDGETS + 1))))).single().code)
    }

    @Test fun imageLimitIsPerSide() {
        assertTrue(uiImageWithinLimits(UI_MAX_IMAGE_SIDE, UI_MAX_IMAGE_SIDE))
        assertFalse(uiImageWithinLimits(UI_MAX_IMAGE_SIDE + 1, 16))
        assertFalse(uiImageWithinLimits(16, UI_MAX_IMAGE_SIDE + 1))
        assertFalse(uiImageWithinLimits(0, 16))
    }

    @Test fun leafWidgetsRejectChildrenAndBadBindingsAreRefused() {
        assertEquals(UiIssueCode.CHILDREN_ON_LEAF, refusal(meta(ui(screen("""{"id":"r","type":"button","children":[{"id":"c","type":"label"}]}""")))).single().code)
        for (binding in listOf("""{"name":"v","type":"vector"}""", """{"name":"v","type":"number","sample":"x"}""",
            """{"name":"v","type":"number","min":5,"max":1}""", """{"name":"v","type":"number","step":0}""", """{"name":"v"}"""))
            assertEquals(binding, UiIssueCode.BAD_BINDING, refusal(meta(ui(screen("""{"id":"r","type":"panel"}"""), extra = ""","bindings":[$binding]"""))).first().code)
        assertEquals(UiIssueCode.UNKNOWN_ENTRY_SCREEN, refusal(meta(minimal.replace("\"entryScreen\":\"main\"", "\"entryScreen\":\"gone\""))).single().code)
    }

    @Test fun unresolvedReferencesAreWarnings() {
        val result = admitted(meta(ui(screen("""{"id":"r","type":"panel","children":[
            {"id":"l","type":"label","bindings":{"text":"missing"}},
            {"id":"b","type":"button","actions":{"click":{"type":"openScreen","screenId":"gone"}}},
            {"id":"far","type":"button","actions":{"click":{"type":"openScreen","assetName":"other","screenId":"x"}}}]}"""))))
        assertEquals(listOf(UiIssueCode.UNRESOLVED_BINDING, UiIssueCode.UNRESOLVED_SCREEN), result.warnings.map { it.code }.sortedBy { it.ordinal })
    }

    @Test fun binderBindsUiMetaAndLeavesOmittedDefaultsOut() {
        val node = meta(minimal)
        val before = node.toString()
        val bound = AssetMetaBinder(json).bind("menus", node)
        assertEquals(MetaType.UI, bound.type)
        val admission = (bound.additional as UiMeta).admission as UiAdmission.Admitted
        assertEquals("main", admission.collection.entryScreen)
        assertEquals(before, node.toString())
        val noEntry = admitted(meta(minimal.replace(""""entryScreen":"main",""", "")))
        assertNull(noEntry.collection.entryScreen)
        assertNull(noEntry.collection.screens[0].root.layout.width)
    }

    @Test fun theDocumentedExampleIsAdmittedWithoutWarnings() {
        val docs = java.io.File(checkNotNull(System.getProperty("abyssus.testData")), "../../../../../docs/ai/file-formats.md").canonicalFile
        val text = docs.readText()
        val block = text.substringAfter("<!-- ui-minimal-example -->").substringAfter("```json").substringBefore("```")
        val result = admitted(json.readObject(block))
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        assertEquals(listOf("main", "hud"), result.collection.screens.map { it.id })
        assertEquals(UiScreenKind.OVERLAY, result.collection.screen("hud")!!.kind)
        assertEquals(UiBindingAccess.WRITE, result.collection.binding("volume")!!.access)
    }
}
