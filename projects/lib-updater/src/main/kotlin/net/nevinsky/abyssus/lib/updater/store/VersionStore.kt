/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import net.nevinsky.abyssus.lib.updater.manifest.parseVersionOrNull

/** The game folder to run: a synced [version] under `versions/`, or `null` for the installed seed. */
data class GameFolder(val version: String?, val dir: Path)

/**
 * The player's update folder: `versions/<v>/` (each complete, marked by `COMPLETE`), `staging/<v>/` while one is being
 * built, and the one-line file `current`. A version is only visible once its folder has been renamed into `versions/`
 * with the marker written, and `current` changes only by an atomic move, so a cut at any point leaves a startable state.
 */
class VersionStore(
    private val root: Path,
    private val seed: Path,
    private val freeSpace: (Path) -> Long = { Files.getFileStore(it).usableSpace },
) {
    private val versions = root.resolve("versions")
    private val staging = root.resolve("staging")
    private val currentFile = root.resolve("current")

    fun versionDir(version: String): Path = versions.resolve(checked(version))

    /** The folder to start: `current` if complete and not in [bad]; else the newest complete good version; else the seed. */
    fun resolve(bad: Set<String>): GameFolder {
        val current = readCurrent() ?: return GameFolder(null, seed)
        if (current !in bad && isComplete(current)) return GameFolder(current, versionDir(current))
        val fallback = completeVersions().filter { it != current && it !in bad }
            .maxWithOrNull(compareBy { parseVersionOrNull(it)!! })
        return if (fallback != null) GameFolder(fallback, versionDir(fallback)) else GameFolder(null, seed)
    }

    /** The version named by `current` when it is well formed, else `null`. */
    fun readCurrent(): String? = try {
        if (Files.isRegularFile(currentFile)) Files.readString(currentFile).trim().takeIf { parseVersionOrNull(it) != null } else null
    } catch (e: IOException) {
        null
    }

    /** Removes staging folders left by an interrupted download and version folders without the marker. */
    fun cleanUp() {
        deleteTree(staging)
        if (Files.isDirectory(versions)) Files.list(versions).use { it.toList() }.forEach {
            if (!Files.exists(it.resolve(COMPLETE))) deleteTree(it)
        }
    }

    /** An empty `staging/<version>`; throws when the disk has less than [requiredBytes] free. */
    fun beginStaging(version: String, requiredBytes: Long): Path {
        checked(version)
        Files.createDirectories(root)
        val free = freeSpace(root)
        if (free < requiredBytes) throw IOException("Not enough free space for $version: need $requiredBytes bytes, have $free")
        val dir = staging.resolve(version)
        deleteTree(dir)
        Files.createDirectories(dir)
        return dir
    }

    /** Throws the staging folder of [version] away. */
    fun abandonStaging(version: String) = deleteTree(staging.resolve(checked(version)))

    /** Makes the staged [version] the current one: marker, rename into `versions/`, atomic `current`, then prune. */
    fun commit(version: String) {
        checked(version)
        val staged = staging.resolve(version)
        Files.writeString(staged.resolve(COMPLETE), version)
        Files.createDirectories(versions)
        val target = versions.resolve(version)
        deleteTree(target)
        moveAtomically(staged, target)
        val previous = readCurrent()
        writeAtomically(currentFile, version.toByteArray())
        deleteTree(staging)
        completeVersions().filter { it != version && it != previous }.forEach { deleteTree(versions.resolve(it)) }
    }

    private fun isComplete(version: String) = Files.exists(versions.resolve(version).resolve(COMPLETE))

    private fun completeVersions(): List<String> =
        if (!Files.isDirectory(versions)) emptyList()
        else Files.list(versions).use { it.toList() }.map { it.fileName.toString() }
            .filter { parseVersionOrNull(it) != null && isComplete(it) }

    private fun checked(version: String): String {
        require(parseVersionOrNull(version) != null) { "Not a numeric dotted version: '$version'" }
        return version
    }
}

private const val COMPLETE = "COMPLETE"
