package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiBindingTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    private val collection: UiCollection = ((UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{
        "schemaVersion":1,
        "bindings":[
          {"name":"health","type":"number","sample":10},
          {"name":"maxHealth","type":"number","sample":100},
          {"name":"volume","type":"number","access":"write","sample":0.5,"min":0,"max":1,"step":0.25},
          {"name":"score","type":"number","sample":7},
          {"name":"muted","type":"boolean","access":"write","sample":false},
          {"name":"name","type":"string","access":"write","sample":"Pilot","maxLength":5},
          {"name":"title","type":"string","sample":"T"}],
        "screens":[{"id":"s","root":{"id":"root","type":"panel","children":[
          {"id":"bar","type":"progressBar","bindings":{"value":"health","maximum":"maxHealth"}},
          {"id":"label","type":"label","properties":{"prefix":"HP ","suffix":"%","precision":1},"bindings":{"text":"health","visible":"muted"}},
          {"id":"vol","type":"slider","bindings":{"value":"volume"}},
          {"id":"scoreSlider","type":"slider","bindings":{"value":"score"}},
          {"id":"mute","type":"checkbox","bindings":{"checked":"muted"}},
          {"id":"nameField","type":"textField","bindings":{"text":"name"}},
          {"id":"badSlider","type":"slider","bindings":{"value":"title"}},
          {"id":"ghost","type":"label","bindings":{"text":"nobody"}},
          {"id":"plain","type":"label","properties":{"text":"hello"}}]}}]}}}""")))
        as UiAdmission.Admitted).collection

    private fun widget(id: String): UiWidget {
        fun find(w: UiWidget): UiWidget? = if (w.id == id) w else w.children.firstNotNullOfOrNull(::find)
        return find(collection.screens[0].root)!!
    }

    private fun num(v: Double) = UiValue.Num(v)
    private val resolver = UiBindingResolver(collection)

    private class CountingWriter(var answer: (UiWrite) -> UiWriteResult = { UiWriteResult.Accepted(it.value) }) : UiValueWriter {
        val writes = mutableListOf<UiWrite>()
        override fun write(write: UiWrite): UiWriteResult { writes += write; return answer(write) }
    }

    @Test fun liveValuesDriveProgressTextAndVisibility() {
        val view = resolver.view(UiSnapshot(mapOf("health" to num(25.0), "maxHealth" to num(100.0), "muted" to UiValue.Bool(true))))
        assertEquals(0.25f, view.progress(widget("bar")), 0f)
        assertEquals("HP 25.0%", view.text(widget("label")))
        assertTrue(view.visible(widget("label")))
        assertEquals("hello", view.text(widget("plain")))
        val later = resolver.view(UiSnapshot(mapOf("health" to num(50.0), "maxHealth" to num(100.0))))
        assertEquals(0.5f, later.progress(widget("bar")), 0f)
        assertFalse(later.visible(widget("label")))
    }

    @Test fun missingOrIncompatibleValuesFallBackToSamplesWithDiagnostics() {
        val view = resolver.view(UiSnapshot(mapOf("health" to UiValue.Text("lots"), "maxHealth" to num(Double.NaN))))
        assertEquals(10.0, (view.value("health") as UiValue.Num).value, 0.0)
        assertEquals(100.0, (view.value("maxHealth") as UiValue.Num).value, 0.0)
        val codes = view.diagnostics.filter { it.binding == "health" || it.binding == "maxHealth" || it.binding == "volume" }
        assertTrue(codes.any { it.code == UiBindingIssue.INCOMPATIBLE_VALUE && it.binding == "health" })
        assertTrue(codes.any { it.code == UiBindingIssue.INCOMPATIBLE_VALUE && it.binding == "maxHealth" })
        assertTrue(codes.any { it.code == UiBindingIssue.MISSING_VALUE && it.binding == "volume" })
        assertEquals(0.1f, view.progress(widget("bar")), 1e-6f)
    }

    @Test fun zeroMaximumShowsEmptyProgressWithoutDividing() {
        val view = resolver.view(UiSnapshot(mapOf("health" to num(5.0), "maxHealth" to num(0.0))))
        assertEquals(0f, view.progress(widget("bar")), 0f)
        assertTrue(view.diagnostics.any { it.code == UiBindingIssue.INVALID_MAXIMUM && it.widgetId == "bar" })
        assertEquals(1f, resolver.view(UiSnapshot(mapOf("health" to num(500.0), "maxHealth" to num(100.0)))).progress(widget("bar")), 0f)
        assertEquals(0f, resolver.view(UiSnapshot(mapOf("health" to num(-5.0), "maxHealth" to num(100.0)))).progress(widget("bar")), 0f)
    }

    @Test fun readOnlyIncompatibleAndUnknownTargetsAreDisabledOrIgnored() {
        val plan = resolver.plan
        assertTrue(plan.isDisabled("scoreSlider"))
        assertTrue(plan.isDisabled("badSlider"))
        assertFalse(plan.isDisabled("vol"))
        assertTrue(plan.isIgnored("ghost", "text"))
        val codes = plan.diagnostics.associate { it.widgetId to it.code }
        assertEquals(UiBindingIssue.READ_ONLY_TARGET, codes["scoreSlider"])
        assertEquals(UiBindingIssue.INCOMPATIBLE_TARGET, codes["badSlider"])
        assertEquals(UiBindingIssue.UNKNOWN_BINDING, codes["ghost"])
        val session = UiBindingSession(collection, CountingWriter())
        val outcome = session.commit(widget("scoreSlider"), num(3.0)) as UiCommitOutcome.Refused
        assertEquals(UiBindingIssue.READ_ONLY_TARGET, outcome.diagnostic.code)
        assertFalse(session.view().enabled(widget("scoreSlider")))
        assertEquals("", session.view().text(widget("ghost")))
    }

    @Test fun normalizedWriteUpdatesEveryConsumerWithoutAFeedbackLoop() {
        val writer = CountingWriter { UiWriteResult.Accepted(num(0.75)) }
        val session = UiBindingSession(collection, writer)
        session.publish(UiSnapshot(mapOf("volume" to num(0.5))))
        val outcome = session.commit(widget("vol"), num(0.76))
        assertEquals(UiCommitOutcome.Applied("volume", num(0.75)), outcome)
        assertEquals(listOf(UiWrite("volume", num(0.75), "vol")), writer.writes) // snapped to the 0.25 step before the host saw it
        assertEquals(0.75, session.view().number(widget("vol"), "value", -1.0), 0.0)
        session.publish(UiSnapshot(mapOf("volume" to num(0.25))))
        assertEquals(0.25, session.view().number(widget("vol"), "value", -1.0), 0.0)
        assertEquals(1, writer.writes.size) // publishing never writes
    }

    @Test fun hostNormalizationWinsOverTheProposal() {
        val writer = CountingWriter { UiWriteResult.Accepted(num(0.5)) }
        val session = UiBindingSession(collection, writer)
        session.publish(UiSnapshot(mapOf("volume" to num(0.0))))
        assertEquals(UiCommitOutcome.Applied("volume", num(0.5)), session.commit(widget("vol"), num(1.0)))
        assertEquals(0.5, session.view().number(widget("vol"), "value", -1.0), 0.0)
    }

    @Test fun rejectedWriteRestoresTheAuthoritativeValue() {
        val session = UiBindingSession(collection, CountingWriter { UiWriteResult.Rejected("name taken") })
        session.publish(UiSnapshot(mapOf("name" to UiValue.Text("Ace"))))
        val outcome = session.commit(widget("nameField"), UiValue.Text("Bob"))
        assertEquals(UiCommitOutcome.Rejected("name", "name taken", UiValue.Text("Ace")), outcome)
        assertEquals("Ace", session.view().text(widget("nameField")))
    }

    @Test fun proposalsAreValidatedBeforeTheHostSeesThem() {
        val writer = CountingWriter()
        val session = UiBindingSession(collection, writer)
        session.publish(UiSnapshot(mapOf("volume" to num(0.5), "name" to UiValue.Text("Ace"))))
        assertEquals(UiCommitOutcome.Applied("volume", num(1.0)), session.commit(widget("vol"), num(7.0))) // clamped
        assertTrue(session.commit(widget("vol"), UiValue.Text("x")) is UiCommitOutcome.Refused)
        assertTrue(session.commit(widget("vol"), num(Double.NaN)) is UiCommitOutcome.Refused)
        assertTrue(session.commit(widget("nameField"), UiValue.Text("toolongname")) is UiCommitOutcome.Refused)
        assertEquals(listOf(num(1.0)), writer.writes.map { it.value })
        // an unchanged value does not write
        assertEquals(UiCommitOutcome.Applied("volume", num(1.0)), session.commit(widget("vol"), num(1.0)))
        assertEquals(1, writer.writes.size)
        assertTrue(session.commit(widget("mute"), UiValue.Bool(true)) is UiCommitOutcome.Applied)
        assertTrue(session.commit(widget("plain"), UiValue.Bool(true)) is UiCommitOutcome.Refused)
    }

    @Test fun commitTimingFollowsTheWidgetKind() {
        assertFalse(commitsOn(UiWidgetType.SLIDER, UiEditGesture.DRAG))
        assertTrue(commitsOn(UiWidgetType.SLIDER, UiEditGesture.RELEASE))
        assertTrue(commitsOn(UiWidgetType.SLIDER, UiEditGesture.KEY_STEP))
        assertTrue(commitsOn(UiWidgetType.CHECKBOX, UiEditGesture.TOGGLE))
        assertFalse(commitsOn(UiWidgetType.TEXT_FIELD, UiEditGesture.TYPING))
        assertTrue(commitsOn(UiWidgetType.TEXT_FIELD, UiEditGesture.ENTER))
        assertTrue(commitsOn(UiWidgetType.TEXT_FIELD, UiEditGesture.FOCUS_LOST))
        assertFalse(commitsOn(UiWidgetType.BUTTON, UiEditGesture.TOGGLE))
        // a drag across several values followed by a release is one write
        val writer = CountingWriter()
        val session = UiBindingSession(collection, writer)
        session.publish(UiSnapshot(mapOf("volume" to num(0.0))))
        for (g in listOf(UiEditGesture.DRAG, UiEditGesture.DRAG, UiEditGesture.RELEASE))
            if (commitsOn(UiWidgetType.SLIDER, g)) session.commit(widget("vol"), num(0.5))
        assertEquals(1, writer.writes.size)
    }

    @Test fun numberFormatting() {
        assertEquals("3", formatValue(num(3.0)))
        assertEquals("2.5", formatValue(num(2.5)))
        assertEquals("2.50", formatValue(num(2.5), 2))
        assertEquals("true", formatValue(UiValue.Bool(true)))
    }
}
