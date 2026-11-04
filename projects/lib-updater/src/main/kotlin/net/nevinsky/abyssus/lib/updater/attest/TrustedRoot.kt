/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.attest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64

/** A certificate authority (or time-stamping authority) of a trusted root: its certificates and the period it was valid. */
class Authority(val validFrom: Instant, val validTo: Instant?, val certificates: List<X509Certificate>) {
    fun validAt(time: Instant) = !time.isBefore(validFrom) && (validTo == null || !time.isAfter(validTo))
}

/**
 * GitHub's Sigstore trusted root (`trusted_root.json` of `tuf-repo.github.com`): the certificate authorities that issue the
 * short-lived signing certificate of a release attestation and the time-stamping authorities that date it. A copy is bundled
 * with the library ([bundledTrustedRoot]); it changes only with a new installer.
 */
class TrustedRoot(val certificateAuthorities: List<Authority>, val timestampAuthorities: List<Authority>)

private val rootMapper = ObjectMapper()

/** Parses a Sigstore trusted root JSON document. */
fun parseTrustedRoot(json: ByteArray): TrustedRoot {
    val root = rootMapper.readTree(json)
    return TrustedRoot(authorities(root.path("certificateAuthorities")), authorities(root.path("timestampAuthorities")))
}

/** The trusted root shipped inside the library. */
fun bundledTrustedRoot(): TrustedRoot =
    parseTrustedRoot(TrustedRoot::class.java.getResourceAsStream("/github-trusted-root.json")!!.use { it.readAllBytes() })

private fun authorities(nodes: JsonNode): List<Authority> {
    val factory = CertificateFactory.getInstance("X.509")
    return nodes.map { node ->
        val valid = node.path("validFor")
        val certs = node.path("certChain").path("certificates").map {
            factory.generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(it.path("rawBytes").asText()))) as X509Certificate
        }
        Authority(
            Instant.parse(valid.path("start").asText()),
            valid.path("end").takeIf { !it.isMissingNode && !it.isNull }?.asText()?.let { Instant.parse(it) }, certs
        )
    }
}
