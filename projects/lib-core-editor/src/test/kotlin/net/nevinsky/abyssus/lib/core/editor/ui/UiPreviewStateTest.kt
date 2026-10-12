package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiPreviewStateTest {
    private val text = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
        "entryScreen":"hud",
        "bindings":[
          {"name":"volume","type":"number","access":"write","sample":0.5,"min":0,"max":1,"step":0.25},
          {"name":"score","type":"number","sample":7},
          {"name":"name","type":"string","access":"write","sample":"Pilot","maxLength":5},
          {"name":"muted","type":"boolean","access":"write","sample":false},
          {"name":"nothing","type":"string"}],
        "screens":[
          {"id":"main","root":{"id":"m","type":"panel"}},
          {"id":"hud","kind":"overlay","root":{"id":"h","type":"panel","children":[
            {"id":"vol","type":"slider","bindings":{"value":"volume"}},
            {"id":"scoreSlider","type":"slider","bindings":{"value":"score"}},
            {"id":"nameField","type":"textField","bindings":{"text":"name"}}]}}]}}}""")!!

    private val collection: UiCollection get() = (UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted).collection
    private val factory = UiPreviewState.Factory()

    private fun widget(c: UiCollection, id: String): UiWidget = c.screens.flatMap { it.root.children + it.root }.first { it.id == id }

    @Test fun startsFromTheAuthoredSamplesAndTheEntryScreen() {
        val state = factory.of(collection)
        assertEquals(UiValue.Num(0.5), state.samples["volume"])
        assertEquals(UiValue.Text("Pilot"), state.samples["name"])
        assertEquals("a binding without a sample starts at the type's zero", UiValue.Text(""), state.samples["nothing"])
        assertEquals("hud", state.startScreen)
        assertEquals(UiValue.Bool(false), state.snapshot()["muted"])
        assertTrue(state.log.isEmpty())
        assertEquals(0, state.generation)
    }

    @Test fun interactionOnlyChangesSamplesAndNeverTheDocument() {
        val c = collection
        val state = factory.of(c)
        val session = UiBindingSession(c, state.writer())
        session.publish(state.snapshot())
        assertEquals(UiCommitOutcome.Applied("volume", UiValue.Num(0.75)), session.commit(widget(c, "vol"), UiValue.Num(0.76)))
        assertEquals(UiValue.Num(0.75), state.samples["volume"])
        // the shown value follows the sample everywhere
        assertEquals(0.75, session.publish(state.snapshot()).number(widget(c, "vol"), "value", -1.0), 0.0)
        // a read-only binding refuses the host-side write and keeps its sample
        val refused = session.commit(widget(c, "scoreSlider"), UiValue.Num(1.0))
        assertTrue(refused is UiCommitOutcome.Refused)
        assertEquals(UiValue.Num(7.0), state.samples["score"])
        state.actionSink().emit("startGame")
        // nothing the preview did reaches the text the editor holds
        assertEquals(SceneJson().pretty(text), text)
        val again = (UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted).collection
        assertEquals(0.5, (again.binding("volume")!!.sample!!.doubleValue()), 0.0)
    }

    @Test fun theWriterRefusesReadOnlyUnknownAndMistypedWrites() {
        val state = factory.of(collection)
        val writer = state.writer()
        assertEquals(UiWriteResult.Rejected("read-only"), writer.write(UiWrite("score", UiValue.Num(1.0), "w")))
        assertEquals(UiWriteResult.Rejected("unknown binding"), writer.write(UiWrite("ghost", UiValue.Num(1.0), "w")))
        assertEquals(UiWriteResult.Rejected("wrong type"), writer.write(UiWrite("volume", UiValue.Text("x"), "w")))
        assertEquals(UiWriteResult.Accepted(UiValue.Bool(true)), writer.write(UiWrite("muted", UiValue.Bool(true), "w")))
        assertEquals(listOf(UiLogKind.REJECTED_WRITE, UiLogKind.REJECTED_WRITE, UiLogKind.REJECTED_WRITE, UiLogKind.WRITE), state.log.map { it.kind })
        assertEquals(UiValue.Bool(true), state.samples["muted"])
    }

    @Test fun theSampleEditorChecksTypeAndConstraints() {
        val state = factory.of(collection)
        assertEquals(SampleResult.Applied(UiValue.Num(0.3)), state.setSample("volume", " 0.3 "))
        assertEquals(SampleResult.Rejected(SampleProblem.ABOVE_MAXIMUM), state.setSample("volume", "1.5"))
        assertEquals(SampleResult.Rejected(SampleProblem.BELOW_MINIMUM), state.setSample("volume", "-1"))
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_NUMBER), state.setSample("volume", "loud"))
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_NUMBER), state.setSample("volume", "NaN"))
        assertEquals("the rejected values left the sample alone", UiValue.Num(0.3), state.samples["volume"])
        assertEquals(SampleResult.Applied(UiValue.Bool(true)), state.setSample("muted", "On"))
        assertEquals(SampleResult.Rejected(SampleProblem.NOT_A_BOOLEAN), state.setSample("muted", "maybe"))
        assertEquals(SampleResult.Applied(UiValue.Text("Ace")), state.setSample("name", "Ace"))
        assertEquals(SampleResult.Rejected(SampleProblem.TOO_LONG), state.setSample("name", "Toolong"))
        assertEquals(SampleResult.Rejected(SampleProblem.UNKNOWN_BINDING), state.setSample("ghost", "1"))
        assertEquals("read-only bindings can still be given sample data", SampleResult.Applied(UiValue.Num(99.0)), state.setSample("score", "99"))
    }

    @Test fun actionsAreLoggedNotRunAndTheLogIsBounded() {
        val state = UiPreviewState.Factory(maxLog = 5).of(collection)
        val sink = state.actionSink()
        assertTrue(sink.emit("startGame"))
        repeat(9) { assertTrue(sink.emit("a$it")) }
        val log = state.log
        assertEquals(5, log.size)
        assertEquals(listOf("a4", "a5", "a6", "a7", "a8"), log.map { it.text })
        assertEquals("sequence numbers keep counting past the trimmed lines", listOf(6L, 7L, 8L, 9L, 10L), log.map { it.sequence })
        assertTrue(log.all { it.kind == UiLogKind.ACTION })
    }

    @Test fun resetRestoresAuthoredSamplesClearsTheLogAndSignalsANewStart() {
        val state = factory.of(collection)
        state.setSample("volume", "0.9"); state.setSample("name", "Ace"); state.actionSink().emit("startGame")
        assertEquals(3, state.log.size)
        state.reset()
        assertEquals(UiValue.Num(0.5), state.samples["volume"])
        assertEquals(UiValue.Text("Pilot"), state.samples["name"])
        assertTrue(state.log.isEmpty())
        assertEquals(1, state.generation)
        state.reset()
        assertEquals(2, state.generation)
    }

    @Test fun aChangedDefinitionIsAFreshStateWithItsOwnStartScreen() {
        val old = factory.of(collection)
        old.setSample("volume", "0.9"); old.actionSink().emit("x")
        val changed = text.replace("\"sample\": 0.5", "\"sample\": 0.25").replace("\"entryScreen\": \"hud\"", "\"entryScreen\": \"main\"")
        val c2 = (UiCollectionDecoder().decode(SceneJson().parse(changed)) as UiAdmission.Admitted).collection
        val fresh = factory.of(c2)
        assertEquals("edits to the previous definition are not carried over", UiValue.Num(0.25), fresh.samples["volume"])
        assertTrue(fresh.log.isEmpty())
        assertEquals("main", fresh.startScreen)
        assertEquals("a chosen start screen overrides the entry", "hud", factory.of(c2, "hud").startScreen)
        assertEquals("the old state is untouched", UiValue.Num(0.9), old.samples["volume"])
    }
}
