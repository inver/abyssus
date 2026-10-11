package net.nevinsky.abyssus.app.game.controlline.uidemo

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.lib.core.assets.ui.UiBundledFont
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.project.ProjectLoader
import net.nevinsky.abyssus.lib.core.project.UiProjectRegistry
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiAction
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType
import net.nevinsky.abyssus.lib.core.ui.render.UiPresenter
import net.nevinsky.abyssus.lib.core.ui.render.UiRenderResources
import net.nevinsky.abyssus.lib.core.ui.state.UiBindingSession
import net.nevinsky.abyssus.lib.core.ui.state.UiScreenRef
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** The demo's authored menus driven through the shared presenter. Opt-in: `-Dabyssus.glTests=true`. */
class UiDemoGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in (-Dabyssus.glTests=true)", TestGl.enabled)

    @Test fun clickingStartDeliversTheActionAndTheSliderCommitsANormalizedValue() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
        val dir = File(checkNotNull(System.getProperty("uiDemo.project")))
        val files = FileLoader(dir)
        val project = ProjectLoader(json, files).load("UiDemo.abss")
        val registry = UiProjectRegistry(json, files)
        val collection = (registry.lookup(project, "menus") as UiAdmission.Admitted).collection
        val host = UiDemoHost()
        val bundled = UiBundledFont()
        val font = bundled.acquire()
        val resources = object : UiRenderResources {
            override val defaultFont = font
            override fun texture(id: String) = null
            override fun font(id: String) = null
        }
        lateinit var ui: UiPresenter
        ui = UiPresenter("menus", collection, { null }, UiBindingSession(collection, host::write),
            { name -> host.emit(name).also { if (it && name == "startGame") ui.navigator.activate(UiAction("showOverlay", UiActionType.SHOW_OVERLAY, screenId = "hud", raw = json.readObject("{}"))) } },
            resources, json)
        try {
            ui.resize(1280, 720)
            ui.publish(host.snapshot())
            val main = UiScreenRef("menus", "main")
            val layout = ui.layout(main)!!
            fun center(id: String) = layout.nodes.getValue(id).bounds.let { layout.fit!!.toViewportX(it.x + it.width / 2) to layout.fit!!.toViewportY(it.y + it.height / 2) }

            // drag the volume slider to the middle and release: one write, snapped to the host's 0.05 step
            val vol = layout.nodes.getValue("volume").bounds
            val y = layout.fit!!.toViewportY(vol.y + vol.height / 2)
            ui.touchDown(layout.fit!!.toViewportX(vol.x + 1), y)
            ui.touchDragged(layout.fit!!.toViewportX(vol.x + vol.width * 0.76f), y)
            ui.touchUp(layout.fit!!.toViewportX(vol.x + vol.width * 0.76f), y)
            assertEquals(0.75, host.volume, 1e-6)

            // a rejected name is reported by the field
            val (nx, ny) = center("name")
            ui.touchDown(nx, ny); ui.touchUp(nx, ny)
            repeat(10) { ui.keyDown(Input.Keys.BACKSPACE) }
            "admin".forEach { ui.keyTyped(it) }
            ui.keyDown(Input.Keys.ENTER)
            assertEquals("That name is not allowed", ui.rejectionReason("name"))
            assertEquals("Pilot", host.playerName)

            // Start: exactly one startGame and the HUD overlay comes up over the menu
            val (sx, sy) = center("start")
            ui.touchDown(sx, sy); ui.touchUp(sx, sy)
            assertEquals(listOf("startGame"), host.actions)
            assertEquals(listOf(UiScreenRef("menus", "hud")), ui.navigator.openOverlays())
            assertEquals(UiScreenRef("menus", "main"), ui.navigator.currentScreen())
            ui.publish(host.snapshot())
            assertTrue(host.started)
        } finally {
            ui.dispose()
            bundled.release()
        }
    }
}
