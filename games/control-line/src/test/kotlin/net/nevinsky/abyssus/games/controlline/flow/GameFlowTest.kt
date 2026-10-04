/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.flow

import net.nevinsky.abyssus.games.controlline.components.PlaneClass
import net.nevinsky.abyssus.games.controlline.flight.FieldFlight
import net.nevinsky.abyssus.games.controlline.flight.FlightEnd
import net.nevinsky.abyssus.games.controlline.loadField
import net.nevinsky.abyssus.games.controlline.score.ScoreEntry
import net.nevinsky.abyssus.games.controlline.score.ScoreTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class GameFlowTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val planes = loadField().engine.let { engine -> planeChoices(engine.ids.ids.associateWith { engine.ids[it]!! }) }

    private fun table() = ScoreTable(temp.root.toPath().resolve("scores.json"))

    private fun flow(table: ScoreTable = table()) = GameFlow(planes, table) { Instant.parse("2026-10-04T12:00:00Z") }

    private fun GameFlow.flyStunter(): Screen.Flying {
        start()
        nextPlane()
        choosePlane()
        return screen as Screen.Flying
    }

    private fun report(score: Int) = FlightReport("Stunter", FlightEnd.CRASHED, score, laps = 4, bestCombo = 2, flightTime = 42f)

    @Test
    fun theGameOpensOnTheMainMenu() = assertEquals(Screen.MainMenu, flow().screen)

    @Test
    fun startOpensPlaneSelect() {
        val flow = flow()
        assertTrue(flow.startEnabled)
        assertNull(flow.message)
        flow.start()
        assertEquals(Screen.PlaneSelect(0), flow.screen)
    }

    @Test
    fun noPlanesDisablesStartWithAMessage() {
        val flow = GameFlow(emptyList(), table())
        assertFalse(flow.startEnabled)
        assertEquals("No plane was found in the field scene", flow.message)
        flow.start()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun theBundledPlanesAreListedByName() {
        assertEquals(listOf("Racer", "Stunter", "Trainer"), planes.map { it.name })
        val stunter = planes[1]
        assertEquals(PlaneClass.STUNT, stunter.planeClass)
        assertEquals(18f, stunter.lineLength, 0f)
        assertEquals(1.1f, stunter.mass, 1e-6f)
        assertEquals(180f, stunter.fuelTime, 0f)
    }

    @Test
    fun leftAndRightChangeThePlaneAndEscapeReturns() {
        val flow = flow()
        flow.start()
        flow.nextPlane()
        assertEquals(Screen.PlaneSelect(1), flow.screen)
        flow.previousPlane()
        flow.previousPlane()
        assertEquals(Screen.PlaneSelect(2), flow.screen)
        flow.back()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun enterFliesTheShownPlane() {
        val flying = flow().flyStunter()
        assertEquals("Stunter", flying.plane.name)
        assertEquals(1, flying.attempt)
    }

    @Test
    fun escapePausesAndResumeContinues() {
        val flow = flow()
        val flying = flow.flyStunter()
        flow.back()
        assertEquals(Screen.Paused(flying), flow.screen)
        flow.resume()
        assertEquals(flying, flow.screen)
        flow.pause()
        flow.mainMenu()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun aHighScoreCrashAsksForANameSavesAndOffersRetryScoresAndMainMenu() {
        val table = table()
        val flow = flow(table)
        val flying = flow.flyStunter()
        flow.flightEnded(report(320))
        val entry = flow.screen as Screen.NameEntry
        assertEquals("", entry.name)
        flow.nameEntered("  Amelia Earhart-Putnam ")
        val over = flow.screen as Screen.GameOver
        assertEquals(flying, over.flying)
        assertEquals(FlightEnd.CRASHED, over.report.end)
        assertEquals("Crashed", over.report.end.label)
        assertEquals(1, over.rank)
        assertEquals(listOf(ScoreEntry("Amelia Earha", "Stunter", 320, 4, 2, 42f, "2026-10-04T12:00:00Z")), table.entries)

        // the next name entry offers the last name
        flow.retry()
        flow.flightEnded(report(100))
        assertEquals("Amelia Earha", (flow.screen as Screen.NameEntry).name)
    }

    @Test
    fun aScoreOutsideTheTableGoesStraightToGameOver() {
        val table = table()
        repeat(10) { table.add(ScoreEntry("P$it", "Racer", 500 + it, 1, 1, 10f, "2026-10-01T00:00:00Z")) }
        val flow = flow(table)
        flow.flyStunter()
        flow.flightEnded(report(20))
        assertEquals(Screen.GameOver(Screen.Flying(planes[1]), report(20), null), flow.screen)
    }

    @Test
    fun retryFliesTheSamePlaneAgain() {
        val flow = flow()
        val flying = flow.flyStunter()
        flow.flightEnded(report(10))
        flow.nameEntered("Ann")
        flow.retry()
        assertEquals(Screen.Flying(flying.plane, attempt = 2), flow.screen)
    }

    @Test
    fun aNewFlightStartsFromTakeoffAtScoreZero() = FieldFlight("Stunter").use { f ->
        f.fly(15f)
        assertTrue(f.flight.scoring.keeper.score > 0)
        f.flight.takeoff()
        assertEquals(0, f.flight.scoring.keeper.score)
        assertEquals(0f, f.flight.time, 0f)
        assertTrue("not back on the ground: ${f.height} m", f.height < 0.3f)
    }

    @Test
    fun scoresFromGameOverHighlightTheSavedRankAndBackReturns() {
        val table = table()
        table.add(ScoreEntry("Best", "Racer", 900, 9, 3, 60f, "2026-10-01T00:00:00Z"))
        table.add(ScoreEntry("Low", "Racer", 100, 1, 1, 60f, "2026-10-01T00:00:00Z"))
        val flow = flow(table)
        flow.flyStunter()
        flow.flightEnded(report(450))
        flow.nameEntered("Ann")
        val over = flow.screen as Screen.GameOver
        flow.scores()
        assertEquals(Screen.Scores(over, highlight = 2), flow.screen)
        assertEquals("Ann", table.entries[1].name)
        flow.back()
        assertEquals(over, flow.screen)
    }

    @Test
    fun scoresFromTheMainMenuGoBackToIt() {
        val flow = flow()
        flow.scores()
        assertEquals(Screen.Scores(Screen.MainMenu, null), flow.screen)
        flow.back()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun quitClosesFromTheMainMenu() {
        val flow = flow()
        flow.quitGame()
        assertTrue(flow.quit)
    }
}
