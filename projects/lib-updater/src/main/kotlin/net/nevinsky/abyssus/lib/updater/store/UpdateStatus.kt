/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path

/**
 * The result of the last check, for the player to read: the version running, the newest one seen, the one waiting for
 * the next start, and the [reason] when a check, download or verification failed or a new installer is needed
 * (with the [failedVersion] and [failedFile] of a verification failure).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StatusData(
    val checkedAt: String,
    val runningVersion: String,
    val newestVersion: String? = null,
    val waitingVersion: String? = null,
    val appliesAtNextStart: Boolean = false,
    val newInstallerNeeded: Boolean = false,
    val releaseUrl: String? = null,
    val reason: String? = null,
    val failedVersion: String? = null,
    val failedFile: String? = null,
)

/** `status.json` in the update folder. */
class UpdateStatus(private val file: Path) {
    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    fun write(data: StatusData) = writeAtomically(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data))

    fun read(): StatusData? = try {
        if (Files.isRegularFile(file)) mapper.readValue<StatusData>(Files.readAllBytes(file)) else null
    } catch (e: Exception) {
        null
    }
}
