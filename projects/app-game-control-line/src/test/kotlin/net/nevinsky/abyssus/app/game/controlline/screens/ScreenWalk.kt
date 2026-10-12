/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Input
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.components.PilotComponent
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.*
import net.nevinsky.abyssus.app.game.controlline.input.HandleInput
import net.nevinsky.abyssus.app.game.controlline.named
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.pattern.SyntheticSchedule
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import net.nevinsky.abyssus.lib.core.scene.SceneContext
import net.nevinsky.abyssus.lib.gdx.testing.failOnWarnings
import net.nevinsky.abyssus.lib.physics.PhysicsAssets
import net.nevinsky.abyssus.lib.physics.jolt.JoltNatives
import java.nio.file.Path

/**
 * What a UI implementation must offer to be walked through the game's screens and compared line by line: it follows the
 * flow, takes keys, and says what it shows as text lines (`label|role|text`, `button|state|text`, `field|text`, `bar|fraction`).
 */
interface ScreenDriver {
    fun update(session: FlightSession?, judge: PatternJudge?, seconds: Float)
    fun keyDown(keycode: Int): Boolean
    fun dump(): List<String>
}

/**
 * The same sequence of game states for any [ScreenDriver]: every screen, the HUD of each mode at each assist level, the
 * pause screen, name entry, results, the debrief and the score tables. Returns what was shown at each named step, so one
 * recording made with the original screens can be compared with another implementation.
 */
class ScreenWalk(private val scene: SceneContext, private val dir: Path) {
    private val planes = planeChoices(scene.engine.ids.ids.associateWith { scene.engine.ids[it]!! })

    /** A fixed clock, so saved flights always carry the same date. */
    private val clock = { java.time.Instant.parse("2026-01-02T03:04:05Z") }

    fun flow() = GameFlow(planes, ScoreTable(dir.resolve("scores.json")), PatternScoreTable(dir.resolve("f2b-scores.json")), clock)

    /** Runs the walk on a driver built over a fresh flow; [driver] gets the flow it must follow. */
    fun run(driver: (GameFlow) -> ScreenDriver): Map<String, List<String>> {
        val steps = LinkedHashMap<String, List<String>>()
        val flow = flow()
        val ui = driver(flow)
        fun show(name: String, session: FlightSession? = null, judge: PatternJudge? = null) {
            ui.update(session, judge, 1f / 60f)
            steps[name] = ui.dump()
        }
        fun flying(name: String, level: AssistLevel?, check: (FlightSession, PatternJudge?) -> Unit) {
            val plane = (flow.screen as Screen.Flying).plane
            val entity = scene.named(plane.name)
            val pilot = scene.engine.entities.single { it.getComponent(PilotComponent::class.java) != null }
            val judge = level?.let { PatternJudge(plane.lineLength) }
            FlightSession(scene.engine, pilot, entity, PhysicsAssets(bundledProject().toFile()), failOnWarnings(), JoltNatives(), judge).use { session ->
                show(name, session, judge)
                check(session, judge)
            }
        }

        show("main-menu")
        flow.f2bPattern(); show("assist-select")
        flow.chooseAssist(AssistLevel.MID); show("plane-select-mid")
        flow.choosePlane()
        flying("hud-mid", AssistLevel.MID) { session, judge ->
            flow.pause(); show("paused-mid", session, judge)
            flow.resume()
        }
        // a whole synthetic flight gives the sheet: name entry, GAME OVER, the debrief and the score tables
        val schedule = SyntheticSchedule().also { it.everythingBeforeTheLanding(); it.landing(); it.end(FlightEnd.LANDED) }
        flow.flightEnded(FlightReport("Stunter", FlightEnd.LANDED, 300f,
            FlightResult.Pattern(AssistLevel.MID, schedule.judge.sheet, schedule.judge.recording("Stunter", AssistLevel.MID))))
        show("name-entry-mid")
        flow.nameEntered("Ann"); show("game-over-mid")
        flow.debrief(); show("debrief-first")
        ui.keyDown(Input.Keys.DOWN); show("debrief-second")
        ui.keyDown(Input.Keys.ESCAPE); show("game-over-after-debrief")
        flow.scores(); show("scores-mid-saved")
        flow.nextTab(); show("scores-noob-empty")
        flow.nextTab(); show("scores-free-empty")
        flow.nextTab(); show("scores-pro-empty")
        flow.back(); flow.mainMenu(); show("main-menu-again")

        flow.freeFlight(); show("plane-select-free")
        flow.choosePlane()
        flying("hud-free", null) { _, _ -> }
        flow.flightEnded(FlightReport("Stunter", FlightEnd.CRASHED, 95f, FlightResult.Free(1250, 12, 3)))
        show("name-entry-free")
        flow.nameEntered("Bob"); show("game-over-free")
        flow.scores(); show("scores-free-saved")
        flow.back(); flow.mainMenu()

        for ((level, name) in listOf(AssistLevel.PRO to "hud-pro", AssistLevel.NOOB to "hud-noob")) {
            flow.f2bPattern(); flow.chooseAssist(level); flow.choosePlane()
            flying(name, level) { _, _ -> }
            flow.pause(); flow.mainMenu()
        }
        // a field without planes
        val empty = GameFlow(emptyList(), ScoreTable(dir.resolve("none.json")), PatternScoreTable(dir.resolve("none-f2b.json")), clock)
        driver(empty).let { other -> other.update(null, null, 1f / 60f); steps["main-menu-no-planes"] = other.dump() }
        return steps
    }
}

/** The golden file text of [steps]: `== name` then the lines of that step. */
fun renderSteps(steps: Map<String, List<String>>): String = steps.entries.joinToString("\n") { (name, lines) -> "== $name\n" + lines.joinToString("\n") }.let { it + "\n" }
