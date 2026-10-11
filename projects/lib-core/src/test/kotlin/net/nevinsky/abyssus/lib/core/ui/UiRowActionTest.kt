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

/** Clickable rows and select-on-focus in a real GL context. Opt-in: `-Dabyssus.glTests=true`. */
class UiRowActionTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val screen = """{"id":"main","navigation":"arrows","referenceSize":{"width":200,"height":100},"root":{"id":"root","type":"column","children":[
        {"id":"list","type":"list","layout":{"width":100},"bindings":{"items":"rows"},"template":{"id":"row","type":"row","layout":{"width":100,"height":20},
          "style":"plain","actions":{"click":{"type":"emitAction","name":"openRow"}},"children":[{"id":"cell","type":"label","bindings":{"text":"item.name"}}]}},
        {"id":"name","type":"textField","layout":{"width":100,"height":20},"properties":{"selectOnFocus":true},"bindings":{"text":"who"}},
        {"id":"ok","type":"button","layout":{"width":100,"height":20},"actions":{"click":{"type":"emitAction","name":"ok"}}}]}}"""

    private val c: UiCollection = (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
        "bindings":[{"name":"rows","type":"list","fields":[{"name":"name","type":"string"}],"sample":[{"name":"A"}]},{"name":"who","type":"string","access":"write","sample":"Ann"}],
        "styles":[{"name":"plain","background":"#204060ff","states":{"hover":{"background":"#406080ff"},"pressed":{"background":"#80a0c0ff"}}}],
        "screens":[$screen]}}}""")) as UiAdmission.Admitted).collection

    private class Resources(override val defaultFont: BitmapFont) : UiRenderResources {
        override fun texture(id: String): Texture? = null
        override fun font(id: String): BitmapFont? = null
    }

    private fun run(block: (UiPresenter, MutableList<String>, MutableList<UiWrite>) -> Unit): Unit = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val bundled = UiBundledFont()
        val actions = mutableListOf<String>(); val writes = mutableListOf<UiWrite>()
        val p = UiPresenter("menus", c, { null }, UiBindingSession(c) { writes += it; UiWriteResult.Accepted(it.value) }, { actions += it; true }, Resources(bundled.acquire()), json)
        try {
            p.resize(200, 100)
            p.publish(UiSnapshot(mapOf("rows" to UiValue.Rows(listOf("A", "B", "C").map { mapOf("name" to UiValue.Text(it)) }))))
            block(p, actions, writes)
        } finally { p.dispose(); bundled.release() }
    }

    private fun snapshot(p: UiPresenter): Pixmap {
        val fbo = FrameBuffer(Pixmap.Format.RGBA8888, 200, 100, false)
        fbo.begin(); Gdx.gl.glClearColor(0f, 0f, 0f, 1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        p.render()
        val px = ScreenUtils.getFrameBufferPixmap(0, 0, 200, 100)
        fbo.end(0, 0, 200, 100); fbo.dispose()
        return px
    }

    private fun Pixmap.at(x: Int, y: Int) = getPixel(x, height - 1 - y)

    @Test fun clickingARowEmitsItsIndexedActionOnceAndTheClickIsConsumed() = run { p, actions, _ ->
        p.render()
        assertTrue("the press is consumed by the row", p.touchDown(50f, 52f)) // the third row: y 40..60
        p.touchUp(50f, 52f)
        assertEquals(listOf("openRow#2"), actions)
        p.touchDown(50f, 12f); p.touchUp(50f, 12f)
        assertEquals(listOf("openRow#2", "openRow#0"), actions)
        // a release elsewhere is not a click
        p.touchDown(50f, 32f); p.touchUp(150f, 12f)
        assertEquals(2, actions.size)
    }

    @Test fun rowsAreNotInTheFocusOrderAndShowHoverAndPressedLooks() = run { p, _, _ ->
        p.render()
        assertEquals("the first focusable control is the name field, not a row", "name", p.navigator.focus)
        p.keyDown(Input.Keys.DOWN); assertEquals("ok", p.navigator.focus)
        p.keyDown(Input.Keys.DOWN); assertEquals("name", p.navigator.focus)
        snapshot(p).let { assertEquals(0x204060ff, it.at(50, 12)); it.dispose() }
        p.pointerMoved(50f, 12f)
        snapshot(p).let { assertEquals("hover", 0x406080ff, it.at(50, 12)); it.dispose() }
        p.touchDown(50f, 12f)
        snapshot(p).let { assertEquals("pressed", 0x80a0c0ff.toInt(), it.at(50, 12)); it.dispose() }
        p.touchUp(150f, 90f)
    }

    @Test fun aSelectOnFocusFieldIsReplacedByTheFirstEditAndEditedAfterThat() = run { p, _, writes ->
        p.render()
        assertEquals("name", p.navigator.focus)
        p.keyTyped('B'); p.keyTyped('o')
        assertEquals("the first character replaced Ann", "Bo", p.fieldTextOf("name"))
        p.keyDown(Input.Keys.ENTER)
        assertEquals(listOf(UiWrite("who", UiValue.Text("Bo"), "name")), writes)
        // focusing it again selects again
        p.keyDown(Input.Keys.DOWN); p.keyDown(Input.Keys.DOWN)
        p.keyDown(Input.Keys.BACKSPACE)
        assertEquals("backspace on a fresh field clears it", "", p.fieldTextOf("name"))
    }

    private fun UiPresenter.fieldTextOf(id: String): String = fieldText(c.screens[0].root.children[1])
}
