/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.attest

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.VerificationException
import net.nevinsky.abyssus.lib.updater.manifest.bindManifest
import net.nevinsky.abyssus.lib.updater.release.FetchException
import net.nevinsky.abyssus.lib.updater.release.HttpFetcher
import net.nevinsky.abyssus.lib.updater.release.Release
import net.nevinsky.abyssus.lib.updater.hashing.sha256Hex

/** Turns the bytes of a release's `manifest.json` into a manifest the game may trust, or throws [VerificationException]. */
fun interface ManifestAuthenticator {
    fun authenticate(release: Release, manifestBytes: ByteArray): Manifest
}

/**
 * Fetches the release attestations GitHub stores for a SHA-256 (`GET /repos/<repository>/attestations/sha256:<digest>`,
 * anonymous). A bundle comes inline or from a `bundle_url`; a `.sn` one is Snappy-compressed. What is returned is untrusted
 * bytes: [ReleaseAttestationVerifier] decides.
 */
class GitHubAttestations(
    private val fetcher: HttpFetcher,
    private val repository: String,
    private val apiBase: URI = URI.create("https://api.github.com"),
    private val maxBundles: Int = 10,
) {
    private val json = ObjectMapper()

    fun bundlesFor(sha256: String): List<ByteArray> {
        val list = json.readTree(fetcher.getBytes(URI.create("$apiBase/repos/$repository/attestations/sha256:$sha256?per_page=$maxBundles"), 8 * 1024 * 1024,
            mapOf("Accept" to "application/vnd.github+json")))
        return list.path("attestations").take(maxBundles).mapNotNull { entry ->
            val inline = entry.path("bundle")
            when {
                inline.isObject -> json.writeValueAsBytes(inline)
                entry.path("bundle_url").isTextual -> fetchBundle(URI.create(entry.path("bundle_url").asText()))
                else -> null
            }
        }
    }

    private fun fetchBundle(url: URI): ByteArray {
        val bytes = fetcher.getBytes(url, 4 * 1024 * 1024)
        return try {
            if (url.path.endsWith(".sn")) snappyDecode(bytes) else bytes
        } catch (e: IllegalArgumentException) {
            throw FetchException("The attestation bundle is not valid Snappy data: ${e.message}", e)
        }
    }
}

/**
 * Accepts a manifest only when GitHub's signed release attestation for the release's tag lists `manifest.json` with the
 * SHA-256 of these exact bytes. GitHub publishes that attestation for an immutable release a short while after publishing, so a
 * release without one yet is simply not applied; the next start asks again.
 */
class ReleaseManifestAuthenticator(
    private val attestations: GitHubAttestations,
    private val verifier: ReleaseAttestationVerifier,
    private val manifestAsset: String = "manifest.json",
    private val bind: (ByteArray) -> Manifest = ::bindManifest,
) : ManifestAuthenticator {
    override fun authenticate(release: Release, manifestBytes: ByteArray): Manifest {
        val digest = sha256Hex(manifestBytes)
        val bundles = try {
            attestations.bundlesFor(digest)
        } catch (e: FetchException) {
            throw VerificationException("Could not fetch the release attestation: ${e.message}", e)
        }
        if (bundles.isEmpty()) {
            throw VerificationException("GitHub has no release attestation for ${release.tag} yet (it appears shortly after an immutable release is published)")
        }
        val failures = mutableListOf<String>()
        for (bundle in bundles) {
            try {
                val attested = verifier.verify(bundle, release.tag)
                if (attested.assets[manifestAsset] == digest) return bind(manifestBytes)
                failures += "an attestation for ${release.tag} does not list this $manifestAsset"
            } catch (e: AttestationException) {
                failures += e.message ?: "an attestation did not verify"
            }
        }
        throw VerificationException("The manifest of ${release.tag} is not covered by GitHub's release attestation: ${failures.distinct().joinToString("; ")}")
    }
}
