package net.nevinsky.abyssus.lib.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
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

/** Lists, bound styles, arrow-key screens and host-driven display in a real GL context. Opt-in: `-Dabyssus.glTests=true`. */
class UiListRendererTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val scores = """{"name":"scores","type":"list","fields":[{"name":"name","type":"string"},{"name":"look","type":"string"}],
        "sample":[{"name":"Sample","look":"plain"}]}"""

    private val styles = """[{"name":"gold","background":"#ffd700ff"},{"name":"plain","background":"#204060ff"},{"name":"empty","background":"#00aa00ff"}]"""

    private fun collection(screens: String, bindings: String = "[$scores]"): UiCollection =
        (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
            "bindings":$bindings,"styles":$styles,"screens":[$screens]}}}""")) as UiAdmission.Admitted).collection

    private class Resources(override val defaultFont: BitmapFont) : UiRenderResources {
        override fun texture(id: String): Texture? = null
        override fun font(id: String): BitmapFont? = null
    }

    private class Host(val presenter: UiPresenter, val actions: MutableList<String>)

    private fun <T> withHost(c: UiCollection, block: (Host) -> T): T = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val bundled = UiBundledFont()
        val actions = mutableListOf<String>()
        val presenter = UiPresenter("menus", c, { null }, UiBindingSession(c) { UiWriteResult.Accepted(it.value) }, { actions += it; true },
            Resources(bundled.acquire()), json)
        try { presenter.resize(200, 100); block(Host(presenter, actions)) } finally { presenter.dispose(); bundled.release() }
    }

    private fun Host.snapshot(): Pixmap {
        val fbo = FrameBuffer(Pixmap.Format.RGBA8888, 200, 100, false)
        fbo.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        presenter.render()
        val pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 200, 100)
        fbo.end(0, 0, 200, 100)
        fbo.dispose()
        return pixels
    }

    private fun Pixmap.at(x: Int, y: Int): Int = getPixel(x, height - 1 - y)

    private fun rows(vararg items: Pair<String, String>) = UiValue.Rows(items.map { (n, l) -> mapOf("name" to UiValue.Text(n), "look" to UiValue.Text(l)) })

    private val tableScreen = """{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
        {"id":"table","type":"list","layout":{"x":10,"y":10,"width":100,"spacing":2},"properties":{"maxRows":2},"bindings":{"items":"scores"},
         "template":{"id":"row","type":"panel","layout":{"width":100,"height":20},"bindings":{"style":"item.look"}},
         "empty":{"id":"none","type":"panel","layout":{"width":100,"height":20},"style":"empty"}}]}}"""

    @Test fun rowsFollowTheDataAndGetTheirOwnBoundStyle() {
        withHost(collection(tableScreen)) { h ->
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows("Ace" to "gold", "Bo" to "plain", "Cy" to "gold"))))
            val px = h.snapshot()
            assertEquals("first row, bound to gold", 0xffd700ff.toInt(), px.at(50, 20))
            assertEquals("second row, bound to plain, 2 units below", 0x204060ff, px.at(50, 42))
            assertEquals("maxRows 2: a third row is not drawn", 0x000000ff, px.at(50, 64))
            px.dispose()
            // the host changes one row's look: only that row changes
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows("Ace" to "plain", "Bo" to "plain"))))
            h.snapshot().let { assertEquals(0x204060ff, it.at(50, 20)); assertEquals(0x204060ff, it.at(50, 42)); it.dispose() }
            val ref = UiScreenRef("menus", "main")
            assertEquals(listOf("row#0", "row#1"), h.presenter.layout(ref)!!.order.filter { it.startsWith("row#") })
        }
    }

    @Test fun anEmptyListShowsTheEmptyStateAndAnUnknownStyleFallsBackWithoutTrouble() {
        withHost(collection(tableScreen)) { h ->
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows())))
            h.snapshot().let { assertEquals("the empty state", 0x00aa00ff, it.at(50, 20)); assertEquals(0x000000ff, it.at(50, 42)); it.dispose() }
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows("Ace" to "nonsuch"))))
            h.snapshot().let { assertEquals("an unknown style leaves the bundled look (panel: clear)", 0x000000ff, it.at(50, 20)); it.dispose() }
        }
    }

    @Test fun anUnchangedSnapshotDoesNotLayOutAgainButAChangeDoes() {
        withHost(collection(tableScreen)) { h ->
            val ref = UiScreenRef("menus", "main")
            val snap = UiSnapshot(mapOf("scores" to rows("Ace" to "gold")))
            h.presenter.publish(snap)
            val first = h.presenter.layout(ref)
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows("Ace" to "gold")))) // an equal snapshot
            assertSame(first, h.presenter.layout(ref))
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows("Ace" to "gold", "Bo" to "plain"))))
            assertNotSame(first, h.presenter.layout(ref))
        }
    }

    private val menu = """{"id":"main","navigation":"arrows","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"column","children":[
        {"id":"a","type":"button","layout":{"width":80,"height":20},"bindings":{"enabled":"on"},"actions":{"click":{"type":"emitAction","name":"a"}}},
        {"id":"b","type":"button","layout":{"width":80,"height":20},"actions":{"click":{"type":"emitAction","name":"b"}}},
        {"id":"c","type":"button","layout":{"width":80,"height":20},"actions":{"click":{"type":"emitAction","name":"c"}}}]}}"""

    @Test fun anArrowScreenFocusesTheFirstEnabledControlAndUpDownWrapAndEnterPressesOnce() {
        withHost(collection(menu, "[{\"name\":\"on\",\"type\":\"boolean\",\"sample\":false}]")) { h ->
            val p = h.presenter
            p.render()
            assertEquals("the first control is disabled, so the second has focus", "b", p.navigator.focus)
            assertTrue(p.keyDown(Input.Keys.DOWN)); assertEquals("c", p.navigator.focus)
            assertTrue(p.keyDown(Input.Keys.DOWN)); assertEquals("a disabled control is skipped when wrapping", "b", p.navigator.focus)
            assertTrue(p.keyDown(Input.Keys.UP)); assertEquals("c", p.navigator.focus)
            assertTrue(p.keyDown(Input.Keys.ENTER))
            assertEquals(listOf("c"), h.actions)
            // Tab still works
            assertTrue(p.keyDown(Input.Keys.TAB)); assertEquals("b", p.navigator.focus)
            p.publish(UiSnapshot(mapOf("on" to UiValue.Bool(true))))
            p.render(); assertTrue(p.keyDown(Input.Keys.UP)); assertEquals("a", p.navigator.focus)
        }
    }

    @Test fun withoutArrowsUpAndDownDoNothing() {
        withHost(collection(menu.replace("\"navigation\":\"arrows\",", ""), "[{\"name\":\"on\",\"type\":\"boolean\",\"sample\":true}]")) { h ->
            h.presenter.render()
            assertNull(h.presenter.navigator.focus)
            assertFalse(h.presenter.keyDown(Input.Keys.DOWN))
            assertTrue(h.presenter.keyDown(Input.Keys.TAB)); assertEquals("a", h.presenter.navigator.focus)
        }
    }

    @Test fun theHostShowsScreensByNameAndAnUnknownOneIsReported() {
        val c = collection("""{"id":"main","root":{"id":"m","type":"panel"}},{"id":"pause","kind":"overlay","root":{"id":"p","type":"panel"}}""")
        withHost(c) { h ->
            val p = h.presenter
            assertTrue(p.display("pause", "menus"))
            assertEquals(listOf(UiScreenRef("menus", "pause")), p.navigator.layers().map { it.ref })
            assertFalse(p.display("nowhere", "menus"))
            assertEquals(UiNavDiagnostic(UiNavIssue.MISSING_TARGET, "menus:nowhere"), p.lastNavigationFailure)
            assertEquals("the previous screen stays", listOf(UiScreenRef("menus", "pause")), p.navigator.layers().map { it.ref })
            assertTrue(p.display("main", "menus"))
            assertEquals(listOf(UiScreenRef("menus", "main")), p.navigator.layers().map { it.ref })
        }
    }

    @Test fun controlsInsideRowsAreClickableAndActOnceWithTheirRowsId() {
        val screen = """{"id":"main","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"panel","children":[
            {"id":"table","type":"list","layout":{"x":10,"y":10,"width":100},"bindings":{"items":"scores"},
             "template":{"id":"open","type":"button","layout":{"width":100,"height":20},"actions":{"click":{"type":"emitAction","name":"open"}}}}]}}"""
        withHost(collection(screen)) { h ->
            h.presenter.publish(UiSnapshot(mapOf("scores" to rows("A" to "x", "B" to "x", "C" to "x"))))
            h.presenter.render()
            h.presenter.touchDown(50f, 52f); h.presenter.touchUp(50f, 52f) // the third row (y 50..70)
            assertEquals("the action says which row was clicked", listOf("open#2"), h.actions)
            h.presenter.keyDown(Input.Keys.TAB); h.presenter.keyDown(Input.Keys.TAB)
            assertEquals("open#1", h.presenter.navigator.focus)
        }
    }
}
