package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutEngine
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.layout.UiMeasurer
import net.nevinsky.abyssus.lib.core.ui.layout.UiSizeF
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiLayoutEditsTest {
    private val json = SceneJson()
    private val editor = UiTextEditor()
    private val hits = UiHitTest()

    private val text: String = json.pretty("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"screens":[{"id":"main","root":
        {"id":"root","type":"panel","children":[
          {"id":"free","type":"button","layout":{"x":50,"y":40,"width":100,"height":40}},
          {"id":"list","type":"column","layout":{"x":400,"y":100,"width":200,"height":300,"spacing":10},"children":[
            {"id":"a","type":"label","layout":{"height":30}},{"id":"b","type":"label","layout":{"height":30}},{"id":"c","type":"label","layout":{"height":30}}]},
          {"id":"box","type":"panel","layout":{"x":700,"y":100,"width":300,"height":200},"children":[
            {"id":"inner","type":"button","layout":{"anchor":"center","x":5,"width":80,"height":30}}]},
          {"id":"hidden","type":"panel","layout":{"x":0,"y":600,"width":50,"height":50},"children":[
            {"id":"clipped","type":"button","layout":{"x":40,"y":0,"width":60,"height":50}}]}]}}]}}}""")!!

    private fun collection(source: String = text): UiCollection =
        (UiCollectionDecoder().decode(json.parse(source)) as UiAdmission.Admitted).collection

    private fun layout(c: UiCollection, width: Float = 1280f, height: Float = 720f): UiLayoutResult =
        UiLayoutEngine(UiMeasurer { UiSizeF(100f, 20f) }).resolve(c.screens[0], width, height)

    private fun gesture(id: String, startX: Float, startY: Float, width: Float = 1280f, height: Float = 720f, source: String = text): Pair<UiLayoutGesture, UiLayoutResult> {
        val c = collection(source)
        val l = layout(c, width, height)
        return UiLayoutGesture(c, l, id, startX, startY, source) to l
    }

    private fun applied(commit: UiGestureCommit?, source: String = text): UiTextResult.Edited {
        val result = editor.apply(source, commit!!.baseText, commit.edit)
        return result as UiTextResult.Edited
    }

    private fun ids(source: String): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: com.fasterxml.jackson.databind.JsonNode) { n.get("id")?.takeIf { it.isTextual }?.let { out += it.asText() }; n.get("children")?.forEach(::walk) }
        walk(json.parse(source).path("additional").path("ui").path("screens").get(0).get("root"))
        return out
    }

    @Test fun pickingUsesScaledCoordinatesAndIgnoresTheLetterbox() {
        val c = collection()
        val l = layout(c, 1024f, 768f) // scale 0.8, 96 units of letterbox above and below
        val fit = l.fit!!
        assertEquals(0.8f, fit.scale, 1e-6f)
        val px = fit.toViewportX(60f); val py = fit.toViewportY(50f)
        assertEquals("free", hits.pick(l, px, py))
        assertEquals("root", hits.pick(l, fit.toViewportX(5f), fit.toViewportY(5f)))
        assertNull("the letterbox is not part of the screen", hits.pick(l, 100f, 10f))
        assertNull(hits.pick(l, -5f, 300f))
        assertEquals("a", hits.pick(l, fit.toViewportX(410f), fit.toViewportY(110f)))
    }

    @Test fun clippedPartsOfAChildCannotBePicked() {
        val l = layout(collection())
        val fit = l.fit!!
        // "clipped" sticks out of its 50x50 parent from x=40; only the first 10 units are visible and pickable
        assertEquals("clipped", hits.pick(l, fit.toViewportX(45f), fit.toViewportY(620f)))
        assertEquals("root", hits.pick(l, fit.toViewportX(80f), fit.toViewportY(620f)))
    }

    @Test fun draggingAPanelChildMovesItsOffsetsByTheScaledDelta() {
        val (g, l) = gesture("free", 0f, 0f, 1024f, 768f)
        val fit = l.fit!!
        val sx = fit.toViewportX(60f); val sy = fit.toViewportY(50f)
        val gesture = UiLayoutGesture(collection(), l, "free", sx, sy, text)
        gesture.update(sx + 40f, sy + 24f) // 40 / 0.8 = 50 and 24 / 0.8 = 30 logical units
        val preview = gesture.update(sx + 80f, sy + 40f) as UiGesturePreview.Move // the last position wins: 100 and 50 units
        assertEquals(150f, preview.x, 1e-3f); assertEquals(90f, preview.y, 1e-3f)
        val edited = applied(gesture.commit())
        val node = json.parse(edited.after).path("additional").path("ui").path("screens").get(0).path("root").path("children").get(0).path("layout")
        assertEquals(150, node.path("x").asInt()); assertEquals(90, node.path("y").asInt())
        assertEquals("one drag is one edit with the exact text to undo to", text, edited.before)
        assertNotNull(g)
    }

    @Test fun aClickWithoutMovementCommitsNothing() {
        val (g, _) = gesture("free", 60f, 50f)
        g.update(61f, 51f)
        g.update(60f, 50f)
        assertNull(g.commit())
        val (untouched, _) = gesture("free", 60f, 50f)
        assertNull(untouched.commit())
    }

    @Test fun draggingARowOrColumnChildReordersInsteadOfOffsetting() {
        val (g, l) = gesture("a", 410f, 110f)
        val b = l.nodes.getValue("b").bounds
        // drop below c's middle: a ends up last
        val c = l.nodes.getValue("c").bounds
        val preview = g.update(410f, c.y + c.height / 2 + 4)
        assertEquals(UiGesturePreview.Reorder("list", 2), preview)
        assertNotNull(b)
        val edited = applied(g.commit())
        assertEquals(listOf("root", "free", "list", "b", "c", "a", "box", "inner", "hidden", "clipped"), ids(edited.after))
        val moved = json.parse(edited.after).path("additional").path("ui").path("screens").get(0).path("root").path("children").get(1).path("children").get(2)
        assertEquals("a", moved.path("id").asText())
        assertEquals("""{"height":30}""", moved.path("layout").toString()) // no numeric offsets are written for a flow child
        // dropping above everything puts it first
        val (up, ul) = gesture("c", 410f, ul(l, "c"))
        assertEquals(UiGesturePreview.Reorder("list", 0), up.update(410f, ul.nodes.getValue("a").bounds.y + 1))
    }

    private fun ul(l: UiLayoutResult, id: String) = l.nodes.getValue(id).bounds.let { it.y + it.height / 2 }

    @Test fun droppingOverAnotherContainerReparentsAndConvertsTheLayoutPolicy() {
        // panel child "free" dropped into the column, between a and b
        val (g, l) = gesture("free", 60f, 50f)
        val a = l.nodes.getValue("a").bounds
        val preview = g.update(420f, a.bottom + 2f)
        assertEquals(UiGesturePreview.Reparent("list", 1), preview)
        val edited = applied(g.commit())
        assertEquals(listOf("root", "list", "a", "free", "b", "c", "box", "inner", "hidden", "clipped"), ids(edited.after))
        val free = json.parse(edited.after).path("additional").path("ui").path("screens").get(0).path("root").path("children").get(0).path("children").get(1).path("layout")
        assertEquals("""{"width":100,"height":40}""", free.toString()) // anchor / x / y gone under a column

        // a flow child dropped into a panel loses grow and keeps its id
        val (g2, l2) = gesture("b", 410f, ul(l, "b"))
        val box = l2.nodes.getValue("box").bounds
        assertEquals(UiGesturePreview.Reparent("box", null), g2.update(box.x + 150f, box.y + 150f))
        val moved = applied(g2.commit())
        assertEquals(listOf("root", "free", "list", "a", "c", "box", "inner", "b", "hidden", "clipped"), ids(moved.after))
    }

    @Test fun anAbortedOrRepeatedlyUpdatedGestureNeverWritesAndOnlyTheFinalStateCommits() {
        val (g, l) = gesture("free", 60f, 50f)
        repeat(20) { g.update(60f + it * 3f, 50f + it) }
        val after = g.update(100f, 90f) as UiGesturePreview.Move
        assertEquals(90f, after.x, 1e-3f); assertEquals(80f, after.y, 1e-3f)
        val commit = g.commit()!!
        assertEquals(text, commit.baseText)
        val edited = applied(commit)
        assertEquals("final offsets only", "\"x\": 90", Regex("\"x\": 90").find(edited.after)!!.value)
        assertNotNull(l)
    }

    @Test fun aGestureWhoseDocumentChangedMeanwhileIsRefusedAsStale() {
        val (g, _) = gesture("free", 60f, 50f)
        g.update(100f, 90f)
        val commit = g.commit()!!
        val changed = text.replace("\"id\": \"free\"", "\"id\": \"free\" ")
        val result = editor.apply(changed.replace("free\" ", "free\""), commit.baseText, commit.edit)
        // the text differs from the gesture's start only when it really changed
        val concurrently = text.replace("\"spacing\": 10", "\"spacing\": 12")
        assertEquals(UiTextResult.Rejected(UiEditError.STALE), editor.apply(concurrently, commit.baseText, commit.edit))
        assertNotNull(result)
    }

    @Test fun selectionFollowsTheDocument() {
        val c = collection()
        assertEquals(UiSelection("main", "inner"), UiSelection("main", "inner").reconcile(c))
        assertEquals("a deleted widget falls back to its screen's root", UiSelection("main", "root"), UiSelection("main", "gone").reconcile(c))
        assertEquals(UiSelection("main", "root"), UiSelection().reconcile(c))
        val edited = applied(UiLayoutGesture(c, layout(c), "free", 60f, 50f, text).also { it.update(400f, 150f) }.commit())
        assertEquals(UiSelection("main", "free"), UiSelection("main", "free").reconcile(collection(edited.after)))
    }
}
