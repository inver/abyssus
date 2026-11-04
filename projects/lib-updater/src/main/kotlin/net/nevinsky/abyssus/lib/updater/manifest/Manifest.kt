/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/** The manifest format this library reads. A manifest with another `schemaVersion` means a new installer is needed. */
const val SUPPORTED_SCHEMA_VERSION = 1

/** One file of a part: its path inside the game folder, size and SHA-256 (lower-case hex). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ManifestFile(val path: String, val size: Long, val sha256: String, val part: String)

/** One downloadable zip of a release: an asset name with its size and SHA-256 (lower-case hex). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ManifestPart(val name: String, val size: Long, val sha256: String)

/**
 * The signed description of a release. Not an Abyssus document: it has its own `schemaVersion`.
 * [installerDigest] covers the content an update cannot replace (see `design.md`, decision 2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Manifest(
    val schemaVersion: Int,
    val product: String,
    val version: String,
    val mainClass: String,
    val installerDigest: String,
    val parts: List<ManifestPart> = emptyList(),
    val files: List<ManifestFile> = emptyList(),
)

/** A manifest or its release could not be authenticated (no valid GitHub release attestation). */
class VerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The reason a manifest could not be bound; [newInstallerNeeded] is set for an unknown `schemaVersion`. */
class ManifestException(message: String, val newInstallerNeeded: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)

private val manifestMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

/** Reads the key and schema version first, so a newer schema is reported even when its other fields differ. */
fun bindManifest(bytes: ByteArray): Manifest {
    val schema = try {
        manifestMapper.readTree(bytes).path("schemaVersion").asInt(-1)
    } catch (e: Exception) {
        throw ManifestException("The manifest is not valid JSON", cause = e)
    }
    if (schema != SUPPORTED_SCHEMA_VERSION) {
        throw ManifestException("Manifest schemaVersion $schema is not supported: a new installer is needed", true)
    }
    try {
        return manifestMapper.readValue<Manifest>(bytes)
    } catch (e: Exception) {
        throw ManifestException("The manifest does not match the schema: ${e.message}", cause = e)
    }
}

/** The manifest as JSON text (used by the packaging tasks and tests). */
fun writeManifest(manifest: Manifest): ByteArray = manifestMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest)
