package net.nevinsky.abyssus.lib.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.ScreenUtils
import net.nevinsky.abyssus.lib.core.assets.ui.UiBundledFont
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.render.UiPresenter
import net.nevinsky.abyssus.lib.core.ui.render.UiRenderResources
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Shared renderer behavior in a real GL context. Opt-in: `-Dabyssus.glTests=true` (opens a window). */
class UiRendererTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private fun collection(screens: String, bindings: String = "[]", styles: String = "[]"): UiCollection =
        (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
            "bindings":$bindings,"styles":$styles,"screens":[$screens]}}}""")) as UiAdmission.Admitted).collection

    private class Resources(override val defaultFont: BitmapFont) : UiRenderResources {
        override fun texture(id: String): Texture? = null
        override fun font(id: String): BitmapFont? = null
    }

    private class Host(val presenter: UiPresenter, val writes: MutableList<UiWrite>, val actions: MutableList<String>)

    private fun <T> withHost(
        collection: UiCollection,
        width: Int, height: Int,
        accept: (UiWrite) -> UiWriteResult = { UiWriteResult.Accepted(it.value) },
        block: (Host) -> T,
    ): T = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val bundled = UiBundledFont()
        val writes = mutableListOf<UiWrite>()
        val actions = mutableListOf<String>()
        val session = UiBindingSession(collection) { writes += it; accept(it) }
        val presenter = UiPresenter("menus", collection, { null }, session, { actions += it; true }, Resources(bundled.acquire()), json)
        try {
            presenter.resize(width, height)
            block(Host(presenter, writes, actions))
        } finally {
            presenter.dispose()
            bundled.release()
        }
    }

    /** Draws into an off-screen buffer of [width] x [height] and returns the pixels (y-up rows, as GL gives them). */
    private fun Host.snapshot(width: Int, height: Int): Pixmap {
        val fbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
        fbo.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        presenter.render()
        val pixels = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)
        fbo.end(0, 0, width, height)
        fbo.dispose()
        return pixels
    }

    /** RGBA8888 at logical top-left coordinates ([x], [y]) of an unscaled snapshot. */
    private fun Pixmap.at(x: Int, y: Int): Int = getPixel(x, height - 1 - y)

    private val root = """{"id":"root","type":"panel","children":[
        {"id":"hud","type":"progressBar","layout":{"anchor":"topRight","x":-16,"y":16,"width":200,"height":16}},
        {"id":"menu","type":"column","layout":{"anchor":"center","spacing":10,"align":"center"},"children":[
           {"id":"a","type":"button","layout":{"width":120,"height":30}},{"id":"b","type":"button","layout":{"width":120,"height":30}}]}]}"""

    @Test fun actorsFollowTheSharedLayoutAndTheViewportFitsUniformly() {
        val ui = collection("""{"id":"main","root":$root}""")
        for ((w, h) in listOf(1280 to 720, 1024 to 768)) withHost(ui, w, h) { host ->
            val ref = UiScreenRef("menus", "main")
            val layout = host.presenter.layout(ref)!!
            for ((id, node) in layout.nodes) {
                val actor = host.presenter.actorBounds(ref, id)!!
                assertEquals(id, node.bounds.x, actor.x, 1e-3f)
                assertEquals(id, 720f - node.bounds.bottom, actor.y, 1e-3f)
                assertEquals(id, node.bounds.width, actor.width, 1e-3f)
                assertEquals(id, node.bounds.height, actor.height, 1e-3f)
            }
            val fit = layout.fit!!
            val vp = host.presenter.viewportBounds(ref)!!
            assertEquals(fit.offsetX, vp.x, 1.01f)
            assertEquals(fit.referenceWidth * fit.scale, vp.width, 1.01f)
            assertEquals(fit.referenceHeight * fit.scale, vp.height, 1.01f)
            assertEquals((h - vp.height) / 2f, vp.y, 1.01f) // letterboxed vertically on 4:3
        }
    }

    @Test fun childrenAreClippedToTheirParentOnScreen() {
        val ui = collection("""{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
            {"id":"box","type":"panel","layout":{"x":20,"y":20,"width":60,"height":60},"properties":{"styleOverrides":{"background":"#0000ffff"}},"children":[
               {"id":"big","type":"panel","layout":{"x":40,"y":0,"width":100,"height":20},"properties":{"styleOverrides":{"background":"#ff0000ff"}}}]}]}}""")
        withHost(ui, 200, 100) { host ->
            val px = host.snapshot(200, 100)
            assertEquals(0x0000ffff, px.at(30, 70))      // the box itself
            assertEquals(0xff0000ff.toInt(), px.at(70, 30)) // the child inside the box
            assertEquals(0x000000ff, px.at(100, 30))     // the same child outside the box: clipped
            assertEquals(0x000000ff, px.at(150, 30))
            assertEquals(0x000000ff, px.at(5, 5))        // transparent root draws nothing
            px.dispose()
        }
    }

    private val buttons = """{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
        {"id":"one","type":"button","style":"flat","layout":{"x":10,"y":10,"width":80,"height":40},"bindings":{"enabled":"on"}},
        {"id":"two","type":"button","style":"flat","layout":{"x":110,"y":10,"width":80,"height":40}}]}}"""
    private val flat = """[{"name":"flat","background":"#102030ff","states":{"hover":{"background":"#405060ff"},"pressed":{"background":"#708090ff"},"disabled":{"background":"#222222ff"}}}]"""
    private val enabledBinding = """[{"name":"on","type":"boolean","sample":true}]"""

    @Test fun stateStylesApplyInPriorityOrderOnScreen() {
        withHost(collection(buttons, enabledBinding, flat), 200, 100) { host ->
            fun corner() = host.snapshot(200, 100).also { }.let { p -> p.at(12, 12).also { p.dispose() } }
            assertEquals(0x102030ff, corner())
            host.presenter.pointerMoved(50f, 30f)
            assertEquals(0x405060ff, corner())
            assertTrue(host.presenter.touchDown(50f, 30f))
            assertEquals(0x708090ff, corner())
            host.presenter.touchUp(300f, 300f) // released elsewhere: no click
            host.presenter.pointerMoved(150f, 30f)
            assertEquals(0x102030ff, corner())
            host.presenter.publish(UiSnapshot(mapOf("on" to UiValue.Bool(false))))
            assertEquals(0x222222ff, corner())
        }
    }

    @Test fun focusKeepsAVisibleIndicatorAndTabMovesIt() {
        withHost(collection(buttons, enabledBinding, flat), 200, 100) { host ->
            assertTrue(host.presenter.keyDown(Input.Keys.TAB))
            assertEquals("one", host.presenter.navigator.focus)
            val p1 = host.snapshot(200, 100)
            assertEquals(0xffcc33ff.toInt(), p1.at(11, 30)) // bundled focus border, 2px, on the left edge of "one"
            assertEquals("interior keeps the style background", 0x102030ff, p1.at(20, 20))
            assertEquals("the other button has no indicator", 0x102030ff, p1.at(111, 30))
            p1.dispose()
            host.presenter.keyDown(Input.Keys.TAB)
            assertEquals("two", host.presenter.navigator.focus)
            host.presenter.keyDown(Input.Keys.TAB, shift = true)
            assertEquals("one", host.presenter.navigator.focus)
        }
    }

    @Test fun buttonsActivateByPointerAndKeyboardWithOneActionEach() {
        val ui = collection("""{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
            {"id":"go","type":"button","layout":{"x":10,"y":10,"width":80,"height":40},"actions":{"click":{"type":"emitAction","name":"startGame"}}}]}}""")
        withHost(ui, 200, 100) { host ->
            host.presenter.touchDown(20f, 20f); host.presenter.touchUp(20f, 20f)
            assertEquals(listOf("startGame"), host.actions)
            host.presenter.keyDown(Input.Keys.TAB); host.presenter.keyDown(Input.Keys.ENTER)
            host.presenter.keyDown(Input.Keys.SPACE)
            assertEquals(listOf("startGame", "startGame", "startGame"), host.actions)
            assertTrue("a screen swallows a press on its empty area", host.presenter.touchDown(190f, 90f))
        }
    }

    private val controls = """{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
        {"id":"vol","type":"slider","layout":{"x":0,"y":0,"width":100,"height":20},"bindings":{"value":"volume"}},
        {"id":"mute","type":"checkbox","layout":{"x":0,"y":30,"width":100,"height":20},"bindings":{"checked":"muted"}},
        {"id":"name","type":"textField","layout":{"x":0,"y":60,"width":100,"height":20},"bindings":{"text":"name"}}]}}"""
    private val controlBindings = """[{"name":"volume","type":"number","access":"write","sample":0,"min":0,"max":1},
        {"name":"muted","type":"boolean","access":"write","sample":false},{"name":"name","type":"string","access":"write","sample":"Ace","maxLength":6}]"""

    @Test fun writableControlsCommitOncePerGesture() {
        withHost(collection(controls, controlBindings), 200, 100) { host ->
            val p = host.presenter
            p.touchDown(10f, 10f); p.touchDragged(30f, 10f); p.touchDragged(50f, 10f)
            assertTrue("dragging alone does not write", host.writes.isEmpty())
            p.touchUp(50f, 10f)
            assertEquals(1, host.writes.size)
            assertEquals(0.5, (host.writes[0].value as UiValue.Num).value, 1e-6)
            p.touchDown(10f, 40f); p.touchUp(10f, 40f)
            assertEquals(UiWrite("muted", UiValue.Bool(true), "mute"), host.writes[1])
            p.touchDown(10f, 70f); p.touchUp(10f, 70f)
            "Bo".forEach { p.keyTyped(it) }
            p.keyDown(Input.Keys.BACKSPACE); "b".forEach { p.keyTyped(it) }
            assertEquals(2, host.writes.size)
            p.keyDown(Input.Keys.ENTER)
            assertEquals(UiWrite("name", UiValue.Text("AceBb"), "name"), host.writes[2])
        }
    }

    @Test fun rejectedWritesRestoreTheValueAndShowFeedback() {
        val ui = collection(controls, controlBindings)
        val field = ui.screens[0].root.children[2]
        withHost(ui, 200, 100, accept = { UiWriteResult.Rejected("nope") }) { host ->
            val p = host.presenter
            p.publish(UiSnapshot(mapOf("name" to UiValue.Text("Ace"))))
            p.touchDown(10f, 70f); p.touchUp(10f, 70f)
            "x".forEach { p.keyTyped(it) }
            p.keyDown(Input.Keys.ENTER)
            assertEquals("nope", p.rejectionReason("name"))
            assertEquals("Ace", p.view.text(field))
        }
    }

    @Test fun modalOverlayBlocksTheScreenAndEscapeClosesIt() {
        val ui = collection("""{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
              {"id":"open","type":"button","layout":{"x":10,"y":10,"width":80,"height":40},"actions":{"click":{"type":"showOverlay","screenId":"pause"}}},
              {"id":"under","type":"button","layout":{"x":110,"y":10,"width":80,"height":40},"actions":{"click":{"type":"emitAction","name":"under"}}}]}},
            {"id":"pause","kind":"overlay","modal":true,"referenceSize":{"width":200,"height":100},"root":{"id":"p","type":"panel","children":[
              {"id":"resume","type":"button","layout":{"x":10,"y":60,"width":80,"height":30},"actions":{"click":{"type":"closeOverlay"}}}]}}""")
        withHost(ui, 200, 100) { host ->
            val p = host.presenter
            p.touchDown(20f, 20f); p.touchUp(20f, 20f)
            assertEquals(1, p.navigator.openOverlays().size)
            assertTrue(p.touchDown(150f, 20f)); p.touchUp(150f, 20f)
            assertTrue("the covered button never fires", host.actions.isEmpty())
            assertTrue(p.keyDown(Input.Keys.ESCAPE))
            assertTrue(p.navigator.openOverlays().isEmpty())
            p.touchDown(150f, 20f); p.touchUp(150f, 20f)
            assertEquals(listOf("under"), host.actions)
        }
    }

    @Test fun nonmodalHudLetsOtherInputThroughToTheGame() {
        val ui = collection("""{"id":"hud","kind":"overlay","referenceSize":{"width":200,"height":100},"root":{"id":"h","type":"panel","children":[
              {"id":"btn","type":"button","layout":{"x":150,"y":10,"width":40,"height":20},"actions":{"click":{"type":"emitAction","name":"hudButton"}}}]}}""")
        TestGl.run {
            Gdx.files = Lwjgl3Files()
            val bundled = UiBundledFont()
            val actions = mutableListOf<String>()
            val presenter = UiPresenter("hud", ui, { null }, UiBindingSession(ui) { UiWriteResult.Accepted(it.value) }, { actions += it; true },
                Resources(bundled.acquire()), json, showEntry = false)
            presenter.navigator.activate(ui.screens[0].let { UiAction("showOverlay", UiActionType.SHOW_OVERLAY, screenId = "hud", raw = json.readObject("{}")) })
            presenter.resize(200, 100)
            assertFalse(presenter.touchDown(20f, 80f))
            assertTrue(presenter.touchDown(160f, 20f)); presenter.touchUp(160f, 20f)
            assertEquals(listOf("hudButton"), actions)
            presenter.dispose(); bundled.release()
        }
    }

    @Test fun hiddenWidgetsAndTheirSubtreesAreNotDrawn() {
        val ui = collection("""{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
            {"id":"group","type":"panel","layout":{"x":10,"y":10,"width":50,"height":50},"bindings":{"visible":"show"},"properties":{"styleOverrides":{"background":"#ff0000ff"}},"children":[
               {"id":"kid","type":"panel","layout":{"x":0,"y":0,"width":20,"height":20},"properties":{"styleOverrides":{"background":"#00ff00ff"}}}]}]}}""",
            """[{"name":"show","type":"boolean","sample":true}]""")
        withHost(ui, 200, 100) { host ->
            host.snapshot(200, 100).let { assertEquals(0x00ff00ff, it.at(15, 15)); it.dispose() }
            host.presenter.publish(UiSnapshot(mapOf("show" to UiValue.Bool(false))))
            host.snapshot(200, 100).let { assertEquals(0x000000ff, it.at(15, 15)); assertEquals(0x000000ff, it.at(50, 50)); it.dispose() }
        }
    }

    @Test fun everyWidgetKindDrawsItsBundledAppearance() {
        val ui = collection("""{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
            {"id":"bar","type":"progressBar","layout":{"x":0,"y":0,"width":100,"height":20},"bindings":{"value":"health","maximum":"max"}},
            {"id":"vol","type":"slider","layout":{"x":0,"y":30,"width":100,"height":20},"bindings":{"value":"volume"}},
            {"id":"on","type":"checkbox","layout":{"x":110,"y":0,"width":80,"height":20},"bindings":{"checked":"muted"}},
            {"id":"off","type":"checkbox","layout":{"x":110,"y":25,"width":80,"height":20}},
            {"id":"field","type":"textField","layout":{"x":110,"y":60,"width":80,"height":20}},
            {"id":"pic","type":"image","layout":{"x":0,"y":60,"width":30,"height":30}},
            {"id":"odd","type":"hologram","layout":{"x":40,"y":60,"width":30,"height":30}}]}}""",
            """[{"name":"health","type":"number","sample":25},{"name":"max","type":"number","sample":100},
                {"name":"volume","type":"number","access":"write","sample":0.5,"min":0,"max":1},{"name":"muted","type":"boolean","access":"write","sample":true}]""")
        withHost(ui, 200, 100) { host ->
            val px = host.snapshot(200, 100)
            assertEquals("progress fill", 0x4caf50ff, px.at(10, 10))
            assertEquals("progress background", 0x222831ff, px.at(60, 10))
            assertEquals("slider fill", 0x4a90d9ff.toInt(), px.at(20, 40))
            assertEquals("slider track", 0x222831ff, px.at(80, 40))
            assertEquals("slider knob", 0xffffffff.toInt(), px.at(50, 40))
            assertEquals("checked box", 0x4a90d9ff.toInt(), px.at(120, 10))
            assertEquals("box frame", 0x222831ff, px.at(111, 10))
            assertEquals("unchecked box has no fill", 0x222831ff, px.at(120, 35))
            assertEquals("text field", 0x1b2027ff, px.at(185, 70))
            assertEquals("missing image placeholder", 0xff00ffff.toInt(), px.at(0, 75))
            assertEquals("unknown widget placeholder", 0xff00ffff.toInt(), px.at(40, 75))
            px.dispose()
            assertTrue(host.presenter.diagnostics().none { it.code == UiBindingIssue.INVALID_MAXIMUM })
        }
    }

    @Test fun theEditOverlayDrawsSelectionHoverOutlinesAndTheDropGhostOverTheScreen() {
        val ui = collection("""{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
            {"id":"box","type":"panel","layout":{"x":20,"y":20,"width":60,"height":40}},
            {"id":"other","type":"panel","layout":{"x":120,"y":20,"width":60,"height":40}}]}}""")
        withHost(ui, 200, 100) { host ->
            assertTrue(host.presenter.display("main", "menus"))
            host.presenter.overlay = net.nevinsky.abyssus.lib.core.ui.render.UiEditOverlay(selectedId = "box", outlines = false,
                ghost = net.nevinsky.abyssus.lib.core.ui.layout.UiRect(120f, 70f, 40f, 20f))
            val px = host.snapshot(200, 100)
            assertEquals("selection frame, 2px, on the left edge", 0x2d8cffff, px.at(20, 40))
            assertEquals(0x2d8cffff, px.at(21, 40))
            assertEquals("not inside", 0x000000ff, px.at(40, 40))
            assertEquals("the ghost", 0xffcc33ff.toInt(), px.at(120, 80))
            assertEquals("outlines are off", 0x000000ff, px.at(120, 30))
            px.dispose()
            host.presenter.overlay = net.nevinsky.abyssus.lib.core.ui.render.UiEditOverlay(hoverId = "other", outlines = true)
            host.snapshot(200, 100).let { p ->
                fun r(v: Int) = (v ushr 24) and 255
                fun b(v: Int) = (v ushr 8) and 255
                val hover = p.at(120, 40)
                assertTrue("a translucent blue frame on the hovered widget", b(hover) > r(hover) && b(hover) > 0)
                val outline = p.at(20, 40)
                assertTrue("outlines mark the other widgets faintly", r(outline) > 0 && r(outline) < 128 && r(outline) == b(outline))
                assertEquals("inside stays clear", 0x000000ff, p.at(40, 40))
                p.dispose()
            }
            host.presenter.overlay = null
            host.snapshot(200, 100).let { p -> assertEquals("no overlay, nothing drawn", 0x000000ff, p.at(20, 40)); p.dispose() }
        }
    }
}
