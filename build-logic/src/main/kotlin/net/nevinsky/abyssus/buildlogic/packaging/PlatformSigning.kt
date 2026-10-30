/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/** Environment variable with the Windows signing command: a template with `{file}` where the file to sign goes. */
const val WINDOWS_SIGN_COMMAND_VARIABLE = "WINDOWS_SIGN_COMMAND"

/** [template] split on spaces, each `{file}` replaced by [file] as one argument. Fails when the template has no `{file}`. */
fun windowsSignCommand(template: String, file: File): List<String> {
    val parts = template.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.none { it.contains("{file}") }) throw GradleException("$WINDOWS_SIGN_COMMAND_VARIABLE has no {file} placeholder")
    return parts.map { it.replace("{file}", file.path) }
}

/** The files a Windows app-image carries that Authenticode can sign: the launcher and its DLLs. */
fun windowsSignableFiles(appImage: File): List<File> =
    appImage.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("exe", "dll") }.sortedBy { it.path }.toList()

/**
 * Signs [files] by running the command template of `WINDOWS_SIGN_COMMAND` once per file (the route is a CI choice: a local
 * `signtool` with a timestamp server, or a client of a signing service). Fails when the variable is missing, so a release
 * never publishes an unsigned file; builds that are not releases never run this task.
 */
abstract class SignWindowsFiles @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val files: ConfigurableFileCollection

    @TaskAction
    fun sign() {
        val template = System.getenv(WINDOWS_SIGN_COMMAND_VARIABLE)?.takeIf { it.isNotBlank() }
            ?: throw GradleException("The Windows signing step cannot run: $WINDOWS_SIGN_COMMAND_VARIABLE is not set")
        files.files.sortedBy { it.path }.forEach { file -> exec.exec { commandLine(windowsSignCommand(template, file)) } }
    }
}

/** Builds the arguments of `xcrun notarytool submit`; the credentials come from the environment, never from the build script. */
fun notarizeCommand(dmg: File, environment: Map<String, String>): List<String> {
    val missing = listOf("APPLE_ID", "APPLE_TEAM_ID", "APPLE_APP_PASSWORD").filter { environment[it].isNullOrBlank() }
    if (missing.isNotEmpty()) throw GradleException("The macOS notarization step cannot run: ${missing.joinToString()} not set")
    return listOf(
        "xcrun", "notarytool", "submit", dmg.path, "--apple-id", environment.getValue("APPLE_ID"), "--team-id", environment.getValue("APPLE_TEAM_ID"),
        "--password", environment.getValue("APPLE_APP_PASSWORD"), "--wait", "--timeout", "30m",
    )
}

/** Submits the signed `.dmg` to Apple, waits for the verdict and staples the ticket. A rejection fails the build. */
abstract class NotarizeDmg @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dmg: RegularFileProperty

    @get:Input
    abstract val retries: Property<Int>

    @TaskAction
    fun notarize() {
        val file = dmg.get().asFile
        val command = notarizeCommand(file, System.getenv())
        var attempt = 0
        while (true) {
            attempt++
            val result = exec.exec { commandLine(command); isIgnoreExitValue = true }
            if (result.exitValue == 0) break
            if (attempt > retries.get()) throw GradleException("Apple's notarization of ${file.name} failed (exit ${result.exitValue}); see the log above for the rejection")
            logger.warn("Notarization attempt $attempt failed; retrying")
        }
        exec.exec { commandLine("xcrun", "stapler", "staple", file.path) }
    }
}
