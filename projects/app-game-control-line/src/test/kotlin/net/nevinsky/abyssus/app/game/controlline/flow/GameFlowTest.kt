/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.flow

import net.nevinsky.abyssus.app.game.controlline.components.PlaneClass
import net.nevinsky.abyssus.app.game.controlline.flight.FieldFlight
import net.nevinsky.abyssus.app.game.controlline.flight.FlightEnd
import net.nevinsky.abyssus.app.game.controlline.loadField
import net.nevinsky.abyssus.app.game.controlline.pattern.AssistLevel
import net.nevinsky.abyssus.app.game.controlline.pattern.Figure
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternScoreTable
import net.nevinsky.abyssus.app.game.controlline.pattern.ScoreSheet
import net.nevinsky.abyssus.app.game.controlline.pattern.ZeroReason
import net.nevinsky.abyssus.app.game.controlline.pattern.recordedFlight
import net.nevinsky.abyssus.app.game.controlline.pattern.SheetEntry
import net.nevinsky.abyssus.app.game.controlline.score.ScoreEntry
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
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

    private fun patternTable() = PatternScoreTable(temp.root.toPath().resolve("f2b-scores.json"))

    private fun flow(table: ScoreTable = table(), patternTable: PatternScoreTable = patternTable(), planes: List<PlaneChoice> = this.planes) =
        GameFlow(planes, table, patternTable) { Instant.parse("2026-10-04T12:00:00Z") }

    private fun GameFlow.flyStunter(): Screen.Flying {
        freeFlight()
        nextPlane()
        choosePlane()
        return screen as Screen.Flying
    }

    private fun GameFlow.flyPattern(level: AssistLevel): Screen.Flying {
        f2bPattern()
        chooseAssist(level)
        choosePlane()
        return screen as Screen.Flying
    }

    private fun report(score: Int) = FlightReport("Stunter", FlightEnd.CRASHED, 42f, FlightResult.Free(score, laps = 4, bestCombo = 2))

    /** An F2B report whose sheet has Take-off marked [tenths] and every other manoeuvre 0. */
    private fun patternReport(level: AssistLevel, tenths: Int): FlightReport {
        val sheet = ScoreSheet()
        for (figure in Figure.entries) sheet[figure] = if (figure == Figure.TAKEOFF) SheetEntry(figure, tenths, null, null, 30f)
        else SheetEntry(figure, 0, net.nevinsky.abyssus.app.game.controlline.pattern.ZeroReason.OMITTED, null, 30f)
        return FlightReport("Stunter", FlightEnd.LANDED, 61f, FlightResult.Pattern(level, sheet))
    }

    @Test
    fun theGameOpensOnTheMainMenu() = assertEquals(Screen.MainMenu, flow().screen)

    @Test
    fun freeFlightOpensPlaneSelectWithEveryPlane() {
        val flow = flow()
        assertTrue(flow.freeEnabled)
        assertTrue(flow.patternEnabled)
        assertNull(flow.message)
        flow.freeFlight()
        assertEquals(Screen.PlaneSelect(0, Mode.Free), flow.screen)
        assertEquals(planes, flow.choices(Mode.Free))
    }

    @Test
    fun noPlanesDisablesBothWithAMessage() {
        val flow = flow(planes = emptyList())
        assertFalse(flow.freeEnabled)
        assertFalse(flow.patternEnabled)
        assertEquals("No plane was found in the field scene", flow.message)
        flow.freeFlight()
        flow.f2bPattern()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun noStuntPlaneDisablesTheF2bPatternWithAMessage() {
        val flow = flow(planes = planes.filter { it.planeClass != PlaneClass.STUNT })
        assertTrue(flow.freeEnabled)
        assertFalse(flow.patternEnabled)
        assertEquals("The F2B pattern needs a stunt plane", flow.message)
        flow.f2bPattern()
        assertEquals(Screen.MainMenu, flow.screen)
        flow.freeFlight()
        assertEquals(Screen.PlaneSelect(0, Mode.Free), flow.screen)
    }

    @Test
    fun theF2bPatternAsksForAnAssistLevelThenOffersOnlyStuntPlanes() {
        val flow = flow()
        flow.f2bPattern()
        assertEquals(Screen.AssistSelect, flow.screen)
        flow.chooseAssist(AssistLevel.MID)
        assertEquals(Screen.PlaneSelect(0, Mode.Pattern(AssistLevel.MID)), flow.screen)
        assertEquals(listOf("Stunter"), flow.choices(Mode.Pattern(AssistLevel.MID)).map { it.name })
        assertEquals(360f, flow.choices(Mode.Pattern(AssistLevel.MID)).single().fuelTime, 0f)
        flow.nextPlane()
        assertEquals(Screen.PlaneSelect(0, Mode.Pattern(AssistLevel.MID)), flow.screen)
        flow.choosePlane()
        assertEquals(Screen.Flying(planes[1], 1, Mode.Pattern(AssistLevel.MID)), flow.screen)
    }

    @Test
    fun noobIsOfferedAfterProAndMid() {
        assertEquals(listOf(AssistLevel.PRO, AssistLevel.MID, AssistLevel.NOOB), OFFERED_LEVELS)
        val flow = flow()
        flow.f2bPattern()
        flow.chooseAssist(AssistLevel.NOOB)
        assertEquals(Screen.PlaneSelect(0, Mode.Pattern(AssistLevel.NOOB)), flow.screen)
        flow.nextPlane()
        flow.choosePlane()
        assertEquals(Screen.Flying(planes[1], 1, Mode.Pattern(AssistLevel.NOOB)), flow.screen)
    }

    @Test
    fun onlyNoobGuidesAndMidAndNoobPrompt() {
        assertEquals(listOf(false, false, true), AssistLevel.entries.map { it.guided })
        assertEquals(listOf(false, true, true), AssistLevel.entries.map { it.prompted })
    }

    @Test
    fun aNoobFlightIsSavedToTheNoobBoardOnlyAndItsRowOpensItsDebrief() {
        val flow = flow()
        flow.flyAndSave(AssistLevel.NOOB, 85)
        assertEquals(1, flow.patternTable.board(AssistLevel.NOOB).size)
        assertTrue(flow.patternTable.board(AssistLevel.PRO).isEmpty() && flow.patternTable.board(AssistLevel.MID).isEmpty())
        flow.scores()
        val scores = flow.screen as Screen.Scores
        assertEquals(ScoreTab.NOOB, scores.tab)
        assertEquals(ScoreTab.NOOB, scores.highlightTab)
        assertEquals(1, scores.highlight)
        flow.openRow()
        assertTrue((flow.screen as Screen.Debrief).model.title.contains("Noob"))
        flow.back()
        flow.back()
        flow.debrief()
        assertTrue((flow.screen as Screen.Debrief).model.title.contains("Noob"))
    }

    @Test
    fun retryKeepsNoob() {
        val flow = flow()
        val flying = flow.flyPattern(AssistLevel.NOOB)
        flow.flightEnded(patternReport(AssistLevel.NOOB, 85))
        flow.nameEntered("Ann")
        flow.retry()
        assertEquals(Screen.Flying(flying.plane, 2, Mode.Pattern(AssistLevel.NOOB)), flow.screen)
    }

    @Test
    fun escapeGoesBackThroughTheAssistLevelScreen() {
        val flow = flow()
        flow.f2bPattern()
        flow.chooseAssist(AssistLevel.PRO)
        flow.back()
        assertEquals(Screen.AssistSelect, flow.screen)
        flow.back()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun theBundledPlanesAreListedByName() {
        assertEquals(listOf("Racer", "Stunter", "Trainer"), planes.map { it.name })
        val stunter = planes[1]
        assertEquals(PlaneClass.STUNT, stunter.planeClass)
        assertEquals(18f, stunter.lineLength, 0f)
        assertEquals(1.1f, stunter.mass, 1e-6f)
        assertEquals(360f, stunter.fuelTime, 0f)
    }

    @Test
    fun leftAndRightChangeThePlaneAndEscapeReturns() {
        val flow = flow()
        flow.freeFlight()
        flow.nextPlane()
        assertEquals(Screen.PlaneSelect(1, Mode.Free), flow.screen)
        flow.previousPlane()
        flow.previousPlane()
        assertEquals(Screen.PlaneSelect(2, Mode.Free), flow.screen)
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
        assertTrue(flow.patternTable.board(AssistLevel.PRO).isEmpty() && flow.patternTable.board(AssistLevel.MID).isEmpty())
        assertFalse(java.nio.file.Files.exists(temp.root.toPath().resolve("f2b-scores.json")))

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
        assertEquals(Screen.GameOver(Screen.Flying(planes[1], 1, Mode.Free), report(20), null), flow.screen)
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
        assertEquals(Screen.Scores(over, highlight = 2, tab = ScoreTab.FREE), flow.screen)
        assertEquals("Ann", table.entries[1].name)
        flow.back()
        assertEquals(over, flow.screen)
    }

    @Test
    fun scoresFromTheMainMenuGoBackToIt() {
        val flow = flow()
        flow.scores()
        assertEquals(Screen.Scores(Screen.MainMenu, null, ScoreTab.FREE), flow.screen)
        flow.back()
        assertEquals(Screen.MainMenu, flow.screen)
    }

    @Test
    fun quitClosesFromTheMainMenu() {
        val flow = flow()
        flow.quitGame()
        assertTrue(flow.quit)
    }

    @Test
    fun retryKeepsTheModeAndLevel() {
        val flow = flow()
        val flying = flow.flyPattern(AssistLevel.PRO)
        flow.flightEnded(patternReport(AssistLevel.PRO, 85))
        flow.nameEntered("Ann")
        flow.retry()
        assertEquals(Screen.Flying(flying.plane, 2, Mode.Pattern(AssistLevel.PRO)), flow.screen)
    }

    @Test
    fun aProFlightThatRanksFirstOpensScoresOnTheProTabHighlighted() {
        val flow = flow()
        flow.flyPattern(AssistLevel.PRO)
        flow.flightEnded(patternReport(AssistLevel.PRO, 85))
        flow.nameEntered("Ann")
        val over = flow.screen as Screen.GameOver
        assertEquals(1, over.rank)
        flow.scores()
        val scores = flow.screen as Screen.Scores
        assertEquals(ScoreTab.PRO, scores.tab)
        assertEquals(ScoreTab.PRO, scores.highlightTab)
        assertEquals(1, scores.highlight)
        assertEquals(1, flow.patternTable.board(AssistLevel.PRO).size)
        assertEquals(17.0, flow.patternTable.board(AssistLevel.PRO).single().score, 1e-9)
        assertTrue(flow.patternTable.board(AssistLevel.MID).isEmpty())
        flow.back()
        assertEquals(over, flow.screen)
    }

    @Test
    fun theScoreTabsChangeWithLeftAndRight() {
        val flow = flow()
        flow.scores()
        assertEquals(ScoreTab.FREE, (flow.screen as Screen.Scores).tab)
        flow.nextTab()
        assertEquals(ScoreTab.PRO, (flow.screen as Screen.Scores).tab)
        flow.nextTab()
        flow.nextTab()
        assertEquals(ScoreTab.NOOB, (flow.screen as Screen.Scores).tab)
        flow.nextTab()
        assertEquals(ScoreTab.FREE, (flow.screen as Screen.Scores).tab)
        flow.previousTab()
        assertEquals(ScoreTab.NOOB, (flow.screen as Screen.Scores).tab)
    }

    @Test
    fun aFreeFlightIsSavedToScoresJsonOnly() {
        val table = table()
        val flow = flow(table)
        flow.flyStunter()
        flow.flightEnded(report(320))
        flow.nameEntered("Ann")
        assertEquals(1, table.entries.size)
        assertTrue(flow.patternTable.board(AssistLevel.PRO).isEmpty() && flow.patternTable.board(AssistLevel.MID).isEmpty())
        assertFalse(java.nio.file.Files.exists(temp.root.toPath().resolve("f2b-scores.json")))
    }

    @Test
    fun aMidFlightIsSavedToTheMidBoardOnly() {
        val flow = flow()
        flow.flyPattern(AssistLevel.MID)
        flow.flightEnded(patternReport(AssistLevel.MID, 100))
        flow.nameEntered("Ann")
        assertEquals(1, flow.patternTable.board(AssistLevel.MID).size)
        assertTrue(flow.patternTable.board(AssistLevel.PRO).isEmpty())
        assertTrue(flow.table.entries.isEmpty())
    }

    /** A Pro or Mid report that carries a recording, like the one the judge hands over. */
    private fun recordedReport(level: AssistLevel, tenths: Int): FlightReport {
        val report = patternReport(level, tenths)
        val result = report.result as FlightResult.Pattern
        return report.copy(result = result.copy(recording = recordedFlight(level)))
    }

    private fun GameFlow.flyAndSave(level: AssistLevel, tenths: Int) {
        flyPattern(level)
        flightEnded(recordedReport(level, tenths))
        nameEntered("Ann")
    }

    @Test
    fun debriefAfterACrashOpensOnTakeoffAndEscapeReturnsToGameOver() {
        val flow = flow()
        flow.flyPattern(AssistLevel.PRO)
        flow.flightEnded(recordedReport(AssistLevel.PRO, 85))
        flow.nameEntered("Ann")
        val over = flow.screen as Screen.GameOver
        flow.debrief()
        val debrief = flow.screen as Screen.Debrief
        assertEquals(over, debrief.from)
        assertEquals(Figure.TAKEOFF, debrief.model.row.figure)
        assertEquals(0, debrief.model.selected)
        assertEquals(ZeroReason.CRASHED, debrief.model.rows[Figure.INSIDE_SQUARES.ordinal].reason)
        assertTrue(debrief.model.recording != null)
        flow.back()
        assertEquals(over, flow.screen)
    }

    @Test
    fun aFlightThatEntersNoBoardCanBeDebriefedToo() {
        val flow = flow()
        flow.flyPattern(AssistLevel.MID)
        flow.flightEnded(recordedReport(AssistLevel.MID, 85))
        flow.nameEntered("Ann")
        flow.retry()
        flow.flightEnded(recordedReport(AssistLevel.MID, 0).let { it.copy(result = (it.result as FlightResult.Pattern).copy(sheet = ScoreSheet())) })
        // a zero score still qualifies on a board with room, so the name is asked first
        flow.nameEntered("Ann")
        flow.debrief()
        assertTrue(flow.screen is Screen.Debrief)
    }

    @Test
    fun freeFlightHasNoDebrief() {
        val flow = flow()
        flow.flyStunter()
        flow.flightEnded(report(320))
        flow.nameEntered("Ann")
        val over = flow.screen
        flow.debrief()
        assertEquals(over, flow.screen)
    }

    @Test
    fun replayingTheBestProFlightEscapeReturnsToTheProTabWithTheFirstRowSelected() {
        val flow = flow()
        flow.flyAndSave(AssistLevel.PRO, 85)
        flow.scores() // from GAME OVER: on the tab of the flight
        val scores = flow.screen as Screen.Scores
        assertEquals(ScoreTab.PRO, scores.tab)
        assertEquals(0, scores.selected)
        flow.openRow()
        val debrief = flow.screen as Screen.Debrief
        assertTrue(debrief.model.recording != null)
        assertEquals(null, debrief.model.notice)
        flow.back()
        val back = flow.screen as Screen.Scores
        assertEquals(ScoreTab.PRO, back.tab)
        assertEquals(0, back.selected)
        // and from there on to GAME OVER, which a flight's score table came from
        flow.back()
        assertTrue(flow.screen is Screen.GameOver)
    }

    @Test
    fun upAndDownSelectARowWithinTheBoardAndEnterOpensIt() {
        val flow = flow()
        flow.flyAndSave(AssistLevel.MID, 85)
        flow.retry()
        flow.flightEnded(recordedReport(AssistLevel.MID, 60))
        flow.nameEntered("Bob")
        flow.scores()
        flow.selectRow(-1)
        assertEquals(0, (flow.screen as Screen.Scores).selected)
        flow.selectRow(1)
        flow.selectRow(1)
        assertEquals(1, (flow.screen as Screen.Scores).selected)
        flow.openRow()
        assertTrue((flow.screen as Screen.Debrief).model.title.contains("Mid"))
        flow.back()
        assertEquals(1, (flow.screen as Screen.Scores).selected)
        flow.nextTab()
        flow.nextTab()
        assertEquals(0, (flow.screen as Screen.Scores).selected)
        // the Free flight tab has no rows to open
        assertEquals(ScoreTab.FREE, (flow.screen as Screen.Scores).tab)
        flow.openRow()
        assertTrue(flow.screen is Screen.Scores)
    }

    @Test
    fun aMissingRecordingOpensTheSheetOnly() {
        val flow = flow()
        flow.flyAndSave(AssistLevel.PRO, 85)
        val id = checkNotNull(flow.patternTable.board(AssistLevel.PRO).single().flight)
        java.nio.file.Files.delete(temp.root.toPath().resolve("f2b-flights/$id.json.gz"))
        flow.scores()
        flow.openRow()
        val model = (flow.screen as Screen.Debrief).model
        assertEquals(null, model.recording)
        assertEquals(15, model.rows.size)
        assertEquals(net.nevinsky.abyssus.app.game.controlline.pattern.NO_REPLAY_MESSAGE, model.notice)
        assertEquals(1, flow.patternTable.board(AssistLevel.PRO).size)
    }
}
