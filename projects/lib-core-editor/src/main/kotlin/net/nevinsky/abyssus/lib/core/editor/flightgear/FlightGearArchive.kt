/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.flightgear

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** The largest archive entry read, in bytes. */
const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

/** The most entries an archive may have. */
const val MAX_ENTRIES = 4096

/** An archive that cannot be imported, with the reason. */
class FlightGearArchiveException(message: String) : IOException(message)

/**
 * One aircraft of an archive: its folder inside the archive ([folder], ending in `/`), the set file, and what the set
 * file says. [modelPath] is the model's path inside the archive, or null when the set file names none.
 */
data class FlightGearAircraft(
    val id: String,
    val folder: String,
    val setFile: String,
    val description: String?,
    val authors: String?,
    val license: String?,
    val modelPath: String?,
)

/**
 * A FlightGear aircraft archive (`.zip`), read lazily and never extracted. Entries with absolute or `..` paths, more
 * than [MAX_ENTRIES] entries and entries over [MAX_ENTRY_BYTES] make the archive unreadable. FlightGear paths
 * (`Aircraft/<dir>/...`) resolve against the folder that holds the aircraft folders, wherever that is in the archive.
 */
class FlightGearArchive(val file: File, private val xml: FlightGearXml = FlightGearXml()) : Closeable {
    private val zip = try {
        ZipFile(file)
    } catch (e: IOException) {
        throw FlightGearArchiveException("${file.name} is not a readable zip archive: ${e.message}")
    }
    private val entries: Map<String, ZipEntry>

    init {
        try {
            val all = zip.entries().toList()
            if (all.size > MAX_ENTRIES) throw FlightGearArchiveException("${file.name} has more than $MAX_ENTRIES entries")
            entries = all.filter { !it.isDirectory }.associateBy { entry ->
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.split('/').any { it == ".." } || Regex("^[A-Za-z]:").containsMatchIn(name)) {
                    throw FlightGearArchiveException("${file.name} has an entry outside the archive: ${entry.name}")
                }
                name
            }
        } catch (e: FlightGearArchiveException) {
            zip.close()
            throw e
        }
    }

    /** Every file path in the archive. */
    val paths: Set<String> get() = entries.keys

    /** The SHA-256 of the archive file, in hex. */
    fun sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The bytes of [path], or null when the archive has no such file. */
    fun read(path: String): ByteArray? {
        val entry = entries[path] ?: return null
        if (entry.size > MAX_ENTRY_BYTES) throw FlightGearArchiveException("$path is larger than $MAX_ENTRY_BYTES bytes")
        zip.getInputStream(entry).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1 shl 16)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_ENTRY_BYTES) throw FlightGearArchiveException("$path is larger than $MAX_ENTRY_BYTES bytes")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** The aircraft in the archive: every `*-set.xml` directly inside a folder, in path order. */
    fun aircraft(): List<FlightGearAircraft> = entries.keys.filter { it.endsWith("-set.xml") }.sorted().mapNotNull { set ->
        val folder = set.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.plus("/") ?: return@mapNotNull null
        val root = xml.parse(read(set) ?: return@mapNotNull null, set)
        val sim = root.child("sim")
        val model = sim?.child("model")?.text("path")
        FlightGearAircraft(
            id = folder.trimEnd('/').substringAfterLast('/'),
            folder = folder,
            setFile = set,
            description = sim?.text("description"),
            authors = sim?.text("author"),
            license = sim?.text("license") ?: sim?.text("licence"),
            modelPath = model?.let { resolve(folder, it) },
        )
    }

    /**
     * The archive path of a FlightGear [path] written in a file of [folder]: `Aircraft/<dir>/...` resolves next to the
     * aircraft folder, anything else relative to [folder]. Null when it leaves the archive.
     */
    fun resolve(folder: String, path: String): String? {
        val clean = path.trim().replace('\\', '/')
        if (clean.isEmpty()) return null
        val joined = if (clean.startsWith("Aircraft/")) {
            val rest = clean.removePrefix("Aircraft/")
            aircraftRoot(rest.substringBefore('/')) + rest
        } else {
            folder + clean
        }
        return normalise(joined)
    }

    /** Files in [folder] (at any depth) whose name equals [name], ignoring case, nearest first. */
    fun findByName(folder: String, name: String): List<String> = entries.keys
        .filter { it.startsWith(folder) && it.substringAfterLast('/').equals(name, ignoreCase = true) }
        .sortedBy { it.count { c -> c == '/' } }

    /** Licence files (`COPYING*`, `LICENSE*`, `LICENCE*`) directly in [folder]. */
    fun licenseFiles(folder: String): List<String> = entries.keys.filter {
        it.startsWith(folder) && !it.removePrefix(folder).contains('/') &&
            it.substringAfterLast('/').uppercase().let { n -> n.startsWith("COPYING") || n.startsWith("LICENSE") || n.startsWith("LICENCE") }
    }.sorted()

    /**
     * What `Aircraft/` stands for in a path to aircraft [dir]: the shortest archive prefix that holds a `dir/` folder,
     * or the archive's root when none does.
     */
    private fun aircraftRoot(dir: String): String =
        entries.keys.mapNotNull { key ->
            var at = key.indexOf("$dir/")
            while (at > 0 && key[at - 1] != '/') at = key.indexOf("$dir/", at + 1)
            if (at >= 0) key.substring(0, at) else null
        }.minByOrNull { it.length } ?: ""

    private fun normalise(path: String): String? {
        val parts = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    override fun close() = zip.close()
}
