package net.nevinsky.abyssus.lib.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import net.nevinsky.abyssus.lib.core.assets.ui.UiBundledFont
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.render.UiPresenter
import net.nevinsky.abyssus.lib.core.ui.render.UiRenderResources
import net.nevinsky.abyssus.lib.core.ui.state.UiBindingSession
import net.nevinsky.abyssus.lib.core.ui.state.UiScreenRef
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The `uppercase` style key changes what is measured and drawn, not the widget's text. Opt-in: `-Dabyssus.glTests=true`. */
class UiUppercaseStyleTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    private val collection = (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
        "styles":[{"name":"caps","uppercase":true}],
        "screens":[{"id":"s","root":{"id":"root","type":"column","children":[
          {"id":"plain","type":"label","properties":{"text":"www"}},
          {"id":"caps","type":"label","style":"caps","properties":{"text":"www"}}]}}]}}}""")) as UiAdmission.Admitted).collection

    private class Resources(override val defaultFont: BitmapFont) : UiRenderResources {
        override fun texture(id: String): Texture? = null
        override fun font(id: String): BitmapFont? = null
    }

    @Test fun anUppercaseLabelIsWiderAndItsTextIsUnchanged() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val bundled = UiBundledFont()
        val p = UiPresenter("a", collection, { null }, UiBindingSession(collection) { UiWriteResult.Rejected("no") }, { true }, Resources(bundled.acquire()), json)
        try {
            p.resize(1280, 720)
            p.publish(net.nevinsky.abyssus.lib.core.ui.state.UiSnapshot())
            val ref = UiScreenRef("a", "s")
            val plain = p.actorBounds(ref, "plain")!!.width
            val caps = p.actorBounds(ref, "caps")!!.width
            assertTrue("WWW ($caps) is wider than www ($plain)", caps > plain)
            assertEquals("www", collection.screens.single().root.children[1].properties["text"]!!.asText())
        } finally { p.dispose(); bundled.release() }
    }
}
