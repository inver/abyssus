/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.score

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** How many flights the table keeps. */
const val TABLE_SIZE = 10

/** The version of `scores.json` this game reads and writes. */
const val SCORES_VERSION = 1

/** One saved flight: [flightTime] in seconds, [date] as ISO-8601 text. */
data class ScoreEntry(
    val name: String,
    val plane: String,
    val score: Int,
    val laps: Int,
    val bestCombo: Int,
    val flightTime: Float,
    val date: String,
)

private data class ScoresFile(val version: Int = SCORES_VERSION, val entries: List<ScoreEntry> = emptyList())

/** Where the game keeps its scores: `~/.abyssus-control-line/scores.json`. */
fun defaultScoresFile(home: String = System.getProperty("user.home")): Path =
    Path.of(home, ".abyssus-control-line", "scores.json")

/**
 * The [TABLE_SIZE] best flights, best score first and earlier flights first among equal scores, kept in [file]
 * (`{version: 1, entries: [...]}`). Writes go through a temporary file and an atomic move. A file that cannot be read
 * is renamed to `scores.json.bad` (replacing an older one) and the table starts empty with [damaged] set.
 */
class ScoreTable(private val file: Path, private val json: ObjectMapper = jacksonObjectMapper()) {
    var entries: List<ScoreEntry> = emptyList()
        private set

    /** The file there was could not be read; the score table screen says so. */
    var damaged = false
        private set

    init {
        if (Files.exists(file)) {
            try {
                val read = json.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                    .readValue(file.toFile(), ScoresFile::class.java)
                require(read.version == SCORES_VERSION) { "unknown version ${read.version}" }
                entries = ranked(read.entries)
            } catch (_: Exception) {
                Files.move(file, file.resolveSibling(file.fileName.toString() + ".bad"), StandardCopyOption.REPLACE_EXISTING)
                damaged = true
            }
        }
    }

    /** Whether a flight scoring [score] would enter the table. */
    fun qualifies(score: Int): Boolean = entries.size < TABLE_SIZE || score > entries.last().score

    /** Adds [entry] when it qualifies and saves the table; returns its rank from 1, or null when it did not enter. */
    fun add(entry: ScoreEntry): Int? {
        if (!qualifies(entry.score)) return null
        val next = ranked(entries + entry)
        entries = next
        save()
        return next.indexOf(entry).takeIf { it >= 0 }?.plus(1)
    }

    private fun ranked(list: List<ScoreEntry>) = list.sortedByDescending { it.score }.take(TABLE_SIZE)

    private fun save() {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, "scores", ".tmp")
        try {
            Files.writeString(temp, json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(ScoresFile(SCORES_VERSION, entries)).replace("\r\n", "\n") + "\n")
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
