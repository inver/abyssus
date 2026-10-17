package net.nevinsky.abyssus.uitesting

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * A disposable copy of the Untitled fixture. [restore] puts the source bytes back after a scenario that edited the
 * project through the UI; nothing here writes through the IDE, and nothing edits the committed fixture.
 */
class FixtureProject(val source: Path, val dir: Path) {

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
        val wanted = tracked(source)
        for (rel in wanted) {
            val target = dir.resolve(rel)
            Files.createDirectories(target.parent)
            val bytes = Files.readAllBytes(source.resolve(rel))
            if (!Files.exists(target) || !Files.readAllBytes(target).contentEquals(bytes)) Files.write(target, bytes)
        }
        (tracked(dir) - wanted.toSet()).forEach { Files.delete(dir.resolve(it)) }
    }

    /** SHA-256 of every tracked file, keyed by relative path. */
    fun hashes(root: Path = dir): Map<String, String> = tracked(root).associate { rel ->
        rel.toString() to MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(rel)))
            .joinToString("") { "%02x".format(it) }
    }

    fun sourceHashes(): Map<String, String> = hashes(source)

    /** Paths whose bytes differ from the source (changed, added or removed). */
    fun differences(): Set<String> {
        val expected = sourceHashes()
        val actual = hashes()
        return (expected.keys + actual.keys).filter { expected[it] != actual[it] }.toSet()
    }
}
