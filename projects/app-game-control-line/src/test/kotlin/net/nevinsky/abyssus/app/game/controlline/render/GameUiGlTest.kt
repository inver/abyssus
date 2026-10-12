/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.components.PilotComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.FlightReport
import net.nevinsky.abyssus.app.game.controlline.flow.FlightResult
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.flow.planeChoices
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.named
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.pattern.SyntheticSchedule
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.app.game.controlline.screens.GameUiHostDriver
import net.nevinsky.abyssus.app.game.controlline.screens.createGameUi
import org.slf4j.helpers.NOPLogger
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Every screen of the authored `screens` asset lays out and draws without problems on a GL context, in Free flight and in an F2B pattern at each assist level. */
class GameUiGlTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    @Test fun everyScreenBuildsAndDraws() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val scene = loadField()
        val planes = planeChoices(scene.engine.ids.ids.associateWith { scene.engine.ids[it]!! })
        val flow = GameFlow(planes, ScoreTable(temp.root.toPath().resolve("scores.json")), PatternScoreTable(temp.root.toPath().resolve("f2b-scores.json")))
        val ui = createGameUi(bundledProject(), "ControlLine.abss", flow, NOPLogger.NOP_LOGGER) {}
        val driver = GameUiHostDriver(ui)
        try {
            fun show(session: FlightSession? = null, judge: PatternJudge? = null) {
                driver.update(session, judge, 1f / 60f)
                ui.draw(1f / 60f)
                val presenter = ui.presenter
                assertEquals(emptyList<Any>(), presenter.layoutDiagnostics())
                assertEquals(emptyList<Any>(), presenter.diagnostics())
                assertEquals(emptyList<Any>(), presenter.listDiagnostics)
                assertNull(presenter.lastNavigationFailure)
            }
            fun assertShows(vararg lines: String) = driver.dump().let { dump -> lines.forEach { assertTrue("$it in $dump", it in dump) } }
            show()
            assertShows("label|normal|Control Line", "button|enabled|checked|Free flight")
            flow.f2bPattern()
            show()
            assertEquals(Screen.AssistSelect, flow.screen)
            assertShows("button|enabled|checked|Pro", "label|muted|Pro: no help: tension, fuel and the clock")
            // a Mid flight: the HUD with the judge, a toast and the score sheet
            flow.chooseAssist(AssistLevel.MID)
            show()
            assertShows("label|highlight|F2B pattern - Mid", "button|enabled|Fly  (Enter)")
            flow.choosePlane()
            val flying = flow.screen as Screen.Flying
            val plane = scene.named(flying.plane.name)
            val pilot = scene.engine.entities.single { it.getComponent(PilotComponent::class.java) != null }
            val judge = PatternJudge(flying.plane.lineLength)
            FlightSession(scene.engine, pilot, plane, PhysicsAssets(bundledProject().toFile()), failOnWarnings(), JoltNatives(), judge).use { session ->
                show(session, judge)
                session.advance(0.5f, HandleInput())
                show(session, judge)
                assertShows("label|normal|Time", "label|normal|Next")
                flow.pause()
                show(session, judge)
                // the pause menu is over the HUD
                assertShows("label|normal|Time", "label|normal|Paused", "button|enabled|checked|Resume")
                flow.resume()
                show(session, judge)
                // a whole synthetic flight gives the sheet
                val schedule = SyntheticSchedule()
                schedule.everythingBeforeTheLanding()
                schedule.landing()
                schedule.end(FlightEnd.LANDED)
                flow.flightEnded(FlightReport("Stunter", FlightEnd.LANDED, 300f, FlightResult.Pattern(AssistLevel.MID, schedule.judge.sheet, schedule.judge.recording("Stunter", AssistLevel.MID))))
                show(session, schedule.judge)
                assertShows("label|normal|GAME OVER", "label|normal|15. Landing", "field|", "button|enabled|Save")
                assertTrue(ui.keyDown(Input.Keys.ESCAPE))
                "Ann".forEach { ui.inputProcessor.keyTyped(it) }
                assertTrue(ui.keyDown(Input.Keys.ENTER))
                show()
                assertTrue(flow.screen is Screen.GameOver)
                assertShows("label|normal|Saved in place 1", "button|enabled|checked|Retry", "button|enabled|Debrief")
                assertEquals("Ann", flow.lastName)
                // the debrief of the flight just flown: every key it answers, then back to GAME OVER
                flow.debrief()
                show()
                assertTrue(ui.keyDown(Input.Keys.DOWN))
                show()
                for (key in listOf(Input.Keys.SPACE, Input.Keys.NUM_1, Input.Keys.NUM_3, Input.Keys.R, Input.Keys.V, Input.Keys.UP)) {
                    assertTrue(ui.keyDown(key))
                    show()
                }
                assertTrue(ui.keyDown(Input.Keys.ESCAPE))
                show()
                assertTrue(flow.screen is Screen.GameOver)
                flow.scores()
                show()
                assertShows("button|enabled|checked|Mid", "label|selected|> 1")
                // the saved flight's replay from the score table, and the sheet alone when its recording is gone
                assertTrue(ui.keyDown(Input.Keys.ENTER))
                show()
                assertTrue(flow.screen is Screen.Debrief)
                ui.keyDown(Input.Keys.ESCAPE)
                show()
                temp.root.toPath().resolve("f2b-flights").toFile().listFiles()?.forEach { it.delete() }
                flow.openRow(0)
                show()
                assertNull((flow.screen as Screen.Debrief).model.recording)
                ui.keyDown(Input.Keys.ESCAPE)
                show()
                repeat(3) {
                    flow.nextTab()
                    show()
                }
                assertNotNull(flow.patternTable.board(AssistLevel.MID).firstOrNull())
            }
            // Pro and Free flight
            flow.back()
            flow.mainMenu()
            show()
            flow.f2bPattern()
            flow.chooseAssist(AssistLevel.PRO)
            flow.choosePlane()
            show()
            flow.pause()
            flow.mainMenu()
            flow.freeFlight()
            show()
            flow.choosePlane()
            show()
            assertShows("label|normal|Laps", "label|normal|Combo")
        } finally {
            ui.dispose()
        }
    }
}
