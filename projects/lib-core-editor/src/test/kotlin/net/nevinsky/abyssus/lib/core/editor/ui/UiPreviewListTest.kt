package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.state.UiBindingResolver
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiPreviewListTest {
    private val text = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
        "bindings":[{"name":"scores","type":"list","fields":[{"name":"rank","type":"number"},{"name":"name","type":"string"},{"name":"ok","type":"boolean"}],
          "sample":[{"rank":1,"name":"Ace","ok":true},{"rank":2,"name":"Bo","ok":false}]}],
        "screens":[{"id":"main","root":{"id":"root","type":"list","bindings":{"items":"scores"},"template":{"id":"row","type":"label","bindings":{"text":"item.name"}}}}]}}}""")!!

    private val collection: UiCollection get() = (UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted).collection
    private fun state() = UiPreviewState.Factory().of(collection)
    private fun rows(s: UiPreviewState) = (s.samples["scores"] as UiValue.Rows).items

    @Test fun theAuthoredSampleRowsAreTheStartingWorld() {
        val s = state()
        assertEquals(2, rows(s).size)
        assertEquals(UiValue.Text("Ace"), rows(s)[0]["name"])
        assertEquals(UiValue.Bool(false), rows(s)[1]["ok"])
        val view = UiBindingResolver(collection).view(s.snapshot())
        assertEquals(2, view.rows("scores").size)
        assertTrue(view.diagnostics.toString(), view.diagnostics.isEmpty())
    }

    @Test fun rowsAreAddedRemovedAndEditedWithTheRecordsTypes() {
        val s = state()
        assertTrue(s.addRow("scores") is SampleResult.Applied)
        assertEquals(UiValue.Num(0.0), rows(s)[2]["rank"])
        assertTrue(s.addRow("scores", mapOf("rank" to "9", "name" to "Cy", "ok" to "yes")) is SampleResult.Applied)
        assertEquals(listOf("Ace", "Bo", "", "Cy"), rows(s).map { (it["name"] as UiValue.Text).value })
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_NUMBER), s.addRow("scores", mapOf("rank" to "nine")))
        assertEquals("a rejected row is not added", 4, rows(s).size)
        assertTrue(s.setRowField("scores", 0, "rank", "5.5") is SampleResult.Applied)
        assertEquals(UiValue.Num(5.5), rows(s)[0]["rank"])
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_BOOLEAN), s.setRowField("scores", 0, "ok", "maybe"))
        assertEquals(SampleResult.Rejected(SampleProblem.NO_SUCH_FIELD), s.setRowField("scores", 0, "nick", "x"))
        assertEquals(SampleResult.Rejected(SampleProblem.NO_SUCH_ROW), s.setRowField("scores", 9, "rank", "1"))
        assertTrue(s.removeRow("scores", 1) is SampleResult.Applied)
        assertEquals(listOf("Ace", "", "Cy"), rows(s).map { (it["name"] as UiValue.Text).value })
        assertEquals(SampleResult.Rejected(SampleProblem.NO_SUCH_ROW), s.removeRow("scores", 7))
    }

    @Test fun aListIsNotEditedAsOneValueAndOtherBindingsAreRejectedForRowEdits() {
        val s = state()
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_LIST), s.setSample("scores", "[]"))
        assertEquals(SampleResult.Rejected(SampleProblem.UNKNOWN_BINDING), s.addRow("ghost"))
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_LIST), s.removeRow("ghost", 0))
    }

    @Test fun resetBringsBackTheAuthoredRowsAndTheWriterRefusesALiveListWrite() {
        val s = state()
        s.addRow("scores"); s.removeRow("scores", 0); s.setRowField("scores", 0, "name", "Zed")
        assertTrue(s.log.size >= 3)
        s.reset()
        assertEquals(listOf("Ace", "Bo"), rows(s).map { (it["name"] as UiValue.Text).value })
        assertTrue(s.log.isEmpty())
        val refused = s.writer().write(net.nevinsky.abyssus.lib.core.ui.state.UiWrite("scores", UiValue.Rows(emptyList()), "w"))
        assertTrue(refused is net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult.Rejected)
        assertEquals("the document text is unchanged by all of it", SceneJson().pretty(text), text)
    }

    @Test fun aDefinitionChangeStartsFromTheNewAuthoredRows() {
        val old = state()
        old.addRow("scores")
        val changed = text.replace("\"name\": \"Ace\"", "\"name\": \"Zoe\"")
        val c2 = (UiCollectionDecoder().decode(SceneJson().parse(changed)) as UiAdmission.Admitted).collection
        val fresh = UiPreviewState.Factory().of(c2)
        assertEquals(2, (fresh.samples["scores"] as UiValue.Rows).items.size)
        assertEquals(UiValue.Text("Zoe"), (fresh.samples["scores"] as UiValue.Rows).items[0]["name"])
        assertEquals(3, rows(old).size)
    }
}
