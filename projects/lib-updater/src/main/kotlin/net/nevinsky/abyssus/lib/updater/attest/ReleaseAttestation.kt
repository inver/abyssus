/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.attest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertPathBuilder
import java.security.cert.CertStore
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.Date
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.tsp.TimeStampResponse
import org.bouncycastle.tsp.TimeStampToken

/** What a release attestation proves: the release [tag] of [repository] at [commit], and the SHA-256 of each of its assets. */
data class AttestedRelease(val repository: String, val tag: String, val commit: String?, val assets: Map<String, String>)

/** The attestation is missing, malformed or does not verify; the message says which check failed. */
class AttestationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The identity GitHub's release attestation is signed under (the certificate's URI SAN). */
const val GITHUB_RELEASES_SIGNER = "https://dotcom.releases.github.com"

private const val SHA256_OID = "2.16.840.1.101.3.4.2.1"
private const val CODE_SIGNING_OID = "1.3.6.1.5.5.7.3.3"
private const val SAN_URI = 6

/**
 * Verifies GitHub's signed release attestation (the "immutable release" attestation, a Sigstore bundle with a DSSE envelope
 * and RFC 3161 timestamps) against [root]. A bundle passes only when all of these hold:
 *
 * 1. a time-stamp token verifies against a time-stamping authority of the root, chains to it at the time it states, and its
 *    message imprint is the SHA-256 of the envelope's signature (so the signature existed at that time);
 * 2. the signing certificate chains to a certificate authority of the root, valid at that time, is not a CA, carries the
 *    code-signing usage and names [GITHUB_RELEASES_SIGNER];
 * 3. the envelope's ECDSA signature over its PAE verifies with the certificate's key;
 * 4. the in-toto statement is a release statement for [repository] and the expected tag.
 */
class ReleaseAttestationVerifier(private val root: TrustedRoot, private val repository: String) {
    private val json = ObjectMapper()

    fun verify(bundleJson: ByteArray, expectedTag: String): AttestedRelease {
        val bundle = try {
            json.readTree(bundleJson)
        } catch (e: Exception) {
            throw AttestationException("The attestation is not valid JSON", e)
        }
        if (!bundle.path("mediaType").asText("").startsWith("application/vnd.dev.sigstore.bundle")) {
            throw AttestationException("The attestation is not a Sigstore bundle")
        }
        val envelope = bundle.path("dsseEnvelope")
        val payload = b64(envelope.path("payload"), "the envelope payload")
        val payloadType = envelope.path("payloadType").asText("")
        val signature = b64(envelope.path("signatures").path(0).path("sig"), "the envelope signature")
        val leaf = leafCertificate(bundle)

        val signedAt = verifiedTime(bundle.path("verificationMaterial").path("timestampVerificationData"), signature)
        checkSigner(leaf)
        checkChain(leaf, signedAt)
        val pae = "DSSEv1 ${payloadType.toByteArray().size} $payloadType ${payload.size} ".toByteArray() + payload
        val ok = try {
            Signature.getInstance("SHA256withECDSA").run { initVerify(leaf.publicKey); update(pae); verify(signature) }
        } catch (e: java.security.GeneralSecurityException) {
            false
        }
        if (!ok) throw AttestationException("The envelope signature does not verify with the signing certificate")
        if (payloadType != "application/vnd.in-toto+json") throw AttestationException("Unexpected payload type '$payloadType'")
        return statement(payload, expectedTag)
    }

