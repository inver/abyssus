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

    private fun flights(): Path = home().resolve("f2b-flights")

    private fun names(): Set<String> = Files.list(flights()).use { files -> files.map { it.fileName.toString() }.toList().toSet() }

    @Test
    fun aMidFlightEnteringSavesItsRecordingAndNamesIt() {
        val table = PatternScoreTable(file())
        table.add(AssistLevel.MID, entry("Mid", 612.4), recordedFlight(AssistLevel.MID))
        val id = checkNotNull(PatternScoreTable(file()).board(AssistLevel.MID).single().flight)
        assertEquals(setOf("$id.json.gz"), names())
        assertEquals("Stunter", checkNotNull(table.recordings.load(id)).plane)
        assertTrue(Files.readString(file()).contains("\"flight\" : \"$id\""))
    }

    @Test
    fun theEleventhFlightDeletesTheLeavingFlightsRecording() {
        val table = PatternScoreTable(file())
        val recording = recordedFlight()
        for (i in 0 until 10) table.add(AssistLevel.PRO, entry("P$i", 100.0 + i * 10), recording)
        val leaving = checkNotNull(table.board(AssistLevel.PRO).last().flight)
        assertEquals(10, names().size)
        table.add(AssistLevel.PRO, entry("New", 150.0), recording)
        assertEquals(10, names().size)
        assertFalse(names().contains("$leaving.json.gz"))
        assertTrue(names().contains(table.board(AssistLevel.PRO).first { it.name == "New" }.flight + ".json.gz"))
    }

    @Test
    fun aFlightThatDoesNotQualifyWritesNothing() {
        val table = PatternScoreTable(file())
        for (i in 0 until 10) table.add(AssistLevel.PRO, entry("P$i", 100.0 + i * 10), recordedFlight())
        val before = names()
        assertNull(table.add(AssistLevel.PRO, entry("Late", 50.0), recordedFlight()))
        assertEquals(before, names())
    }

    @Test
    fun anEntryWithoutAFlightStillLoadsAndUnknownFieldsAreIgnored() {
        Files.createDirectories(home())
        Files.writeString(file(), """{"version": 1, "boards": {"pro": [{"name": "Old", "plane": "Stunter", "score": 300.5, "flightTime": 400.0, "date": "2026-10-04T12:00:00Z", "marks": [7.5], "later": {"x": 1}}]}}""")
        val table = PatternScoreTable(file())
        assertFalse(table.damaged)
        assertNull(table.board(AssistLevel.PRO).single().flight)
        table.add(AssistLevel.PRO, entry("New", 400.0))
        assertFalse(Files.readString(file()).contains("\"flight\""))
    }

    @Test
    fun startPrunesTheRecordingsNoEntryNames() {
        val table = PatternScoreTable(file())
        table.add(AssistLevel.MID, entry("Mid", 600.0), recordedFlight())
        val named = checkNotNull(table.board(AssistLevel.MID).single().flight)
        val orphan = table.recordings.save(recordedFlight())
        assertEquals(2, names().size)
        PatternScoreTable(file())
        assertEquals(setOf("$named.json.gz"), names())
        assertFalse(names().contains("$orphan.json.gz"))
    }

    @Test
    fun aDamagedTableDoesNotPruneTheRecordings() {
        val table = PatternScoreTable(file())
        table.add(AssistLevel.MID, entry("Mid", 600.0), recordedFlight())
        Files.writeString(file(), "not scores")
        assertTrue(PatternScoreTable(file()).damaged)
        assertEquals(1, names().size)
    }

    @Test
    fun aRecordingThatCannotBeSavedKeepsTheEntry() {
        Files.createDirectories(home())
        Files.writeString(flights(), "a file where the folder goes")
        val table = PatternScoreTable(file())
        assertEquals(1, table.add(AssistLevel.MID, entry("Mid", 600.0), recordedFlight()))
        assertNull(PatternScoreTable(file()).board(AssistLevel.MID).single().flight)
    }
}
