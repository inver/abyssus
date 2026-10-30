/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Downloads [url] into [output] and fails, deleting the file, unless its SHA-256 is [sha256]. A file already there with that hash is kept. */
abstract class DownloadVerified : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun download() {
        val file = output.get().asFile
        if (file.isFile && hashOf(file) == sha256.get()) return
        file.parentFile.mkdirs()
        URI(url.get()).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        val actual = hashOf(file)
        if (actual != sha256.get()) {
            file.delete()
            throw GradleException("${url.get()} has SHA-256 $actual, expected ${sha256.get()}; the file was deleted")
        }
        file.setExecutable(true)
    }

    private fun hashOf(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
