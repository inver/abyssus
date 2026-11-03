/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flow.Control
import net.nevinsky.abyssus.app.game.controlline.flow.FlightReport
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Mode
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.pattern.ScoreSheet
import net.nevinsky.abyssus.app.game.controlline.pattern.recordedFlight
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleScoreTable
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.ui.state.UiValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Watch AI action, and who flies as the screens show it, without graphics. */
class GameUiModelTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val planes = loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) }

    private val flow by lazy { GameFlow(planes, ScoreTable(temp.root.toPath().resolve("s.json")), PatternScoreTable(temp.root.toPath().resolve("p.json")), ScaleScoreTable(temp.root.toPath().resolve("sc.json"))) }
    private val model by lazy { GameUiModel(flow) {} }

    private fun flag(name: String): Boolean = (model.snapshot(null, null, 0f).toMap().getValue(name) as UiValue.Bool).value

    private fun report(level: AssistLevel) = FlightReport("Stunter", FlightEnd.LANDED, 370f, FlightResult.Pattern(level, ScoreSheet(), recordedFlight(level)))

    private fun toF2bPlaneSelect() {
        flow.f2bPattern()
        flow.chooseAssist(AssistLevel.MID)
    }

    @Test
    fun watchAiShowsOnlyOnF2bPlaneSelect() {
        assertEquals(false, flag("watchAiVisible"))
        flow.freeFlight()
        assertEquals("not in Free flight", false, flag("watchAiVisible"))
        flow.back()
        toF2bPlaneSelect()
        assertEquals(true, flag("watchAiVisible"))
        flow.choosePlane()
        assertEquals(false, flag("watchAiVisible"))
    }

    @Test
    fun theWatchAiActionAndTheAKeyStartADemonstrationAndEnterStillFlies() {
        toF2bPlaneSelect()
        assertTrue(model.emit("watchAi"))
        assertEquals(Control.AI, (flow.screen as Screen.Flying).control)

        flow.pause(); flow.mainMenu(); toF2bPlaneSelect()
        assertTrue(model.key(Input.Keys.A))
        assertEquals(Control.AI, (flow.screen as Screen.Flying).control)

        flow.pause(); flow.mainMenu(); toF2bPlaneSelect()
        assertTrue(model.key(Input.Keys.ENTER, focus = WATCH_AI_BUTTON))
        assertEquals("Enter on the focused Watch AI button", Control.AI, (flow.screen as Screen.Flying).control)

        flow.pause(); flow.mainMenu(); toF2bPlaneSelect()
        assertTrue(model.key(Input.Keys.ENTER, focus = "ps-fly"))
        assertEquals("Enter anywhere else flies", Control.HUMAN, (flow.screen as Screen.Flying).control)

        flow.pause(); flow.mainMenu(); toF2bPlaneSelect()
        assertTrue(model.key(Input.Keys.ENTER))
        assertEquals(Control.HUMAN, (flow.screen as Screen.Flying).control)
    }

    @Test
    fun inFreeFlightTheAKeyDoesNothing() {
        flow.freeFlight()
        assertEquals(false, model.key(Input.Keys.A))
        assertTrue(flow.screen is Screen.PlaneSelect)
    }

    @Test
    fun aDemonstrationIsNamedOnTheHudThePauseMenuGameOverAndTheDebrief() {
        toF2bPlaneSelect()
        flow.watchAi()
        assertEquals("HUD", listOf(true, false), listOf(flag("isAi"), flag("isHuman")))
        flow.pause()
        assertEquals("pause", true, flag("isAi"))
        flow.resume()
        flow.flightEnded(report(AssistLevel.MID))
        assertEquals("GAME OVER", true, flag("isAi"))
        flow.debrief()
        assertTrue(flow.screen is Screen.Debrief)
        assertEquals("debrief", true, flag("isAi"))
        flow.back()
        flow.scores()
        assertEquals("the boards are the players'", false, flag("isAi"))
        flow.back()
        flow.retry()
        assertEquals("retry", true, flag("isAi"))
    }

    @Test
    fun thePlayersOwnFlightShowsTheirHint() {
        toF2bPlaneSelect()
        flow.choosePlane()
        assertEquals(listOf(false, true), listOf(flag("isAi"), flag("isHuman")))
        flow.pause()
        assertEquals(false, flag("isAi"))
        assertEquals(Mode.Pattern(AssistLevel.MID), (flow.screen as Screen.Paused).flying.mode)
    }
}
