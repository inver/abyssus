/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.verify

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.system.exitProcess
import net.nevinsky.abyssus.lib.updater.attest.AttestationException
import net.nevinsky.abyssus.lib.updater.attest.GitHubAttestations
import net.nevinsky.abyssus.lib.updater.attest.ReleaseAttestationVerifier
import net.nevinsky.abyssus.lib.updater.attest.bundledTrustedRoot
import net.nevinsky.abyssus.lib.updater.release.FetchException
import net.nevinsky.abyssus.lib.updater.release.HttpFetcher
import net.nevinsky.abyssus.lib.updater.release.githubHostPolicy
import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex

/**
 * After a release is published: waits for GitHub's release attestation of [tag] and checks that it verifies against the
 * bundled trusted root and lists every file of the local release folder with the same SHA-256, so what was uploaded is
 * what GitHub signed. Returns the problems found when the wait ends (empty when everything matches).
 */
class PublishedReleaseVerifier(private val attestations: GitHubAttestations, private val verifier: ReleaseAttestationVerifier) {
    fun verify(folder: Path, tag: String, wait: Duration, pause: Duration = Duration.ofSeconds(15)): List<String> {
        val manifest = folder.resolve("manifest.json")
        val digest = sha256Hex(manifest)
        val deadline = System.nanoTime() + wait.toNanos()
        var problems = listOf("no attestation yet")
        while (true) {
            problems = check(folder, tag, digest)
            if (problems.isEmpty() || System.nanoTime() >= deadline) return problems
            Thread.sleep(pause.toMillis())
        }
    }

    private fun check(folder: Path, tag: String, digest: String): List<String> {
        val bundles = try {
            attestations.bundlesFor(digest)
        } catch (e: FetchException) {
            return listOf("could not fetch the attestation: ${e.message}")
        }
        if (bundles.isEmpty()) return listOf("GitHub has no attestation for manifest.json yet")
        var last = listOf("no attestation verified")
        for (bundle in bundles) {
            try {
                val attested = verifier.verify(bundle, tag)
                val files = Files.list(folder).use { it.filter { f -> Files.isRegularFile(f) }.toList() }
                val problems = files.mapNotNull { f ->
                    val name = f.fileName.toString()
                    when (attested.assets[name]) {
                        null -> "$name: not listed in GitHub's release attestation"
                        sha256Hex(f) -> null
                        else -> "$name: differs from GitHub's release attestation"
                    }
                }
                if (problems.isEmpty()) return emptyList()
                last = problems
            } catch (e: AttestationException) {
                last = listOf(e.message ?: "an attestation did not verify")
            }
        }
        return last
    }
}

/** `verifyPublishedRelease`: `<owner/repo> <tag> <release folder> [wait minutes, default 10]`; exits 1 with the problems. */
fun main(args: Array<String>) {
    if (args.size < 3) {
        System.err.println("usage: <owner/repo> <tag> <release folder> [wait minutes]")
        exitProcess(2)
    }
    val (repository, tag, folder) = args
    val fetcher = HttpFetcher(githubHostPolicy(), Duration.ofSeconds(30), "control-line-release-check")
    val problems = PublishedReleaseVerifier(GitHubAttestations(fetcher, repository), ReleaseAttestationVerifier(bundledTrustedRoot(), repository))
        .verify(Path.of(folder), tag, Duration.ofMinutes(args.getOrNull(3)?.toLong() ?: 10))
    if (problems.isNotEmpty()) {
        problems.forEach { System.err.println("FAIL $it") }
        exitProcess(1)
    }
    println("GitHub's release attestation of $tag covers every file of $folder")
}
