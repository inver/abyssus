package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test

class UiNavigationTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    private fun collection(screens: String): UiCollection =
        (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,"screens":[$screens]}}}""")) as UiAdmission.Admitted).collection

    private fun button(id: String, action: String) = """{"id":"$id","type":"button","actions":{"click":$action}}"""

    private val menus = collection("""
        {"id":"main","root":{"id":"m","type":"column","children":[
            ${button("start", """{"type":"emitAction","name":"startGame"}""")},
            ${button("settings", """{"type":"openScreen","screenId":"settings"}""")},
            ${button("far", """{"type":"openScreen","assetName":"other","screenId":"home"}""")},
            ${button("pause", """{"type":"showOverlay","screenId":"pause"}""")},
            ${button("nowhere", """{"type":"openScreen","screenId":"gone"}""")},
            ${button("ghostAsset", """{"type":"openScreen","assetName":"ghost","screenId":"x"}""")},
            ${button("wrongKind", """{"type":"openScreen","screenId":"pause"}""")}]}},
        {"id":"settings","root":{"id":"s","type":"panel","children":[${button("back", """{"type":"back"}""")},{"id":"vol","type":"slider"}]}},
        {"id":"pause","kind":"overlay","modal":true,"root":{"id":"p","type":"panel","children":[
            ${button("resume", """{"type":"closeOverlay"}""")},{"id":"p-check","type":"checkbox"}]}},
        {"id":"hud","kind":"overlay","root":{"id":"h","type":"panel","children":[{"id":"hbtn","type":"button"}]}}""")

    private val other = collection("""{"id":"home","root":{"id":"o","type":"panel","children":[${button("home-back", """{"type":"back"}""")}]}}""")

    private val emitted = mutableListOf<String>()
    private var handled = true

    private fun navigator(showEntry: Boolean = true) = UiNavigator("menus", menus, { if (it == "other") other else null },
        { emitted += it; handled }, showEntry)

    private fun UiNavigator.click(id: String): UiNavOutcome {
        fun find(w: UiWidget): UiWidget? = if (w.id == id) w else w.children.firstNotNullOfOrNull(::find)
        val widget = layers().firstNotNullOf { l -> (if (l.ref.assetName == "menus") menus else other).screen(l.ref.screenId)?.root?.let(::find) }
        return activate(widget.actions.getValue("click"))
    }

    private val always: (UiWidget) -> Boolean = { true }

    @Test fun entryScreenOpensAndBackAtTheRootIsANoOp() {
        val nav = navigator()
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
        assertEquals(UiNavOutcome.NoOp, nav.back())
        assertEquals(UiNavOutcome.NoOp, nav.escape())
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
        assertNull(navigator(showEntry = false).currentScreen())
    }

    @Test fun openingASettingsScreenAndGoingBackRestoresScreenAndFocus() {
        val nav = navigator()
        nav.moveFocus(true, always, always) // start
        nav.moveFocus(true, always, always) // settings
        assertEquals("settings", nav.focus)
        assertEquals(UiNavOutcome.Navigated, nav.click("settings"))
        assertEquals(UiScreenRef("menus", "settings"), nav.currentScreen())
        assertNull(nav.focus)
        assertEquals(UiNavOutcome.Navigated, nav.click("back"))
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
        assertEquals("settings", nav.focus)
        assertFalse(nav.canGoBack())
    }

    @Test fun crossAssetTargetsNavigateAndReturn() {
        val nav = navigator()
        assertEquals(UiNavOutcome.Navigated, nav.click("far"))
        assertEquals(UiScreenRef("other", "home"), nav.currentScreen())
        assertEquals(UiNavOutcome.Navigated, nav.click("home-back"))
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
    }

    @Test fun missingAndWrongKindTargetsAreReportedAndTheUiStaysUsable() {
        val nav = navigator()
        assertEquals(UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.MISSING_TARGET, "menus:gone")), nav.click("nowhere"))
        assertEquals(UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.MISSING_TARGET, "ghost:x")), nav.click("ghostAsset"))
        assertEquals(UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.WRONG_KIND, "menus:pause")), nav.click("wrongKind"))
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
        assertEquals(UiNavOutcome.Navigated, nav.click("settings"))
    }

    @Test fun oneActionPerActivationAndUnhandledActionsAreReported() {
        val nav = navigator()
        assertEquals(UiNavOutcome.Emitted("startGame"), nav.click("start"))
        assertEquals(listOf("startGame"), emitted)
        handled = false
        assertEquals(UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.UNHANDLED_ACTION, "startGame")), nav.click("start"))
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
        handled = true
        val unknown = UiAction("teleport", null, raw = json.readObject("{}"))
        assertEquals(UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.UNKNOWN_ACTION, "teleport")), nav.activate(unknown))
    }

    @Test fun escapeClosesTheTopOverlayThenGoesBack() {
        val nav = navigator()
        nav.click("settings")
        nav.activate(UiAction("showOverlay", UiActionType.SHOW_OVERLAY, screenId = "pause", raw = json.readObject("{}")))
        assertEquals(listOf(UiScreenRef("menus", "pause")), nav.openOverlays())
        assertEquals(UiNavOutcome.Navigated, nav.escape())
        assertTrue(nav.openOverlays().isEmpty())
        assertEquals(UiScreenRef("menus", "settings"), nav.currentScreen())
        assertEquals(UiNavOutcome.Navigated, nav.escape())
        assertEquals(UiScreenRef("menus", "main"), nav.currentScreen())
        assertEquals(UiNavOutcome.NoOp, nav.escape())
    }

    @Test fun closingAnOverlayRestoresTheFocusItCovered() {
        val nav = navigator()
        repeat(4) { nav.moveFocus(true, always, always) } // start, settings, far, pause
        assertEquals("pause", nav.focus)
        nav.click("pause")
        assertNull(nav.focus)
        nav.moveFocus(true, always, always)
        assertEquals("resume", nav.focus)
        assertEquals(UiNavOutcome.Navigated, nav.click("resume"))
        assertEquals("pause", nav.focus)
        assertEquals(UiNavOutcome.NoOp, nav.closeOverlay())
    }

    @Test fun modalOverlayBlocksLowerLayersAndNonmodalPassesThrough() {
        val nav = navigator()
        nav.click("pause")
        val hitsMain: (UiLayer) -> String? = { if (it.ref.screenId == "main") "start" else null }
        assertEquals(UiRoute.Blocked, nav.route(hitsMain))
        assertEquals(UiRoute.Handled(UiLayer(UiScreenRef("menus", "pause"), modal = true, overlay = true), "resume"),
            nav.route { if (it.ref.screenId == "pause") "resume" else null })
        nav.closeOverlay()
        // a HUD overlay with no screen below: only interactive widgets are consumed
        val hud = navigator(showEntry = false)
        hud.activate(UiAction("showOverlay", UiActionType.SHOW_OVERLAY, screenId = "hud", raw = json.readObject("{}")))
        assertEquals(UiRoute.Passthrough, hud.route { null })
        assertTrue(hud.route { "hbtn" } is UiRoute.Handled)
        // a nonmodal overlay over a screen still lets nothing reach the game: the screen blocks
        hud.activate(UiAction("openScreen", UiActionType.OPEN_SCREEN, screenId = "main", raw = json.readObject("{}")))
        assertEquals(0, hud.openOverlays().size)
    }

    @Test fun tabTraversalSkipsHiddenAndDisabledAndWraps() {
        val nav = navigator()
        nav.click("settings")
        val hiddenVol: (UiWidget) -> Boolean = { it.id != "vol" }
        assertEquals("back", nav.moveFocus(true, always, always))
        assertEquals("vol", nav.moveFocus(true, always, always))
        assertEquals("back", nav.moveFocus(true, always, always))
        assertEquals("vol", nav.moveFocus(false, always, always))
        assertEquals("back", nav.moveFocus(true, hiddenVol, always))
        assertEquals("back", nav.moveFocus(true, always) { it.id != "vol" })
        assertEquals(listOf("back"), nav.focusOrder({ true }, { it.id != "vol" }))
        assertNull(nav.moveFocus(true, { false }, always))
        // focus stays within the topmost layer
        nav.activate(UiAction("showOverlay", UiActionType.SHOW_OVERLAY, screenId = "pause", raw = json.readObject("{}")))
        assertEquals(listOf("resume", "p-check"), nav.focusOrder(always, always))
    }

    @Test fun enterAndSpaceActivateOnlyAFocusedButton() {
        val nav = navigator()
        assertEquals(UiNavOutcome.NoOp, nav.activateFocused())
        nav.moveFocus(true, always, always)
        assertEquals(UiNavOutcome.Emitted("startGame"), nav.activateFocused())
        assertEquals(listOf("startGame"), emitted)
        nav.click("settings")
        nav.focusOn("vol")
        assertEquals(UiNavOutcome.NoOp, nav.activateFocused())
    }

    @Test fun historyIsBounded() {
        val loop = collection("""{"id":"a","root":{"id":"r","type":"panel"}},{"id":"b","root":{"id":"r2","type":"panel"}}""")
        val nav = UiNavigator("x", loop, { null }, { true }, maxHistory = 3)
        repeat(6) { i ->
            nav.activate(UiAction("openScreen", UiActionType.OPEN_SCREEN, screenId = if (i % 2 == 0) "b" else "a", raw = json.readObject("{}")))
        }
        var backs = 0
        while (nav.back() == UiNavOutcome.Navigated) backs++
        assertEquals(3, backs)
    }

    @Test fun displayShowsExactlyOneScreenOrOverlayAndForgetsTheRest() {
        val nav = navigator()
        nav.click("settings")
        nav.click("back")
        nav.click("pause")
        assertTrue(nav.display(UiScreenRef("menus", "settings")))
        assertEquals(UiScreenRef("menus", "settings"), nav.currentScreen())
        assertTrue("overlays and history are forgotten", nav.openOverlays().isEmpty() && !nav.canGoBack())
        assertEquals(listOf(UiScreenRef("menus", "settings")), nav.layers().map { it.ref })
        // an overlay is shown alone, with no screen below it
        assertTrue(nav.display(UiScreenRef("menus", "hud")))
        assertNull(nav.currentScreen())
        assertEquals(listOf(UiScreenRef("menus", "hud")), nav.layers().map { it.ref })
        assertFalse(nav.display(UiScreenRef("menus", "nowhere")))
        assertEquals("a refused display changes nothing", listOf(UiScreenRef("menus", "hud")), nav.layers().map { it.ref })
    }
}
