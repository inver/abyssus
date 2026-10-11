/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.nevinsky.abyssus.app.game.controlline.score.TABLE_SIZE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** How much help an F2B flight gets: Pro none, Mid the next manoeuvre and a toast per mark, Noob that and the ideal track drawn ahead. */
enum class AssistLevel(val label: String, val key: String) {
    PRO("Pro", "pro"),
    MID("Mid", "mid"),
    NOOB("Noob", "noob");

    /** Whether the HUD names the next manoeuvre and the pause, and toasts each mark. */
    val prompted: Boolean get() = this != PRO

    /** Whether the ideal track of the next manoeuvre is drawn in the flight. */
    val guided: Boolean get() = this == NOOB
}

/** The version of `f2b-scores.json` this game reads and writes. */
const val F2B_SCORES_VERSION = 1

/**
 * One saved F2B flight: the [score] with two decimals, [flightTime] in seconds, [date] as ISO-8601 text and the 15 [marks].
 * [flight] names its recording in `f2b-flights/`, null for a flight saved without one.
 */
data class PatternScoreEntry(
    val name: String,
    val plane: String,
    val score: Double,
    val flightTime: Float,
    val date: String,
    val marks: List<Double>,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val flight: String? = null,
)

private data class PatternScoresFile(
    val version: Int = F2B_SCORES_VERSION,
    val boards: Map<String, List<PatternScoreEntry>> = emptyMap(),
)

/** Where the game keeps its F2B scores: `~/.abyssus-control-line/f2b-scores.json`. */
fun defaultPatternScoresFile(home: String = System.getProperty("user.home")): Path =
    Path.of(home, ".abyssus-control-line", "f2b-scores.json")

/**
 * The [TABLE_SIZE] best F2B flights of each assist level, best score first and earlier flights first among equal scores,
 * kept in [file] (`{version: 1, boards: {pro: [...], mid: [...], noob: [...]}}`; a missing board is empty). Writes go
 * through a temporary file and an atomic move. A file that cannot be read is renamed to `f2b-scores.json.bad` (replacing
 * an older one) and the boards start empty with [damaged] set. `scores.json` is never touched.
 */
class PatternScoreTable(
    private val file: Path,
    private val json: ObjectMapper = jacksonObjectMapper(),
    /** The recordings of the flights on the boards, in `f2b-flights` beside [file]. */
    val recordings: RecordingStore = RecordingStore(file.resolveSibling("f2b-flights")),
) {
    private var boards: Map<String, List<PatternScoreEntry>> = emptyMap()

    /** The file there was could not be read; the F2B tabs of the score table screen say so. */
    var damaged = false
        private set

    init {
        if (Files.exists(file)) {
            try {
                val read = json.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(file.toFile(), PatternScoresFile::class.java)
                require(read.version == F2B_SCORES_VERSION) { "unknown version ${read.version}" }
                boards = read.boards.mapValues { ranked(it.value) }
            } catch (_: Exception) {
                Files.move(file, file.resolveSibling(file.fileName.toString() + ".bad"), StandardCopyOption.REPLACE_EXISTING)
                damaged = true
            }
            // a table that could not be read names nothing, but its recordings may still be wanted
            if (!damaged) prune()
        }
    }

    private fun prune() {
        try {
            recordings.prune(boards.values.flatten().mapNotNull { it.flight }.toSet())
        } catch (_: java.io.IOException) {
            // leftovers are only disk use; the next start tries again
        }
    }

    /** The flights of [level]'s board, best first. */
    fun board(level: AssistLevel): List<PatternScoreEntry> = boards[level.key].orEmpty()

    /** Whether a flight scoring [score] would enter [level]'s board. */
    fun qualifies(level: AssistLevel, score: Double): Boolean {
        val board = board(level)
        return board.size < TABLE_SIZE || score > board.last().score
    }

    /**
     * Adds [entry] to [level]'s board when it qualifies and saves the file; returns its rank from 1, or null when it did not
     * enter. Its [recording] is saved first and named in the entry; when that fails the entry is kept without one. The
     * recording of a flight that leaves the board is deleted.
     */
    fun add(level: AssistLevel, entry: PatternScoreEntry, recording: FlightRecording? = null): Int? {
        if (!qualifies(level, entry.score)) return null
        val id = recording?.let {
            try {
                recordings.save(it)
            } catch (_: java.io.IOException) {
                null
            }
        }
        val named = if (id != null) entry.copy(flight = id) else entry
        val before = board(level)
        val next = ranked(before + named)
        boards = boards + (level.key to next)
        try {
            save()
        } catch (e: java.io.IOException) {
            id?.let(recordings::delete)
            throw e
        }
        for (left in (before + named).filter { gone -> next.none { it === gone } }) left.flight?.let(::forget)
        return next.indexOf(named).takeIf { it >= 0 }?.plus(1)
    }

    private fun forget(id: String) {
        try {
            recordings.delete(id)
        } catch (_: java.io.IOException) {
            // pruned at the next start
        }
    }

    private fun ranked(list: List<PatternScoreEntry>) = list.sortedByDescending { it.score }.take(TABLE_SIZE)

    private fun save() {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, "f2b-scores", ".tmp")
        try {
            Files.writeString(temp, json.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(PatternScoresFile(F2B_SCORES_VERSION, boards)).replace("\r\n", "\n") + "\n")
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
