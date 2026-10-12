/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.pattern

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Where the game keeps its saved flights: `~/.abyssus-control-line/f2b-flights`. */
fun defaultRecordingsFolder(home: String = System.getProperty("user.home")): Path =
    Path.of(home, ".abyssus-control-line", "f2b-flights")

/**
 * The saved flights of the F2B boards (design decision 2): one `<id>.json.gz` per flight in [folder], which is made on the
 * first save. Writes go through a temporary file and an atomic move. A file that cannot be read is renamed with `.bad`
 * added. The recording's JSON is `version: 1`, gzipped.
 */
class RecordingStore(private val folder: Path, private val json: ObjectMapper = jacksonObjectMapper()) {
    /** Saves [recording] under a new id and returns the id. */
    fun save(recording: FlightRecording): String {
        Files.createDirectories(folder)
        val id = UUID.randomUUID().toString()
        val temp = Files.createTempFile(folder, "flight", TEMP)
        try {
            GZIPOutputStream(Files.newOutputStream(temp)).use { json.writeValue(it, recording) }
            Files.move(temp, fileOf(id), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        return id
    }

    /** The recording [id], or null when it is missing or cannot be read (then its file is renamed to `.bad`). */
    fun load(id: String): FlightRecording? {
        if (!valid(id)) return null
        val file = fileOf(id)
        if (!Files.exists(file)) return null
        return try {
            val read = GZIPInputStream(Files.newInputStream(file)).use { json.readValue(it, FlightRecording::class.java) }
            require(read.version == RECORDING_VERSION) { "unknown version ${read.version}" }
            require(read.samples.size % SAMPLE_STRIDE == 0) { "samples are not whole" }
            require(read.judgements.all { it.index in SCHEDULE.indices && it.ideal.size % 3 == 0 }) { "a judgement is not whole" }
            read
        } catch (_: Exception) {
            Files.move(file, file.resolveSibling(file.fileName.toString() + BAD), StandardCopyOption.REPLACE_EXISTING)
            null
        }
    }

    /** Deletes the recording [id], if there is one. */
    fun delete(id: String) {
        Files.deleteIfExists(fileOf(id))
    }

    /** Deletes the recordings that are not in [named], and any temporary files left by an interrupted save. */
    fun prune(named: Set<String>) {
        if (!Files.isDirectory(folder)) return
        Files.newDirectoryStream(folder).use { files ->
            for (file in files) {
                val name = file.fileName.toString()
                val stale = name.endsWith(TEMP) || (name.endsWith(SUFFIX) && name.removeSuffix(SUFFIX) !in named)
                if (stale) Files.deleteIfExists(file)
            }
        }
    }

    /** The ids of the recordings on disk. */
    fun ids(): Set<String> {
        if (!Files.isDirectory(folder)) return emptySet()
        return Files.newDirectoryStream(folder, "*$SUFFIX").use { files -> files.map { it.fileName.toString().removeSuffix(SUFFIX) }.toSet() }
    }

    /** An id from a score entry is a file name, never a path. */
    private fun valid(id: String) = id.isNotEmpty() && id.none { it == '/' || it == '\\' } && id != "." && id != ".."

    private fun fileOf(id: String): Path {
        require(valid(id)) { "bad recording id" }
        return folder.resolve(id + SUFFIX)
    }

    private companion object {
        const val SUFFIX = ".json.gz"
        const val BAD = ".bad"
        const val TEMP = ".tmp"
    }
}
