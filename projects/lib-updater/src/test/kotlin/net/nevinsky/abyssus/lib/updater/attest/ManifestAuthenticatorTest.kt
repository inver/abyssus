/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.attest

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.VerificationException
import net.nevinsky.abyssus.lib.updater.manifest.parseVersion
import net.nevinsky.abyssus.lib.updater.release.HttpFetcher
import net.nevinsky.abyssus.lib.updater.release.Release
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The attestation lookup and the manifest check, with GitHub's real `cli/cli` v2.103.3 attestation served from a loopback server. */
class ManifestAuthenticatorTest {
    private lateinit var server: HttpServer
    private val bundle = javaClass.getResourceAsStream("/cli-release-attestation.json")!!.readAllBytes()
    private val compressed = javaClass.getResourceAsStream("/cli-release-attestation.json.sn")!!.readAllBytes()
    private val checksums = javaClass.getResourceAsStream("/cli-checksums.txt")!!.readAllBytes()
    private val asked = mutableListOf<String>()
    private var answer: (String) -> String = { "{\"attestations\": []}" }

    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val release = Release("v2.103.3", parseVersion("2.103.3"), "", emptyList())
    private val fakeManifest = Manifest(1, "control-line", "2.103.3", "Main", "d")

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos") { e ->
            asked += e.requestURI.path
            val bytes = answer(e.requestURI.path).toByteArray()
            e.sendResponseHeaders(200, bytes.size.toLong())
            e.responseBody.use { it.write(bytes) }
        }
        server.createContext("/bundle.json.sn") { e ->
            e.sendResponseHeaders(200, compressed.size.toLong())
            e.responseBody.use { it.write(compressed) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun authenticator(): ReleaseManifestAuthenticator {
        val fetcher = HttpFetcher({ it.host == "127.0.0.1" }, Duration.ofSeconds(5), "test")
        return ReleaseManifestAuthenticator(
            GitHubAttestations(fetcher, "cli/cli", URI.create(base)), ReleaseAttestationVerifier(bundledTrustedRoot(), "cli/cli"),
            manifestAsset = "gh_2.103.3_checksums.txt", bind = { fakeManifest },
        )
    }

    private fun refused(fragment: String, bytes: ByteArray = checksums) {
        try {
            authenticator().authenticate(release, bytes)
            fail("expected a refusal containing '$fragment'")
        } catch (e: VerificationException) {
            assertTrue("'${e.message}' lacks '$fragment'", e.message!!.contains(fragment))
        }
    }

    @Test
    fun `an inline bundle that lists the manifest authenticates it`() {
        answer = { """{"attestations": [{"bundle": ${String(bundle)}, "bundle_url": null}]}""" }
        assertEquals(fakeManifest, authenticator().authenticate(release, checksums))
        assertEquals(listOf("/repos/cli/cli/attestations/sha256:f756e7859d90dbb5e85aae5194206c37da220bec9bac23d3d659078a8e766fdc"), asked)
    }

    @Test
    fun `a snappy bundle_url is fetched and decoded`() {
        answer = { """{"attestations": [{"bundle": null, "bundle_url": "$base/bundle.json.sn?se=x&sig=y"}]}""" }
        assertEquals(fakeManifest, authenticator().authenticate(release, checksums))
    }

    @Test
    fun `no attestation yet is a refusal that says so`() = refused("no release attestation")

    @Test
    fun `an attestation that does not list these bytes is refused`() {
        answer = { """{"attestations": [{"bundle": ${String(bundle)}}]}""" }
        refused("does not list", bytes = checksums + 1)
    }

    @Test
    fun `an attestation for another tag is refused`() {
        answer = { """{"attestations": [{"bundle": ${String(bundle)}}]}""" }
        try {
            authenticator().authenticate(Release("v9.9.9", parseVersion("9.9.9"), "", emptyList()), checksums)
            fail("expected a refusal")
        } catch (e: VerificationException) {
            assertTrue(e.message!!, e.message!!.contains("not 'v9.9.9'"))
        }
    }

    @Test
    fun `garbage from the lookup is a refusal not a crash`() {
        answer = { """{"attestations": [{"bundle": {"mediaType": "x"}}]}""" }
        refused("not a Sigstore bundle")
    }
}