    private fun leafCertificate(bundle: JsonNode): X509Certificate {
        val material = bundle.path("verificationMaterial")
        val raw = material.path("certificate").path("rawBytes").takeIf { !it.isMissingNode }
            ?: material.path("x509CertificateChain").path("certificates").path(0).path("rawBytes")
        return try {
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(b64(raw, "the signing certificate"))) as X509Certificate
        } catch (e: java.security.GeneralSecurityException) {
            throw AttestationException("The signing certificate is unreadable", e)
        }
    }

    /** The time stated by a time-stamp token that verifies and covers [signature]; at least one is required. */
    private fun verifiedTime(data: JsonNode, signature: ByteArray): Instant {
        val tokens = data.path("rfc3161Timestamps")
        if (!tokens.isArray || tokens.isEmpty) throw AttestationException("The attestation has no time-stamp")
        var failure = "no time-stamp verified"
        for (entry in tokens) {
            try {
                return checkTimestamp(b64(entry.path("signedTimestamp"), "a time-stamp"), signature)
            } catch (e: AttestationException) {
                failure = e.message ?: failure
            } catch (e: Exception) {
                failure = "a time-stamp is malformed (${e.javaClass.simpleName})"
            }
        }
        throw AttestationException("The attestation's time-stamp does not verify: $failure")
    }

    private fun checkTimestamp(bytes: ByteArray, signature: ByteArray): Instant {
        val response = TimeStampResponse(bytes)
        if (response.status != 0 && response.status != 1) throw AttestationException("The time-stamp response was not granted")
        val token: TimeStampToken = response.timeStampToken ?: throw AttestationException("The time-stamp response holds no token")
        val info = token.timeStampInfo
        val time = info.genTime.toInstant()
        if (info.messageImprintAlgOID.id != SHA256_OID ||
            !MessageDigest.isEqual(info.messageImprintDigest, MessageDigest.getInstance("SHA-256").digest(signature))
        ) throw AttestationException("The time-stamp does not cover the envelope signature")
        // the token carries no certificate: the signer is a certificate of a time-stamping authority of the trusted root
        val authorities = root.timestampAuthorities.filter { it.validAt(time) }
        val signer = authorities.flatMap { it.certificates }.firstOrNull { token.sid.match(JcaX509CertificateHolder(it)) }
            ?: throw AttestationException("The time-stamp signer is not in the trusted root")
        try {
            token.validate(JcaSimpleSignerInfoVerifierBuilder().build(JcaX509CertificateHolder(signer)))
        } catch (e: Exception) {
            throw AttestationException("The time-stamp signature does not verify: ${e.message}", e)
        }
        val pool = authorities.flatMap { it.certificates }
        chain(signer, authorities, pool, time, "time-stamping")
        return time
    }

    private fun checkSigner(leaf: X509Certificate) {
        val uris = leaf.subjectAlternativeNames.orEmpty().filter { it[0] == SAN_URI }.map { it[1] as String }
        if (uris != listOf(GITHUB_RELEASES_SIGNER)) {
            throw AttestationException("The attestation is not signed by GitHub's release signer (certificate names $uris)")
        }
        if (leaf.basicConstraints != -1) throw AttestationException("The signing certificate is a CA")
        if (CODE_SIGNING_OID !in leaf.extendedKeyUsage.orEmpty()) throw AttestationException("The signing certificate lacks the code-signing usage")
        if (leaf.keyUsage?.getOrNull(0) != true) throw AttestationException("The signing certificate may not sign data")
    }

    private fun checkChain(leaf: X509Certificate, time: Instant) {
        val factory = root.certificateAuthorities.filter { it.validAt(time) }
        chain(leaf, factory, emptyList(), time, "certificate")
    }

    /** Builds a PKIX path from [leaf] to a self-signed certificate of one of [authorities], valid at [time]. */
    private fun chain(leaf: X509Certificate, authorities: List<Authority>, extra: List<X509Certificate>, time: Instant, what: String) {
        val certs = authorities.flatMap { it.certificates }
        val anchors = certs.filter { it.subjectX500Principal == it.issuerX500Principal }.map { TrustAnchor(it, null) }.toSet()
        if (anchors.isEmpty()) throw AttestationException("The trusted root has no $what authority valid at $time")
        val selector = X509CertSelector().apply { certificate = leaf }
        val params = PKIXBuilderParameters(anchors, selector).apply {
            isRevocationEnabled = false
            date = Date.from(time)
            addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(certs + extra + leaf)))
        }
        try {
            CertPathBuilder.getInstance("PKIX").build(params)
        } catch (e: java.security.GeneralSecurityException) {
            throw AttestationException("The $what chain does not lead to GitHub's trusted root at $time: ${e.message}", e)
        }
    }

    private fun statement(payload: ByteArray, expectedTag: String): AttestedRelease {
        val statement = try {
            json.readTree(payload)
        } catch (e: Exception) {
            throw AttestationException("The attested statement is not valid JSON", e)
        }
        if (statement.path("_type").asText() != "https://in-toto.io/Statement/v1") throw AttestationException("The statement is not an in-toto statement")
        if (!statement.path("predicateType").asText().startsWith("https://in-toto.io/attestation/release/")) {
            throw AttestationException("The statement is not a release attestation")
        }
        val predicate = statement.path("predicate")
        if (predicate.path("repository").asText() != repository) {
            throw AttestationException("The attestation is for '${predicate.path("repository").asText()}', not '$repository'")
        }
        if (predicate.path("tag").asText() != expectedTag) {
            throw AttestationException("The attestation is for tag '${predicate.path("tag").asText()}', not '$expectedTag'")
        }
        var commit: String? = null
        val assets = mutableMapOf<String, String>()
        for (subject in statement.path("subject")) {
            val digest = subject.path("digest")
            if (subject.path("uri").asText() == "pkg:github/$repository@$expectedTag") commit = digest.path("sha1").asText(null)
            val name = subject.path("name").asText(null)
            val sha256 = digest.path("sha256").asText(null)
            if (name != null && sha256 != null) assets[name] = sha256
        }
        return AttestedRelease(repository, expectedTag, commit, assets)
    }

    private fun b64(node: JsonNode, what: String): ByteArray =
        try {
            Base64.getDecoder().decode(node.asText(""))
        } catch (e: IllegalArgumentException) {
            throw AttestationException("$what is not valid base64", e)
        }
}
