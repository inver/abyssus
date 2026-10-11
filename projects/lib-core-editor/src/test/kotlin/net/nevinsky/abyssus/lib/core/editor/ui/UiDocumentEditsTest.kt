package net.nevinsky.abyssus.lib.core.editor.ui

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiDocumentEditsTest {
    private val editor = UiTextEditor()

    /** Pretty printed, with an unknown member, a widget kind this build does not know, and number text that must survive. */
    private val raw = """
{
  "format": "abyssus",
  "formatVersion": 1,
  "type": "UI",
  "extension": {
    "keep": 1.000
  },
  "additional": {
    "ui": {
      "schemaVersion": 1,
      "entryScreen": "main",
      "futureField": [ 2.50, 1.0E-4 ],
      "bindings": [
        {
          "name": "volume",
          "type": "number",
          "access": "write",
          "sample": 0.50
        }
      ],
      "screens": [
        {
          "id": "main",
          "name": "Main",
          "root": {
            "id": "root",
            "type": "panel",
            "children": [
              {
                "id": "menu",
                "type": "column",
                "layout": {
                  "anchor": "center",
                  "spacing": 10
                },
                "children": [
                  {
                    "id": "title",
                    "type": "label",
                    "properties": {
                      "text": "Hello"
                    }
                  },
                  {
                    "id": "start",
                    "type": "button",
                    "style": "big",
                    "layout": {
                      "grow": 1
                    },
                    "bindings": {
                      "enabled": "volume"
                    }
                  },
                  {
                    "id": "ghost",
                    "type": "hologram",
                    "depth": 3.000
                  }
                ]
              },
              {
                "id": "hud",
                "type": "panel",
                "layout": {
                  "anchor": "topRight",
                  "x": -16,
                  "y": 16,
                  "width": 200
                }
              }
            ]
          }
        }
      ],
      "styles": [
        {
          "name": "big",
          "fontSize": 32
        }
      ]
    }
  }
}
"""

    /** The fixture in the printer's own layout, so "unchanged text" can be compared exactly. */
    private val original: String = SceneJson().pretty(raw)!!

    private fun edit(text: String = original, expected: String? = null, op: (UiDocumentEdits, JsonNode) -> UiEditResult) = editor.apply(text, expected, op)

    private fun edited(result: UiTextResult): UiTextResult.Edited = result as UiTextResult.Edited

    private fun rejected(result: UiTextResult, error: UiEditError): UiTextResult.Rejected {
        val r = result as UiTextResult.Rejected
        assertEquals(error, r.error)
        return r
    }

    private fun widgetIds(text: String): List<String> {
        val ids = mutableListOf<String>()
        fun walk(n: JsonNode) { n.get("id")?.takeIf { it.isTextual }?.let { ids += it.asText() }; n.get("children")?.forEach(::walk) }
        SceneJson().parse(text).path("additional").path("ui").path("screens").forEach { walk(it.get("root")) }
        return ids
    }

    @Test fun aPropertyEditChangesOnlyThatLineAndKeepsEverythingElseExactly() {
        val result = edited(edit { e, m -> e.setProperty(m, "title", "text", JsonNodeFactory.instance.textNode("Bye")) })
        assertEquals(original.replace("\"text\": \"Hello\"", "\"text\": \"Bye\""), result.after)
        assertTrue(result.after.contains("1.000") && result.after.contains("2.50") && result.after.contains("1.0E-4") && result.after.contains("3.000"))
        assertEquals(original, result.before)
    }

    @Test fun undoRestoresTheExactPreviousTextAndReversingAnEditRoundTrips() {
        val moved = edited(edit { e, m -> e.reorderWidget(m, "start", 0) })
        assertNotEquals(original, moved.after)
        assertEquals(listOf("root", "menu", "start", "title", "ghost", "hud"), widgetIds(moved.after))
        val back = edited(edit(moved.after) { e, m -> e.reorderWidget(m, "start", 1) })
        assertEquals("reordering back gives the original text byte for byte", original, back.after)
        assertEquals(moved.after, back.before)
    }

    @Test fun addingWidgetsAssignsUniqueIdsAndTypeDefaults() {
        var text = original
        val ids = mutableListOf<String>()
        repeat(3) {
            val r = edited(edit(text) { e, m -> e.addWidget(m, "menu", "button") })
            ids += (r.result as UiEditResult.Added).id
            text = r.after
        }
        assertEquals(listOf("button", "button-2", "button-3"), ids)
        assertEquals(listOf("root", "menu", "title", "start", "ghost", "button", "button-2", "button-3", "hud"), widgetIds(text))
        val node = SceneJson().parse(text)
        assertEquals("Button", node.path("additional").path("ui").path("screens").get(0).path("root").path("children").get(0).path("children").get(3).path("properties").path("text").asText())
        // at an index, into the root panel
        val at = edited(edit { e, m -> e.addWidget(m, "root", "slider", index = 0) })
        assertEquals(listOf("root", "slider", "menu", "title", "start", "ghost", "hud"), widgetIds(at.after))
    }

    @Test fun addingToANonContainerOrWithAnUnknownTypeIsRejectedWithoutChange() {
        rejected(edit { e, m -> e.addWidget(m, "title", "label") }, UiEditError.NOT_A_CONTAINER)
        rejected(edit { e, m -> e.addWidget(m, "menu", "hologram") }, UiEditError.UNKNOWN_TYPE)
        rejected(edit { e, m -> e.addWidget(m, "nowhere", "label") }, UiEditError.UNKNOWN_WIDGET)
    }

    @Test fun duplicatingRepairsIdsForTheWholeSubtreeAndKeepsUnknownMembers() {
        val r = edited(edit { e, m -> e.duplicateWidget(m, "menu") })
        assertEquals(UiEditResult.Added("menu-2"), r.result)
        assertEquals(listOf("root", "menu", "title", "start", "ghost", "menu-2", "title-2", "start-2", "ghost-2", "hud"), widgetIds(r.after))
        assertEquals("the copy keeps the unknown widget's own fields", 2, Regex("\"depth\": 3.000").findAll(r.after).count())
        assertEquals("and its binding", 2, Regex("\"enabled\": \"volume\"").findAll(r.after).count())
        val again = edited(edit(r.after) { e, m -> e.duplicateWidget(m, "menu") })
        assertEquals(UiEditResult.Added("menu-3"), again.result)
        rejected(edit { e, m -> e.duplicateWidget(m, "root") }, UiEditError.ROOT_WIDGET)
    }

    @Test fun reparentingConvertsTheLayoutPolicyAndKeepsTheIdAndOrder() {
        // free placement is dropped under a column; a panel drops grow
        val intoColumn = edited(edit { e, m -> e.reparentWidget(m, "hud", "menu", index = 1) })
        assertEquals(listOf("root", "menu", "title", "hud", "start", "ghost"), widgetIds(intoColumn.after))
        val hud = SceneJson().parse(intoColumn.after).path("additional").path("ui").path("screens").get(0).path("root").path("children").get(0).path("children").get(1)
        assertEquals("hud", hud.path("id").asText())
        assertEquals("""{"width":200}""", hud.path("layout").toString())
        val intoPanel = edited(edit { e, m -> e.reparentWidget(m, "start", "hud") })
        val start = SceneJson().parse(intoPanel.after).path("additional").path("ui").path("screens").get(0).path("root").path("children").get(1).path("children").get(0)
        assertEquals("start", start.path("id").asText())
        assertTrue("grow is dropped and the empty layout removed", start.path("layout").isMissingNode)
        assertEquals("big", start.path("style").asText())
        // and the original is restorable from the result's `before`
        assertEquals(original, intoPanel.before)
    }

    @Test fun invalidHierarchyIsRejectedWithNoChange() {
        rejected(edit { e, m -> e.reparentWidget(m, "hud", "title") }, UiEditError.NOT_A_CONTAINER)
        rejected(edit { e, m -> e.reparentWidget(m, "menu", "title") }, UiEditError.HIERARCHY_CYCLE)
        rejected(edit { e, m -> e.reparentWidget(m, "menu", "menu") }, UiEditError.HIERARCHY_CYCLE)
        rejected(edit { e, m -> e.reparentWidget(m, "root", "menu") }, UiEditError.ROOT_WIDGET)
        val nested = edited(edit { e, m -> e.addWidget(m, "menu", "panel") })
        val inner = (nested.result as UiEditResult.Added).id
        rejected(edit(nested.after) { e, m -> e.reparentWidget(m, "menu", inner) }, UiEditError.HIERARCHY_CYCLE)
        rejected(edit { e, m -> e.deleteWidget(m, "root") }, UiEditError.ROOT_WIDGET)
        rejected(edit { e, m -> e.reorderWidget(m, "root", 0) }, UiEditError.ROOT_WIDGET)
    }

    @Test fun deletingRemovesTheSubtreeAndAnEmptyChildrenList() {
        val r = edited(edit { e, m -> e.deleteWidget(m, "menu") })
        assertEquals(listOf("root", "hud"), widgetIds(r.after))
        val all = edited(edit(r.after) { e, m -> e.deleteWidget(m, "hud") })
        assertFalse(all.after.contains("\"children\""))
        assertEquals(UiTextResult.Unchanged, edit { e, m -> e.reorderWidget(m, "title", 0) })
    }

    @Test fun layoutValuesAreValidatedAndNumbersKeepTheirShape() {
        val ok = edited(edit { e, m -> e.setLayout(m, "hud", "width", 240.0) })
        assertTrue(ok.after.contains("\"width\": 240"))
        assertTrue(edited(edit { e, m -> e.setLayout(m, "hud", "y", 12.5) }).after.contains("\"y\": 12.5"))
        for ((field, value) in listOf("width" to -1.0, "height" to Double.NaN, "grow" to -0.1, "x" to Double.POSITIVE_INFINITY, "anchor" to "middle", "align" to "sideways", "weight" to 1.0))
            rejected(edit { e, m -> e.setLayout(m, "hud", field, value) }, UiEditError.BAD_VALUE)
        assertTrue(edited(edit { e, m -> e.setLayout(m, "hud", "anchor", "bottom") }).after.contains("\"anchor\": \"bottom\""))
        val removed = edited(edit { e, m -> e.setLayout(m, "start", "grow", null) })
        assertFalse(removed.after.contains("\"grow\""))
        assertFalse("an emptied layout object is removed", removed.after.substringAfter("\"id\": \"start\"").substringBefore("\"id\": \"ghost\"").contains("\"layout\""))
        assertEquals(UiTextResult.Unchanged, edit { e, m -> e.setLayout(m, "hud", "width", 200.0) })
    }

    @Test fun bindingsStylesAndActionsAreEditedAndCleanedUp() {
        val withAction = edited(edit { e, m -> e.setAction(m, "start", "click", SceneJson().parse("""{"type":"emitAction","name":"go"}""")) })
        assertTrue(withAction.after.contains("\"emitAction\""))
        rejected(edit { e, m -> e.setAction(m, "start", "click", SceneJson().parse("""{"name":"go"}""")) }, UiEditError.BAD_VALUE)
        val bound = edited(edit { e, m -> e.setBinding(m, "title", "text", "volume") })
        assertTrue(bound.after.contains("\"text\": \"volume\""))
        // deleting a binding clears the widgets that used it, in the same edit
        val gone = edited(edit { e, m -> e.deleteBinding(m, "volume") })
        assertFalse(gone.after.contains("\"enabled\"")); assertFalse(gone.after.contains("\"bindings\""))
        rejected(edit { e, m -> e.deleteBinding(m, "nope") }, UiEditError.UNKNOWN_NAME)
        val styled = edited(edit { e, m -> e.upsertStyle(m, "fancy", SceneJson().parse("""{"fontSize":20,"color":"#ff0000"}""")) })
        assertTrue(styled.after.contains("\"fancy\""))
        val noStyle = edited(edit { e, m -> e.deleteStyle(m, "big") })
        assertFalse(noStyle.after.contains("\"style\": \"big\"")); assertFalse(noStyle.after.contains("\"styles\""))
        val newBinding = edited(edit { e, m -> e.upsertBinding(m, "score", "number", SceneJson().parseObject("""{"sample":3}""")) })
        assertTrue(newBinding.after.contains("\"score\""))
        rejected(edit { e, m -> e.upsertBinding(m, "bad", "vector") }, UiEditError.BAD_VALUE)
    }

    @Test fun screensAreAddedRenamedAndDeletedWithTheEntryKeptValid() {
        val added = edited(edit { e, m -> e.addScreen(m, "Pause Menu", "overlay") })
        assertEquals(UiEditResult.Added("pause-menu"), added.result)
        assertTrue(added.after.contains("\"kind\": \"overlay\""))
        val renamed = edited(edit(added.after) { e, m -> e.renameScreen(m, "pause-menu", "Paused") })
        assertTrue(renamed.after.contains("\"name\": \"Paused\""))
        rejected(edit { e, m -> e.setEntryScreen(m, "pause-menu") }, UiEditError.UNKNOWN_SCREEN)
        rejected(edit(added.after) { e, m -> e.setEntryScreen(m, "pause-menu") }, UiEditError.BAD_VALUE)
        val second = edited(edit { e, m -> e.addScreen(m, "Settings") })
        val deletedEntry = edited(edit(second.after) { e, m -> e.deleteScreen(m, "main") })
        assertTrue(deletedEntry.after.contains("\"entryScreen\": \"settings\""))
        val none = edited(edit { e, m -> e.deleteScreen(m, "main") })
        assertFalse(none.after.contains("entryScreen"))
    }

    @Test fun staleGesturesAreRefusedAndMatchingOnesApply() {
        rejected(edit(original + " ", expected = original) { e, m -> e.reorderWidget(m, "start", 0) }, UiEditError.STALE)
        assertTrue(edit(original, expected = original) { e, m -> e.reorderWidget(m, "start", 0) } is UiTextResult.Edited)
    }

    @Test fun unsupportedOrInvalidDocumentsAreNeverEditedAndInvalidResultsAreRefused() {
        val future = original.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        val refused = rejected(edit(future) { e, m -> e.setProperty(m, "title", "text", null) }, UiEditError.NOT_EDITABLE)
        assertTrue(refused.issues.isNotEmpty())
        val notNative = original.replace("\"format\": \"abyssus\"", "\"format\": \"other\"")
        assertTrue(edit(notNative) { e, m -> e.setProperty(m, "title", "text", null) } is UiTextResult.Refused)
        assertTrue(edit("{not json") { e, m -> e.setProperty(m, "title", "text", null) } is UiTextResult.Refused)
        // an edit that would leave an invalid collection is not written
        val bad = rejected(edit { e, m -> e.upsertBinding(m, "volume", "number", SceneJson().parseObject("""{"sample":"loud"}""")) }, UiEditError.INVALID_RESULT)
        assertTrue(bad.issues.isNotEmpty())
    }

    @Test fun everyEditedResultIsStillAdmittedUi() {
        var text = original
        val steps: List<(UiDocumentEdits, JsonNode) -> UiEditResult> = listOf(
            { e, m -> e.addWidget(m, "menu", "slider") }, { e, m -> e.duplicateWidget(m, "menu") },
            { e, m -> e.reparentWidget(m, "hud", "menu-2") }, { e, m -> e.setBinding(m, "slider", "value", "volume") },
            { e, m -> e.deleteWidget(m, "title-2") }, { e, m -> e.addScreen(m, "Other") },
        )
        for (step in steps) text = edited(edit(text, op = step)).after
        assertTrue(UiCollectionDecoder().decode(SceneJson().parse(text)) is UiAdmission.Admitted)
    }

    @Test fun resourcesAreDeclaredOnceAndRemovedCleanly() {
        val added = edited(edit { e, m -> e.addResource(m, "logo", "image", "images/logo.png") })
        assertEquals(UiEditResult.Added("logo"), added.result)
        assertTrue(added.after.contains("\"path\": \"images/logo.png\""))
        rejected(edit(added.after) { e, m -> e.addResource(m, "logo", "image", "images/other.png") }, UiEditError.DUPLICATE_ID)
        rejected(edit { e, m -> e.addResource(m, "x", "video", "x.mp4") }, UiEditError.BAD_VALUE)
        rejected(edit { e, m -> e.addResource(m, " ", "image", "x.png") }, UiEditError.BAD_VALUE)
        val removed = edited(edit(added.after) { e, m -> e.deleteResource(m, "logo") })
        assertEquals("deleting the last resource restores the original text", original, removed.after)
        rejected(edit { e, m -> e.deleteResource(m, "logo") }, UiEditError.UNKNOWN_NAME)
    }
}
