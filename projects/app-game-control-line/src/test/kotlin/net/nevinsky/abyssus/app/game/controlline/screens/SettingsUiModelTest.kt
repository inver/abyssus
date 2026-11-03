/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerContext
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerProfiles
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerService
import net.nevinsky.abyssus.app.game.controlline.controller.FakeControllerSource
import net.nevinsky.abyssus.app.game.controlline.controller.GAMEPAD_AXIS_LEFT_Y
import net.nevinsky.abyssus.app.game.controlline.controller.gamepadDefault
import net.nevinsky.abyssus.app.game.controlline.display.DisplayOptions
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import net.nevinsky.abyssus.lib.core.ui.state.UiWrite
import net.nevinsky.abyssus.lib.core.ui.state.UiWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/** The controller settings screen's values, writes and actions, with a real service over a fake device and no graphics. */
class SettingsUiModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val source = FakeControllerSource()
    private val dir get() = temp.root.toPath()
    private val profiles by lazy { ControllerProfiles(dir.resolve("controls.json")) }
    private val service by lazy { ControllerService(source, profiles, HandleInput()) }
    private val flow by lazy {
        GameFlow(emptyList(), ScoreTable(dir.resolve("s.json")), PatternScoreTable(dir.resolve("p.json")), ScaleScoreTable(dir.resolve("sc.json")))
    }
    private class FakeDisplay(var isOn: Boolean = false, override var damaged: Boolean = false) : DisplayOptions {
        override val fullscreen: Boolean get() = isOn

        override fun setFullscreen(on: Boolean) {
            isOn = on
        }
    }

    private val display = FakeDisplay()
    private val model by lazy { GameUiModel(flow, service, display) {} }
    private val settingsContext = ControllerContext(menu = true, settings = true, humanFlight = false)

    private fun values(): Map<String, UiValue> {
        service.update(1f / 60f, settingsContext)
        return model.snapshot(null, null, 0f).toMap()
    }

    private fun Map<String, UiValue>.text(name: String) = (getValue(name) as UiValue.Text).value
    private fun Map<String, UiValue>.flag(name: String) = (getValue(name) as UiValue.Bool).value
    private fun Map<String, UiValue>.num(name: String) = (getValue(name) as UiValue.Num).value

    private fun write(binding: String, value: UiValue) = model.write(UiWrite(binding, value, "w"))

    @Test
    fun withNoDeviceTheScreenSaysSoAndShowsNothingElse() {
        val v = values()
        assertFalse(v.flag("hasDevice"))
        assertEquals("No controller found", v.text("deviceName"))
        assertEquals("", v.text("deviceCount"))
        assertEquals("", v.text("liveTiltText"))
        assertFalse(v.flag("detecting"))
    }

    @Test
    fun settingsOpensFromTheMenuAndUsesItsOwnLayer() {
        assertTrue(model.emit("settings"))
        assertEquals(Screen.Settings, flow.screen)
        assertEquals(GameLayers(SCREEN_SETTINGS), model.layersOf(flow.screen))
    }

    @Test
    fun theLiveBarFollowsTheDeviceWithItsDeadZoneInvertAndCurveApplied() {
        source.plug("pad", "Logitech F310", gamepad = true)
        source.axis("pad", GAMEPAD_AXIS_LEFT_Y, 0.55f)
        var v = values()
        assertTrue(v.flag("hasDevice"))
        assertEquals("Logitech F310", v.text("deviceName"))
        assertEquals("1 of 1    gamepad", v.text("deviceCount"))
        assertEquals("Left stick Y", v.text("axisName"))
        assertEquals("+0.50", v.text("liveTiltText"))
        assertEquals(1.5, v.num("liveTilt"), 1e-5)

        assertTrue(write("invert", UiValue.Bool(true)) is UiWriteResult.Accepted)
        v = values()
        assertEquals("-0.50", v.text("liveTiltText"))
        assertEquals(0.5, v.num("liveTilt"), 1e-5)

        write("invert", UiValue.Bool(false))
        write("deadZone", UiValue.Num(0.0))
        write("curve", UiValue.Num(100.0))
        v = values()
        assertEquals(0.55 * 0.55 * 0.55, v.text("liveTiltText").toDouble(), 0.005)
        assertEquals("100 % cubic", v.text("curveText"))
        assertEquals("0 %", v.text("deadZoneText"))
    }

    @Test
    fun aWriteIsValidatedAppliedAndSaved() {
        source.plug("pad", gamepad = true)
        values()
        assertEquals(UiWriteResult.Accepted(UiValue.Num(30.0)), write("deadZone", UiValue.Num(30.0)))
        assertEquals(UiWriteResult.Accepted(UiValue.Bool(true)), write("invert", UiValue.Bool(true)))
        assertEquals(UiWriteResult.Accepted(UiValue.Num(40.0)), write("curve", UiValue.Num(40.0)))
        val saved = ControllerProfiles(dir.resolve("controls.json")).profile("pad", true)
        assertEquals(0.3f, saved.deadZone, 1e-6f)
        assertEquals(0.4f, saved.curve, 1e-6f)
        assertTrue(saved.invert)
        assertEquals(30.0, values().num("deadZone"), 1e-4)

        assertTrue(write("deadZone", UiValue.Num(60.0)) is UiWriteResult.Rejected)
        assertTrue(write("deadZone", UiValue.Num(-1.0)) is UiWriteResult.Rejected)
        assertTrue(write("curve", UiValue.Num(101.0)) is UiWriteResult.Rejected)
        assertTrue(write("deadZone", UiValue.Text("x")) is UiWriteResult.Rejected)
        assertTrue(write("invert", UiValue.Num(1.0)) is UiWriteResult.Rejected)
        assertTrue(write("deadZone", UiValue.Num(Double.NaN)) is UiWriteResult.Rejected)
        assertEquals("a rejected write changes nothing", 0.3f, ControllerProfiles(dir.resolve("controls.json")).profile("pad", true).deadZone, 1e-6f)
    }

    @Test
    fun withNoDeviceTheSettingsCannotBeWritten() {
        assertTrue(write("invert", UiValue.Bool(true)) is UiWriteResult.Rejected)
        assertTrue(write("deadZone", UiValue.Num(10.0)) is UiWriteResult.Rejected)
    }

    @Test
    fun resetRestoresTheDefaultProfile() {
        source.plug("pad", gamepad = true)
        values()
        write("invert", UiValue.Bool(true))
        assertTrue(model.emit("resetProfile"))
        val v = values()
        assertFalse(v.flag("invert"))
        assertEquals(10.0, v.num("deadZone"), 1e-4)
        assertEquals(gamepadDefault(), service.device!!.profile)
    }

    @Test
    fun theDetectActionsStartAndCancelADetect() {
        source.plug("stick", "Extreme 3D Pro")
        values()
        assertTrue(model.emit("detectAxis"))
        var v = values()
        assertTrue(v.flag("detecting"))
        assertTrue(v.text("detectStatus"), v.text("detectStatus").startsWith("Move the axis you want to use"))
        assertTrue(model.emit("cancelDetect"))
        assertFalse(values().flag("detecting"))
        assertEquals("", values().text("detectStatus"))

        assertTrue(model.emit("detectPause"))
        v = values()
        assertTrue(v.text("detectStatus"), v.text("detectStatus").startsWith("Press the button you want to use"))
    }

    @Test
    fun escapeAndBackCancelADetectFirstAndLeaveOnTheNextPress() {
        source.plug("stick")
        model.emit("settings")
        values()
        model.emit("detectAxis")
        values()
        assertTrue(model.key(Input.Keys.ESCAPE))
        assertEquals("the first Escape only stops the detect", Screen.Settings, flow.screen)
        assertFalse(values().flag("detecting"))
        model.emit("detectPause")
        values()
        assertTrue(model.emit("back"))
        assertEquals(Screen.Settings, flow.screen)
        assertTrue(model.key(Input.Keys.ESCAPE))
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun theDeviceActionsTurnTheDeviceList() {
        source.plug("a", "First").plug("b", "Second")
        assertEquals("First", values().text("deviceName"))
        assertTrue(model.emit("deviceNext"))
        assertEquals("Second", values().text("deviceName"))
        assertEquals("2 of 2    joystick", values().text("deviceCount"))
        assertTrue(model.emit("devicePrevious"))
        assertEquals("First", values().text("deviceName"))
    }

    @Test
    fun anUnreadableSettingsFileIsReported() {
        Files.writeString(dir.resolve("controls.json"), "not json")
        val v = values()
        assertTrue(v.flag("hasControlsDamaged"))
        assertTrue(v.text("controlsDamaged").contains("controls.json.bad"))
    }

    @Test
    fun theFullscreenCheckboxFollowsTheOption() {
        assertFalse(values().flag("fullscreen"))
        display.isOn = true
        assertTrue(values().flag("fullscreen"))
    }

    @Test
    fun aFullscreenWriteTogglesTheOption() {
        assertEquals(UiWriteResult.Accepted(UiValue.Bool(true)), write("fullscreen", UiValue.Bool(true)))
        assertTrue(display.isOn)
        assertTrue(values().flag("fullscreen"))
        assertEquals(UiWriteResult.Accepted(UiValue.Bool(false)), write("fullscreen", UiValue.Bool(false)))
        assertFalse(display.isOn)
    }

    @Test
    fun aFullscreenWriteThatIsNotOnOrOffIsRejected() {
        assertTrue(write("fullscreen", UiValue.Num(1.0)) is UiWriteResult.Rejected)
        assertTrue(write("fullscreen", UiValue.Text("on")) is UiWriteResult.Rejected)
        assertFalse(display.isOn)
    }

    @Test
    fun fullscreenWorksWithNoControllerConnected() {
        assertFalse(values().flag("hasDevice"))
        assertTrue(write("fullscreen", UiValue.Bool(true)) is UiWriteResult.Accepted)
        assertTrue(display.isOn)
    }

    @Test
    fun anUnreadableDisplayFileIsReportedAndIsSeparateFromTheControllerOne() {
        display.damaged = true
        val v = values()
        assertTrue(v.flag("hasDisplayDamaged"))
        assertTrue(v.text("displayDamaged").contains("display.json.bad"))
        assertFalse(v.flag("hasControlsDamaged"))
        display.damaged = false
        assertEquals("", values().text("displayDamaged"))
    }
}
