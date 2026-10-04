/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ScoreTableTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): Path = temp.root.toPath().resolve("home/.abyssus-control-line/scores.json")

    private fun entry(name: String, score: Int, date: String = "2026-10-04T12:00:00Z") =
        ScoreEntry(name, "Stunter", score, laps = 3, bestCombo = 2, flightTime = 61.5f, date = date)

    @Test
    fun theDefaultFileIsInTheHomeFolder() =
        assertEquals(Path.of("/home/pilot/.abyssus-control-line/scores.json"), defaultScoresFile("/home/pilot"))

    @Test
    fun anEmptyTableTakesAnyFlightAndWritesTheFile() {
        val table = ScoreTable(file())
        assertFalse(table.damaged)
        assertEquals(1, table.add(entry("Ann", 0)))
        assertEquals(listOf("Ann"), ScoreTable(file()).entries.map { it.name })
    }

    @Test
    fun eleventhFlightReplacesTheLowestAndKeepsOrder() {
        val table = ScoreTable(file())
        val scores = listOf(500, 120, 300, 200, 450, 130, 900, 250, 160, 700)
        scores.forEachIndexed { i, s -> table.add(entry("P$i", s)) }
        assertEquals(10, table.entries.size)
        assertEquals(120, table.entries.last().score)

        assertTrue(table.qualifies(150))
        assertEquals(9, table.add(entry("New", 150)))

        val saved = ScoreTable(file()).entries
        assertEquals(10, saved.size)
        assertEquals(listOf(900, 700, 500, 450, 300, 250, 200, 160, 150, 130), saved.map { it.score })
        assertFalse(saved.any { it.score == 120 })
    }

    @Test
    fun aFlightEqualToTheLowestOfAFullTableDoesNotEnter() {
        val table = ScoreTable(file())
        repeat(10) { table.add(entry("P$it", 100 + it)) }
        assertFalse(table.qualifies(100))
        assertNull(table.add(entry("Late", 100)))
    }

    @Test
    fun equalScoresKeepTheEarlierFlightFirst() {
        val table = ScoreTable(file())
        table.add(entry("First", 200, "2026-10-01T10:00:00Z"))
        table.add(entry("Second", 200, "2026-10-02T10:00:00Z"))
        table.add(entry("Third", 300, "2026-10-03T10:00:00Z"))
        assertEquals(listOf("Third", "First", "Second"), ScoreTable(file()).entries.map { it.name })
    }

    @Test
    fun aFileThatIsNotJsonBecomesBad() {
        Files.createDirectories(file().parent)
        Files.writeString(file(), "these are not scores")
        Files.writeString(file().resolveSibling("scores.json.bad"), "an older bad file")
        val table = ScoreTable(file())
        assertTrue(table.damaged)
        assertEquals(emptyList<ScoreEntry>(), table.entries)
        assertFalse(Files.exists(file()))
        assertEquals("these are not scores", Files.readString(file().resolveSibling("scores.json.bad")))
    }

    @Test
    fun anUnknownVersionBecomesBad() {
        Files.createDirectories(file().parent)
        Files.writeString(file(), """{"version": 2, "entries": []}""")
        assertTrue(ScoreTable(file()).damaged)
    }

    @Test
    fun theFileHoldsEveryField() {
        ScoreTable(file()).add(entry("Ann", 420))
        val text = Files.readString(file())
        for (key in listOf("\"version\" : 1", "\"name\"", "\"plane\"", "\"score\"", "\"laps\"", "\"bestCombo\"", "\"flightTime\"", "\"date\"")) {
            assertTrue("$key in $text", text.contains(key))
        }
    }
}
