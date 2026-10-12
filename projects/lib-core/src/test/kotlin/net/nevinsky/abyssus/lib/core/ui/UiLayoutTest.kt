package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.layout.*
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiLayoutTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    private fun screen(root: String, refW: Int = 1280, refH: Int = 720): UiScreen {
        val meta = json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
            "screens":[{"id":"s","referenceSize":{"width":$refW,"height":$refH},"root":$root}]}}}""")
        return ((UiCollectionDecoder().decode(meta)) as UiAdmission.Admitted).collection.screens.single()
    }

    /** Every leaf measures 100 x 20 unless the widget has a `text` property, then 10 per character x 20. */
    private val measurer = UiMeasurer { w ->
        val text = w.properties["text"]?.asText()
        UiSizeF(if (text != null) 10f * text.length else 100f, 20f)
    }
    private val engine = UiLayoutEngine(measurer)

    private fun UiLayoutResult.rect(id: String) = nodes.getValue(id).bounds

    @Test fun fitsReferenceUniformlyAndCenters() {
        val wide = fitReference(1280f, 720f, 1920f, 1080f)!!
        assertEquals(1.5f, wide.scale, 0f); assertEquals(0f, wide.offsetX, 0f); assertEquals(0f, wide.offsetY, 0f)
        val boxy = fitReference(1280f, 720f, 1024f, 768f)!!
        assertEquals(0.8f, boxy.scale, 1e-6f); assertEquals(0f, boxy.offsetX, 0f); assertEquals(96f, boxy.offsetY, 1e-4f)
        val tall = fitReference(1280f, 720f, 800f, 800f)!!
        assertEquals(0.625f, tall.scale, 1e-6f); assertEquals(175f, tall.offsetY, 1e-3f)
        assertEquals(640f, boxy.toLogicalX(boxy.toViewportX(640f)), 1e-3f)
    }

    @Test fun aRootPanelCentresAnAnchoredColumnButARootColumnIgnoresItsOwnAnchor() {
        val centred = engine.resolve(screen("""{"id":"root","type":"panel","children":[
            {"id":"col","type":"column","layout":{"anchor":"center","spacing":10},"children":[
              {"id":"a","type":"label","layout":{"width":200,"height":40}},{"id":"b","type":"label","layout":{"width":200,"height":40}}]}]}"""), 1280f, 720f)
        val col = centred.rect("col")
        assertEquals(640f, col.x + col.width / 2f, 1e-3f)
        assertEquals(360f, col.y + col.height / 2f, 1e-3f)
        // a root fills the reference rectangle, so its own anchor changes nothing and its children flow from the top
        val bare = engine.resolve(screen("""{"id":"root","type":"column","layout":{"anchor":"center"},"children":[
            {"id":"a","type":"label","layout":{"width":200,"height":40}}]}"""), 1280f, 720f)
        assertEquals(0f, bare.rect("root").y, 0f)
        assertEquals(720f, bare.rect("root").height, 0f)
        assertEquals(0f, bare.rect("a").y, 0f)
    }

    @Test fun hudKeepsEdgeOffsetsAndColumnStaysCenteredAtBothResolutions() {
        val ui = screen("""{"id":"root","type":"panel","children":[
            {"id":"hud","type":"progressBar","layout":{"anchor":"topRight","x":-16,"y":16,"width":200,"height":16}},
            {"id":"menu","type":"column","layout":{"anchor":"center","spacing":10,"align":"center"},"children":[
              {"id":"a","type":"button"},{"id":"b","type":"button","layout":{"width":200}}]}]}""")
        for ((w, h) in listOf(1280f to 720f, 1024f to 768f)) {
            val result = engine.resolve(ui, w, h)
            assertTrue(result.diagnostics.isEmpty())
            val hud = result.rect("hud")
            assertEquals(1280f - 16f, hud.right, 1e-3f); assertEquals(16f, hud.y, 1e-3f)
            val menu = result.rect("menu")
            assertEquals(640f, menu.x + menu.width / 2, 1e-3f); assertEquals(360f, menu.y + menu.height / 2, 1e-3f)
            val a = result.rect("a"); val b = result.rect("b")
            assertEquals(a.bottom + 10f, b.y, 1e-3f)
            assertEquals(640f, a.x + a.width / 2, 1e-3f); assertEquals(640f, b.x + b.width / 2, 1e-3f)
            assertEquals(listOf("root", "hud", "menu", "a", "b"), result.order)
        }
    }

    @Test fun rowAllocatesFixedMeasuredAndGrowSpace() {
        val ui = screen("""{"id":"root","type":"row","layout":{"padding":10,"spacing":5,"align":"stretch"},"children":[
            {"id":"fixed","type":"label","layout":{"width":100}},
            {"id":"grow1","type":"panel","layout":{"grow":1}},
            {"id":"grow3","type":"panel","layout":{"grow":3}},
            {"id":"text","type":"label","properties":{"text":"abcd"}}]}""", 1000, 100)
        val result = engine.resolve(ui, 1000f, 100f)
        // inner width 980; fixed 100 + text 40 + 3 gaps of 5 => 825 left, grow 1:3
        assertEquals(100f, result.rect("fixed").width, 1e-3f)
        assertEquals(825f / 4, result.rect("grow1").width, 1e-3f)
        assertEquals(825f * 3 / 4, result.rect("grow3").width, 1e-3f)
        assertEquals(40f, result.rect("text").width, 1e-3f)
        assertEquals(10f, result.rect("fixed").x, 1e-3f)
        assertEquals(10f, result.rect("fixed").y, 1e-3f); assertEquals(80f, result.rect("fixed").height, 1e-3f)
        assertEquals(990f, result.rect("text").right, 1e-3f)
    }

    @Test fun columnCrossAxisAlignment() {
        fun x(align: String): UiRect {
            val ui = screen("""{"id":"root","type":"column","layout":{"align":"$align"},"children":[{"id":"c","type":"button","layout":{"width":100}}]}""", 400, 100)
            return engine.resolve(ui, 400f, 100f).rect("c")
        }
        assertEquals(0f, x("start").x, 1e-3f)
        assertEquals(150f, x("center").x, 1e-3f)
        assertEquals(300f, x("end").x, 1e-3f)
        assertEquals(0f, x("stretch").x, 1e-3f)
        val stretched = engine.resolve(screen("""{"id":"root","type":"column","layout":{"align":"stretch"},"children":[{"id":"c","type":"button"}]}""", 400, 100), 400f, 100f)
        assertEquals(400f, stretched.rect("c").width, 1e-3f)
    }

    @Test fun nestedContainersUseMeasuredContainerSizes() {
        val ui = screen("""{"id":"root","type":"panel","children":[
            {"id":"col","type":"column","layout":{"anchor":"bottomRight","pivot":"bottomRight","padding":4,"spacing":2},"children":[
              {"id":"row","type":"row","layout":{"spacing":6},"children":[{"id":"x","type":"label","properties":{"text":"ab"}},{"id":"y","type":"label","properties":{"text":"abc"}}]},
              {"id":"z","type":"label","properties":{"text":"abcdefghij"}}]}]}""")
        val result = engine.resolve(ui, 1280f, 720f)
        // row: 20 + 6 + 30 = 56 x 20; z: 100 x 20; column content 100 x 42 plus padding 8
        val col = result.rect("col")
        assertEquals(108f, col.width, 1e-3f); assertEquals(50f, col.height, 1e-3f)
        assertEquals(1280f, col.right, 1e-3f); assertEquals(720f, col.bottom, 1e-3f)
        assertEquals(56f, result.rect("row").width, 1e-3f)
        assertEquals(col.x + 4, result.rect("row").x, 1e-3f)
        assertEquals(result.rect("row").bottom + 2, result.rect("z").y, 1e-3f)
        assertEquals(result.rect("row").x + 20 + 6, result.rect("y").x, 1e-3f)
    }

    @Test fun overflowClipsAndReportsWithoutChangingAuthoredSizes() {
        val ui = screen("""{"id":"root","type":"row","layout":{"width":150},"children":[
            {"id":"a","type":"panel","layout":{"width":100,"height":50}},{"id":"b","type":"panel","layout":{"width":100,"height":50}}]}""")
        val result = engine.resolve(ui, 1280f, 720f)
        assertEquals(listOf(UiLayoutDiagnostic(UiLayoutIssue.OVERFLOW, "root")), result.diagnostics)
        val b = result.nodes.getValue("b")
        assertEquals(100f, b.bounds.width, 1e-3f)
        assertEquals(50f, b.visible.width, 1e-3f)
        assertEquals("a", result.hit(10f, 10f)); assertEquals("b", result.hit(140f, 10f)); assertNull(result.hit(160f, 10f))
    }

    @Test fun textMeasurementIsAnInputAndInvalidMeasuresAreReported() {
        val ui = screen("""{"id":"root","type":"panel","children":[{"id":"l","type":"label","properties":{"text":"hello"}}]}""")
        assertEquals(50f, engine.resolve(ui, 1280f, 720f).rect("l").width, 1e-3f)
        val bad = UiLayoutEngine { UiSizeF(Float.NaN, -3f) }.resolve(ui, 1280f, 720f)
        assertEquals(0f, bad.rect("l").width, 0f)
        assertTrue(bad.diagnostics.all { it.code == UiLayoutIssue.INVALID_MEASURE && it.widgetId == "l" })
    }

    @Test fun invalidViewportYieldsNoLayout() {
        val ui = screen("""{"id":"root","type":"panel"}""")
        for ((w, h) in listOf(0f to 100f, 100f to -1f, Float.NaN to 100f, Float.POSITIVE_INFINITY to 100f)) {
            val result = engine.resolve(ui, w, h)
            assertNull(result.fit); assertTrue(result.nodes.isEmpty())
            assertEquals(UiLayoutIssue.INVALID_VIEWPORT, result.diagnostics.single().code)
        }
        assertNull(fitReference(0f, 720f, 100f, 100f))
    }

    @Test fun panelChildrenOutsideTheParentAreClippedForHitTesting() {
        val ui = screen("""{"id":"root","type":"panel","children":[
            {"id":"box","type":"panel","layout":{"x":100,"y":100,"width":50,"height":50},"children":[
              {"id":"out","type":"button","layout":{"x":40,"y":0,"width":50,"height":10}}]}]}""")
        val result = engine.resolve(ui, 1280f, 720f)
        assertEquals(10f, result.nodes.getValue("out").visible.width, 1e-3f)
        assertEquals("root", result.hit(155f, 105f))
        assertEquals("out", result.hit(145f, 105f))
    }
}
