package net.nevinsky.abyssus.uitesting

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * A disposable copy of a fixture project (Untitled by default). [restore] puts the source bytes back after a scenario
 * that edited the project through the UI, including removing folders the scenario created; [switchTo] makes the same
 * copy another committed fixture profile (Custom, Physics, Foliage, ...). Nothing here writes through the IDE, and
 * nothing edits a committed fixture: [untouchedProfiles] proves it for every profile that was used.
 */
class FixtureProject(val source: Path, val dir: Path) {
    private var active: Path = source
    private val usedProfiles = LinkedHashMap<Path, Map<String, String>>()

    init { usedProfiles[source] = hashes(source) }

    /** The committed fixture the copy currently stands for. */
    val profile: Path get() = active

    /** Fixture files, relative to the project root. IDE state (`.idea`) and OS litter are not part of the project. */
    private fun tracked(root: Path): List<Path> = Files.walk(root).use { walk ->
        walk.filter { it.isRegularFile() && !isIdeState(it.relativeTo(root)) }.map { it.relativeTo(root) }.sorted().toList()
    }

    private fun isIdeState(rel: Path) = rel.startsWith(".idea") || rel.name == ".DS_Store"

    fun resolve(relative: String): Path = dir.resolve(relative)

    fun read(relative: String): ByteArray = Files.readAllBytes(resolve(relative))

    /** Copies the fixture into [dir], replacing what is there, without the IDE workspace state. */
    fun create() {
        dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        restore()
    }

    /** Rewrites every tracked file from the source and removes tracked files the source does not have. */
    fun restore() {
        val wanted = tracked(active)
        for (rel in wanted) {
            val target = dir.resolve(rel)
            Files.createDirectories(target.parent)
            val bytes = Files.readAllBytes(active.resolve(rel))
            if (!Files.exists(target) || !Files.readAllBytes(target).contentEquals(bytes)) Files.write(target, bytes)
        }
        (tracked(dir) - wanted.toSet()).forEach { Files.delete(dir.resolve(it)) }
        removeEmptyFolders()
    }

    /**
     * Makes the copy the fixture [profile] (another committed project folder): the next [restore] state. A folder a
     * scenario or the previous profile left empty would collide with names the next scenario creates, so those go too.
     */
    fun switchTo(profile: Path) {
        usedProfiles.getOrPut(profile) { hashes(profile) }
        active = profile
        restore()
    }

    /** Profiles used so far whose committed files differ from when they were first read; empty when all are intact. */
    fun untouchedProfiles(): List<Path> = usedProfiles.filter { (path, before) -> hashes(path) != before }.keys.toList()

    /** Deletes folders without any file below them, deepest first, except the root and IDE state. */
    private fun removeEmptyFolders() {
        val folders = Files.walk(dir).use { walk ->
            walk.filter { Files.isDirectory(it) && it != dir && !isIdeState(it.relativeTo(dir)) }.toList()
        }
        folders.sortedByDescending { it.nameCount }.forEach { folder ->
            if (Files.exists(folder) && Files.list(folder).use { it.findAny().isEmpty }) Files.delete(folder)
        }
    }

    /** SHA-256 of every tracked file, keyed by relative path. */
    fun hashes(root: Path = dir): Map<String, String> = tracked(root).associate { rel ->
        rel.toString() to MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(rel)))
            .joinToString("") { "%02x".format(it) }
    }

    fun sourceHashes(): Map<String, String> = hashes(active)

    /** Paths whose bytes differ from the source (changed, added or removed). */
    fun differences(): Set<String> {
        val expected = hashes(active)
        val actual = hashes()
        return (expected.keys + actual.keys).filter { expected[it] != actual[it] }.toSet()
    }
}
