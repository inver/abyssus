/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.scale

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.nevinsky.abyssus.app.game.controlline.io.AtomicJsonStore
import net.nevinsky.abyssus.app.game.controlline.score.TABLE_SIZE
import java.nio.file.Files
import java.nio.file.Path

/** The version of `scale-scores.json` this game reads and writes. */
const val SCALE_SCORES_VERSION = 1

/** One saved Scale flight: [flightTime] in seconds, [date] as ISO-8601 text, the three marks beside the [score]. */
data class ScaleScoreEntry(
    val name: String,
    val plane: String,
    val prototype: String,
    val score: Int,
    val speed: Int,
    val level: Int,
    val landing: Int,
    val laps: Int,
    val flightTime: Float,
    val date: String,
)

private data class ScaleScoresFile(val version: Int = SCALE_SCORES_VERSION, val entries: List<ScaleScoreEntry> = emptyList())

/** Where the game keeps its Scale scores: `~/.abyssus-control-line/scale-scores.json`. */
fun defaultScaleScoresFile(home: String = System.getProperty("user.home")): Path =
    Path.of(home, ".abyssus-control-line", "scale-scores.json")

/**
 * The [TABLE_SIZE] best Scale flights, best score first and earlier flights first among equal scores, kept in [file]
 * (`{version: 1, entries: [...]}`), the way [net.nevinsky.abyssus.app.game.controlline.score.ScoreTable] keeps Free flights.
 * A file that cannot be read is renamed to `scale-scores.json.bad` and the table starts empty with [damaged] set.
 */
class ScaleScoreTable(private val file: Path, private val json: ObjectMapper = jacksonObjectMapper()) {
    private val store = AtomicJsonStore()

    var entries: List<ScaleScoreEntry> = emptyList()
        private set

    /** The file there was could not be read; the score table screen says so. */
    var damaged = false
        private set

    init {
        entries = store.read(file, {
            val read = json.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .readValue(it.toFile(), ScaleScoresFile::class.java)
            require(read.version == SCALE_SCORES_VERSION) { "unknown version ${read.version}" }
            ranked(read.entries)
        }, { damaged = true }).orEmpty()
    }

    /** Whether a flight scoring [score] would enter the table. */
    fun qualifies(score: Int): Boolean = entries.size < TABLE_SIZE || score > entries.last().score

    /** Adds [entry] when it qualifies and saves the table; returns its rank from 1, or null when it did not enter. */
    fun add(entry: ScaleScoreEntry): Int? {
        if (!qualifies(entry.score)) return null
        val next = ranked(entries + entry)
        entries = next
        save()
        return next.indexOf(entry).takeIf { it >= 0 }?.plus(1)
    }

    private fun ranked(list: List<ScaleScoreEntry>) = list.sortedByDescending { it.score }.take(TABLE_SIZE)

    private fun save() {
        store.save(file, "scale-scores") { temp ->
            Files.writeString(temp, json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(ScaleScoresFile(SCALE_SCORES_VERSION, entries)).replace("\r\n", "\n") + "\n")
        }
    }
}
