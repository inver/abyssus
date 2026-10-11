package net.nevinsky.abyssus.app.game.controlline.uidemo

import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.project.ProjectLoader
import net.nevinsky.abyssus.lib.core.project.UiProjectRegistry
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget
import net.nevinsky.abyssus.lib.core.ui.state.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UiDemoHostTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val dir = File(checkNotNull(System.getProperty("uiDemo.project")) { "run through Gradle" })

    private val admission: UiAdmission.Admitted by lazy {
        val files = FileLoader(dir)
        val project = ProjectLoader(json, files).load("UiDemo.abss")
        assertEquals(listOf("menus"), project.uiAssetNames)
        UiProjectRegistry(json, files).lookup(project, "menus") as UiAdmission.Admitted
    }
    private val collection: UiCollection get() = admission.collection

    private fun widget(id: String): UiWidget {
        fun find(w: UiWidget): UiWidget? = if (w.id == id) w else w.children.firstNotNullOfOrNull(::find)
        return collection.screens.firstNotNullOf { find(it.root) }
    }

    private fun write(binding: String, value: UiValue) = UiWrite(binding, value, "test")

    @Test fun theAuthoredDemoAssetIsAdmittedCleanlyAndIsSeparateFromControlLine() {
        assertTrue(admission.warnings.toString(), admission.warnings.isEmpty())
        assertEquals(listOf("main", "settings", "hud", "pause"), collection.screens.map { it.id })
        assertNotEquals(File(checkNotNull(System.getProperty("controlLine.project"))).canonicalPath, dir.canonicalPath)
    }

    @Test fun writesAreAcceptedNormalizedOrRejected() {
        val host = UiDemoHost()
        assertEquals(UiWriteResult.Accepted(UiValue.Num(0.75)), host.write(write("volume", UiValue.Num(0.76))))
        assertEquals(0.75, host.volume, 0.0)
        assertEquals(UiWriteResult.Accepted(UiValue.Num(1.0)), host.write(write("volume", UiValue.Num(9.0))))
        assertEquals(UiWriteResult.Accepted(UiValue.Bool(true)), host.write(write("muted", UiValue.Bool(true))))
        assertEquals(UiWriteResult.Accepted(UiValue.Text("Ace")), host.write(write("playerName", UiValue.Text("  Ace "))))
        assertEquals(UiWriteResult.Rejected("That name is not allowed"), host.write(write("playerName", UiValue.Text("Admin"))))
        assertEquals(UiWriteResult.Rejected("That name is not allowed"), host.write(write("playerName", UiValue.Text("   "))))
        assertEquals("Ace", host.playerName)
        assertEquals(UiWriteResult.Rejected("health is read-only"), host.write(write("health", UiValue.Num(1.0))))
        assertEquals(UiWriteResult.Rejected("volume must be a number"), host.write(write("volume", UiValue.Text("loud"))))
    }

    @Test fun actionsAreDeliveredOnceAndUnknownOnesAreReportedUnhandled() {
        val host = UiDemoHost()
        assertTrue(host.emit("startGame"))
        assertTrue(host.started)
        assertFalse(host.emit("cheat"))
        assertTrue(host.emit("toMenu"))
        assertFalse(host.started)
        assertEquals(listOf("startGame", "cheat", "toMenu"), host.actions)
    }

    @Test fun liveDataChangesWhileAGameRuns() {
        val host = UiDemoHost()
        host.tick(10.0)
        assertEquals(100.0, host.health, 0.0) // nothing runs before Start
        host.emit("startGame")
        host.tick(4.0)
        assertEquals(80.0, host.health, 1e-9)
        assertEquals(40.0, host.score, 1e-9)
        host.tick(100.0)
        assertEquals(0.0, host.health, 0.0)
        val session = UiBindingSession(collection) { host.write(it) }
        val view = session.publish(host.snapshot())
        assertEquals(0f, view.progress(widget("health-bar")), 0f)
        host.emit("toMenu")
    }

    @Test fun theAuthoredControlsRoundTripThroughTheHost() {
        val host = UiDemoHost()
        val session = UiBindingSession(collection) { host.write(it) }
        session.publish(host.snapshot())
        assertEquals(UiCommitOutcome.Applied("volume", UiValue.Num(0.75)), session.commit(widget("volume"), UiValue.Num(0.76)))
        // the settings screen's slider is the same binding and shows the normalized value
        assertEquals(0.75, session.view().number(widget("settings-volume"), "value", -1.0), 0.0)
        val rejected = session.commit(widget("name"), UiValue.Text("admin")) as UiCommitOutcome.Rejected
        assertEquals("That name is not allowed", rejected.reason)
        assertEquals(UiValue.Text("Pilot"), rejected.restored)
        assertEquals("Pilot", session.view().text(widget("name")))
        assertTrue(session.commit(widget("name"), UiValue.Text("a-name-that-is-too-long")) is UiCommitOutcome.Refused)
        assertTrue(session.plan.diagnostics.toString(), session.plan.diagnostics.isEmpty())
        // a game publishing health 25 of 100 fills the bar a quarter
        val hud = session.publish(UiSnapshot(mapOf("health" to UiValue.Num(25.0), "maxHealth" to UiValue.Num(100.0))))
        assertEquals(0.25f, hud.progress(widget("health-bar")), 0f)
    }
}
