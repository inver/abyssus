/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.scenes.scene2d.ui.Skin
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
import net.nevinsky.abyssus.app.game.controlline.screens.GameUi
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Every screen of the game builds and draws on a GL context, in Free flight and in an F2B pattern at each assist level. */
class GameUiGlTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    @Test fun everyScreenBuildsAndDraws() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        val skin = Skin(Gdx.files.classpath("uiskin/uiskin.json"))
        val scene = loadField()
        val planes = planeChoices(scene.engine.ids.ids.associateWith { scene.engine.ids[it]!! })
        val flow = GameFlow(planes, ScoreTable(temp.root.toPath().resolve("scores.json")), PatternScoreTable(temp.root.toPath().resolve("f2b-scores.json")))
        val ui = GameUi(flow, skin) {}
        try {
            fun show(session: FlightSession? = null, judge: PatternJudge? = null) {
                ui.update(session, judge, 1f / 60f)
                ui.draw()
            }
            show()
            flow.f2bPattern()
            show()
            assertEquals(Screen.AssistSelect, flow.screen)
            // a Mid flight: the HUD with the judge, a toast and the score sheet
            flow.chooseAssist(AssistLevel.MID)
            show()
            flow.choosePlane()
            val flying = flow.screen as Screen.Flying
            val plane = scene.named(flying.plane.name)
            val pilot = scene.engine.entities.single { it.getComponent(PilotComponent::class.java) != null }
            val judge = PatternJudge(flying.plane.lineLength)
            FlightSession(scene.engine, pilot, plane, PhysicsAssets(bundledProject().toFile()), failOnWarnings(), JoltNatives(), judge).use { session ->
                show(session, judge)
                session.advance(0.5f, HandleInput())
                show(session, judge)
                flow.pause()
                show(session, judge)
                flow.resume()
                show(session, judge)
                // a whole synthetic flight gives the sheet
                val schedule = SyntheticSchedule()
                schedule.everythingBeforeTheLanding()
                schedule.landing()
                schedule.end(FlightEnd.LANDED)
                flow.flightEnded(FlightReport("Stunter", FlightEnd.LANDED, 300f, FlightResult.Pattern(AssistLevel.MID, schedule.judge.sheet)))
                show(session, schedule.judge)
                flow.nameEntered("Ann")
                show()
                flow.scores()
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
        } finally {
            ui.dispose()
            skin.dispose()
        }
    }
}
