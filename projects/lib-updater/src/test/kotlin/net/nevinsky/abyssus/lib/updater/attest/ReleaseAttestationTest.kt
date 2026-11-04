/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.attest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runs against a real release attestation GitHub published for `cli/cli` release v2.103.3 (fetched from the attestations API
 * and snappy-decoded); the project's own releases are verified the same way once they exist.
 */
class ReleaseAttestationTest {
    private val bundle = javaClass.getResourceAsStream("/cli-release-attestation.json")!!.readAllBytes()
    private val verifier = ReleaseAttestationVerifier(bundledTrustedRoot(), "cli/cli")
    private val mapper = ObjectMapper()

    private fun refused(verifier: ReleaseAttestationVerifier = this.verifier, bytes: ByteArray = bundle, tag: String = "v2.103.3", fragment: String) {
        try {
            verifier.verify(bytes, tag)
            fail("expected a refusal containing '$fragment'")
        } catch (e: AttestationException) {
            assertTrue("'${e.message}' lacks '$fragment'", e.message!!.contains(fragment))
        }
    }

    private fun edited(change: (ObjectNode) -> Unit): ByteArray = mapper.writeValueAsBytes((mapper.readTree(bundle) as ObjectNode).also(change))

    @Test
    fun `the real release attestation verifies and names the assets`() {
        val release = verifier.verify(bundle, "v2.103.3")
        assertEquals("cli/cli", release.repository)
        assertEquals("63837fb778d6a39f02be0ce76ff89f2bc8d74bdc", release.commit)
        assertEquals("f756e7859d90dbb5e85aae5194206c37da220bec9bac23d3d659078a8e766fdc", release.assets["gh_2.103.3_checksums.txt"])
        assertTrue(release.assets.size > 10)
    }

    @Test
    fun `another repository or another tag is refused`() {
        refused(ReleaseAttestationVerifier(bundledTrustedRoot(), "inver/abyssus"), fragment = "not 'inver/abyssus'")
        refused(tag = "v2.103.4", fragment = "not 'v2.103.4'")
    }

    @Test
    fun `a changed statement fails the signature`() {
        val payload = Base64.getDecoder().decode((mapper.readTree(bundle)).path("dsseEnvelope").path("payload").asText())
        val changed = String(payload).replace("v2.103.3", "v9.103.3").toByteArray()
        refused(bytes = edited { (it.path("dsseEnvelope") as ObjectNode).put("payload", Base64.getEncoder().encodeToString(changed)) }, tag = "v9.103.3", fragment = "signature")
    }

    @Test
    fun `a changed signature fails`() {
        val sig = Base64.getDecoder().decode(mapper.readTree(bundle).path("dsseEnvelope").path("signatures").path(0).path("sig").asText())
        sig[sig.size - 1] = (sig[sig.size - 1] + 1).toByte()
        // the time-stamp covers the original signature, so the changed one is refused there first
        refused(bytes = edited { (it.path("dsseEnvelope").path("signatures").path(0) as ObjectNode).put("sig", Base64.getEncoder().encodeToString(sig)) }, fragment = "time-stamp")
    }

    @Test
    fun `a bundle without a time-stamp is refused`() {
        refused(bytes = edited { (it.path("verificationMaterial") as ObjectNode).remove("timestampVerificationData") }, fragment = "no time-stamp")
    }

    @Test
    fun `a time-stamp from an authority outside the trusted root is refused`() {
        // the sigstore public-good root has no GitHub authority: nothing in it can vouch for this bundle
        val publicOnly = TrustedRoot(emptyList(), emptyList())
        refused(ReleaseAttestationVerifier(publicOnly, "cli/cli"), fragment = "time-stamp")
    }

    @Test
    fun `garbage is refused`() {
        refused(bytes = "not json".toByteArray(), fragment = "not valid JSON")
        refused(bytes = """{"mediaType":"text/plain"}""".toByteArray(), fragment = "not a Sigstore bundle")
    }

    @Test
    fun `snappy decodes literals and copies`() {
        // "abcabcabc": literal "abc" then a copy of length 6 at offset 3
        val data = byteArrayOf(9, 0x08, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), (0x01 or ((6 - 4) shl 2)).toByte(), 3)
        assertEquals("abcabcabc", String(snappyDecode(data)))
    }
}
