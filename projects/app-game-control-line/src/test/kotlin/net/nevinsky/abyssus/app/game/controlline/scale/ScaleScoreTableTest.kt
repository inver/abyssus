/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import net.nevinsky.abyssus.app.game.controlline.score.ScoreEntry
import net.nevinsky.abyssus.app.game.controlline.score.ScoreTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ScaleScoreTableTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val home get() = temp.root.toPath().resolve("home/.abyssus-control-line")

    private fun file(): Path = home.resolve("scale-scores.json")

    private fun entry(name: String, score: Int, date: String = "2026-10-04T12:00:00Z") =
        ScaleScoreEntry(name, "Mig-3", "MiG-3", score, speed = 400, level = 300, landing = 200, laps = 5, flightTime = 61.5f, date = date)

    @Test
    fun theDefaultFileIsInTheHomeFolder() =
        assertEquals(Path.of("/home/pilot/.abyssus-control-line/scale-scores.json"), defaultScaleScoresFile("/home/pilot"))

    @Test
    fun anEmptyTableTakesAnyFlightAndWritesTheFile() {
        val table = ScaleScoreTable(file())
        assertFalse(table.damaged)
        assertEquals(1, table.add(entry("Ann", 0)))
        assertEquals(listOf("Ann"), ScaleScoreTable(file()).entries.map { it.name })
    }

    @Test
    fun theMarksAndPrototypeSurviveTheFile() {
        ScaleScoreTable(file()).add(entry("Ann", 900))
        assertEquals(entry("Ann", 900), ScaleScoreTable(file()).entries.single())
    }

    @Test
    fun aFullTableTurnsAwayAFlightBelowItsLowestEntry() {
        val table = ScaleScoreTable(file())
        listOf(700, 800, 900, 950, 1000, 710, 720, 730, 740, 750).forEachIndexed { i, s -> table.add(entry("P$i", s)) }
        assertEquals(10, table.entries.size)
        assertEquals(700, table.entries.last().score)

        assertFalse(table.qualifies(640))
        assertNull(table.add(entry("Low", 640)))
        assertEquals(10, ScaleScoreTable(file()).entries.size)
        assertEquals(700, ScaleScoreTable(file()).entries.last().score)
    }

    @Test
    fun aBetterFlightReplacesTheLowestAndEarlierFlightsWinTies() {
        val table = ScaleScoreTable(file())
        listOf(700, 800, 900, 950, 1000, 710, 720, 730, 740, 750).forEachIndexed { i, s -> table.add(entry("P$i", s)) }
        assertEquals(10, table.add(entry("New", 705)))
        assertEquals("New", table.entries.last().name)

        val ties = ScaleScoreTable(temp.root.toPath().resolve("ties.json"))
        ties.add(entry("First", 500))
        ties.add(entry("Second", 500))
        assertEquals(listOf("First", "Second"), ties.entries.map { it.name })
    }

    @Test
    fun anUnreadableFileIsRenamedAndTheTableStartsEmpty() {
        Files.createDirectories(home)
        Files.writeString(file(), "not json")
        val table = ScaleScoreTable(file())
        assertTrue(table.damaged)
        assertEquals(emptyList<ScaleScoreEntry>(), table.entries)
        assertTrue(Files.exists(home.resolve("scale-scores.json.bad")))
        assertFalse(Files.exists(file()))
    }

    @Test
    fun scaleFlightsLeaveTheOtherTablesAlone() {
        val free = home.resolve("scores.json")
        val f2b = home.resolve("f2b-scores.json")
        ScoreTable(free).add(ScoreEntry("Ann", "Racer", 10, 1, 1, 5f, "2026-10-04T12:00:00Z"))
        Files.writeString(f2b, "f2b untouched")
        val before = Files.readString(free)

        ScaleScoreTable(file()).add(entry("Bob", 900))

        assertEquals(before, Files.readString(free))
        assertEquals("f2b untouched", Files.readString(f2b))
    }
}
