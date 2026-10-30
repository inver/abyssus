/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.sync

import net.nevinsky.abyssus.lib.updater.hashing.hex
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import net.nevinsky.abyssus.lib.updater.manifest.ManifestFile

/** A part that must not be extracted; [file] names the entry or manifest file at fault when there is one. */
class UnzipException(message: String, val file: String? = null) : Exception(message)

/**
 * Extracts one part under a folder, accepting only the files the signed manifest lists for it. Every entry's path is
 * checked (no `..`, no absolute or drive path, no backslash), its size and SHA-256 must match the manifest, and every
 * listed file must appear.
 */
class SafeUnzip {
    fun extract(zip: InputStream, expected: List<ManifestFile>, target: Path) {
        val wanted = expected.associateBy { it.path }
        val seen = mutableSetOf<String>()
        val root = target.toAbsolutePath().normalize()
        ZipInputStream(zip).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                val name = entry.name
                checkName(name)
                if (entry.isDirectory) continue
                val listed = wanted[name] ?: throw UnzipException("'$name' is not listed in the manifest", name)
                if (!seen.add(name)) throw UnzipException("'$name' appears twice", name)
                val out = root.resolve(name).normalize()
                if (!out.startsWith(root)) throw UnzipException("'$name' leaves the target folder", name)
                Files.createDirectories(out.parent)
                writeChecked(stream, listed, out)
            }
        }
        (wanted.keys - seen).firstOrNull()?.let { throw UnzipException("'$it' is missing from the part", it) }
    }

    private fun checkName(name: String) {
        val bad = name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains('\u0000') ||
            Regex("^[A-Za-z]:").containsMatchIn(name) || name.split('/').any { it == ".." }
        if (bad) throw UnzipException("Unsafe entry name '$name'", name)
    }

    private fun writeChecked(input: InputStream, listed: ManifestFile, out: Path) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        Files.newOutputStream(out).use { sink ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                if (size > listed.size) throw UnzipException("'${listed.path}' is larger than the manifest says", listed.path)
                digest.update(buffer, 0, n)
                sink.write(buffer, 0, n)
            }
        }
        if (size != listed.size) throw UnzipException("'${listed.path}' is smaller than the manifest says", listed.path)
        if (hex(digest.digest()) != listed.sha256) throw UnzipException("'${listed.path}' does not match its checksum", listed.path)
    }
}
