/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class PatternScoreTableTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun home(): Path = temp.root.toPath().resolve("home/.abyssus-control-line")

    private fun file(): Path = home().resolve("f2b-scores.json")

    private fun entry(name: String, score: Double, date: String = "2026-10-04T12:00:00Z") =
        PatternScoreEntry(name, "Stunter", score, 412.5f, date, List(15) { 7.5 })

    @Test
    fun theDefaultFileIsInTheHomeFolder() =
        assertEquals(Path.of("/home/pilot/.abyssus-control-line/f2b-scores.json"), defaultPatternScoresFile("/home/pilot"))

    @Test
    fun aMidFlightEntersOnlyTheMidBoardWithItsFifteenMarks() {
        val table = PatternScoreTable(file())
        repeat(3) { table.add(AssistLevel.MID, entry("P$it", 500.0 + it)) }
        assertEquals(1, table.add(AssistLevel.MID, entry("Mid", 612.4)))
        val saved = PatternScoreTable(file())
        assertEquals(4, saved.board(AssistLevel.MID).size)
        assertEquals("Mid", saved.board(AssistLevel.MID).first().name)
        assertEquals(15, saved.board(AssistLevel.MID).first().marks.size)
        assertTrue(saved.board(AssistLevel.PRO).isEmpty())
        assertTrue(saved.board(AssistLevel.NOOB).isEmpty())
    }

    @Test
    fun theEleventhFlightReplacesTheLowest() {
        val table = PatternScoreTable(file())
        listOf(500.0, 120.0, 300.0, 200.0, 450.0, 130.0, 900.0, 250.0, 160.0, 700.0).forEachIndexed { i, s -> table.add(AssistLevel.PRO, entry("P$i", s)) }
        assertTrue(table.qualifies(AssistLevel.PRO, 150.0))
        assertEquals(9, table.add(AssistLevel.PRO, entry("New", 150.0)))
        assertEquals(listOf(900.0, 700.0, 500.0, 450.0, 300.0, 250.0, 200.0, 160.0, 150.0, 130.0), PatternScoreTable(file()).board(AssistLevel.PRO).map { it.score })
        assertFalse(table.qualifies(AssistLevel.PRO, 130.0))
        assertNull(table.add(AssistLevel.PRO, entry("Late", 130.0)))
        // another board is not full
        assertTrue(table.qualifies(AssistLevel.MID, 1.0))
    }

    @Test
    fun equalScoresKeepTheEarlierFlightFirst() {
        val table = PatternScoreTable(file())
        table.add(AssistLevel.PRO, entry("First", 200.0, "2026-10-01T10:00:00Z"))
        table.add(AssistLevel.PRO, entry("Second", 200.0, "2026-10-02T10:00:00Z"))
        table.add(AssistLevel.PRO, entry("Third", 300.0, "2026-10-03T10:00:00Z"))
        assertEquals(listOf("Third", "First", "Second"), PatternScoreTable(file()).board(AssistLevel.PRO).map { it.name })
    }

    @Test
    fun textThatIsNotJsonBecomesBadAndTheBoardsStartEmpty() {
        Files.createDirectories(home())
        Files.writeString(file(), "these are not scores")
        Files.writeString(home().resolve("f2b-scores.json.bad"), "an older bad file")
        Files.writeString(home().resolve("scores.json"), """{"version": 1, "entries": []}""")
        val table = PatternScoreTable(file())
        assertTrue(table.damaged)
        assertTrue(table.board(AssistLevel.MID).isEmpty())
        assertFalse(Files.exists(file()))
        assertEquals("these are not scores", Files.readString(home().resolve("f2b-scores.json.bad")))
        assertEquals("""{"version": 1, "entries": []}""", Files.readString(home().resolve("scores.json")))
    }

    @Test
    fun savingLeavesScoresJsonAlone() {
        Files.createDirectories(home())
        Files.writeString(home().resolve("scores.json"), "free flight scores")
        PatternScoreTable(file()).add(AssistLevel.MID, entry("Ann", 400.0))
        assertEquals("free flight scores", Files.readString(home().resolve("scores.json")))
    }

    @Test
    fun theFileHoldsEveryFieldAndAMissingBoardReadsEmpty() {
        PatternScoreTable(file()).add(AssistLevel.MID, entry("Ann", 612.4))
        val text = Files.readString(file())
        for (key in listOf("\"version\" : 1", "\"boards\"", "\"mid\"", "\"name\"", "\"plane\"", "\"score\" : 612.4", "\"flightTime\"", "\"date\"", "\"marks\"")) {
            assertTrue("$key in $text", text.contains(key))
        }
        Files.writeString(file(), """{"version": 1, "boards": {"pro": []}}""")
        assertTrue(PatternScoreTable(file()).board(AssistLevel.MID).isEmpty())
    }

    @Test
    fun anUnknownVersionBecomesBad() {
        Files.createDirectories(home())
        Files.writeString(file(), """{"version": 2, "boards": {}}""")
        assertTrue(PatternScoreTable(file()).damaged)
    }
}
