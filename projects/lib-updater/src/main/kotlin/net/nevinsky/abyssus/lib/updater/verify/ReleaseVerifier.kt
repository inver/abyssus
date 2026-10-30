/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.verify

import java.nio.file.Files
import java.nio.file.Path
import net.nevinsky.abyssus.lib.updater.manifest.ManifestException
import net.nevinsky.abyssus.lib.updater.manifest.bindManifest
import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex

/** What is wrong with a release folder, one line each. */
class ReleaseVerificationException(val problems: List<String>) : Exception(problems.joinToString("; "))

/**
 * Checks a release folder before anything is published: the manifest binds, every part is present with the size and SHA-256
 * the manifest states, and, when `SHA256SUMS` is there (or [requireChecksums]), every file it lists matches. Signing is not
 * part of it: GitHub signs the release when it is published (see [PublishedReleaseVerifier]).
 */
class ReleaseVerifier(private val requireChecksums: Boolean = false) {
    fun verify(folder: Path) {
        val problems = mutableListOf<String>()
        val manifestFile = folder.resolve("manifest.json")
        if (!Files.isRegularFile(manifestFile)) throw ReleaseVerificationException(listOf("manifest.json is missing"))
        val manifestBytes = Files.readAllBytes(manifestFile)
        try {
            val manifest = bindManifest(manifestBytes)
            for (part in manifest.parts) {
                val file = folder.resolve(part.name)
                when {
                    !Files.isRegularFile(file) -> problems += "${part.name}: missing"
                    Files.size(file) != part.size -> problems += "${part.name}: size differs from the manifest"
                    sha256Hex(file) != part.sha256 -> problems += "${part.name}: checksum differs from the manifest"
                }
            }
            if (manifest.installerDigest.isBlank()) problems += "the manifest has no installerDigest"
        } catch (e: ManifestException) {
            problems += "manifest.json: ${e.message}"
        }
        checksums(folder, problems)
        if (problems.isNotEmpty()) throw ReleaseVerificationException(problems)
    }

    private fun checksums(folder: Path, problems: MutableList<String>) {
        val sums = folder.resolve("SHA256SUMS")
        if (!Files.isRegularFile(sums)) {
            if (requireChecksums) problems += "SHA256SUMS: missing"
            return
        }
        for (line in Files.readAllLines(sums).filter { it.isNotBlank() }) {
            val hash = line.substringBefore("  ")
            val name = line.substringAfter("  ", "")
            val file = folder.resolve(name)
            when {
                name.isEmpty() || name.contains('/') -> problems += "SHA256SUMS: bad line '$line'"
                !Files.isRegularFile(file) -> problems += "$name: listed in SHA256SUMS but missing"
                sha256Hex(file) != hash -> problems += "$name: checksum differs from SHA256SUMS"
            }
        }
    }
}
