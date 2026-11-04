/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private val PACKAGE_EXTENSIONS = setOf("deb", "rpm", "AppImage", "msi", "dmg")

/** `sha256sum`-format lines (`<hash>  <name>`, sorted by name) for the installers in [folder]. */
fun checksumLines(folder: File): List<String> =
    folder.listFiles { f -> f.isFile && f.extension in PACKAGE_EXTENSIONS }!!.sortedBy { it.name }.map { "${sha256(it)}  ${it.name}" }

/** Writes `SHA256SUMS` into the release folder, naming every installer in it (the Linux packages are covered by its signature). */
abstract class WriteChecksums : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseDir: DirectoryProperty

    @TaskAction
    fun write() {
        val folder = releaseDir.get().asFile
        val lines = checksumLines(folder)
        if (lines.isEmpty()) throw org.gradle.api.GradleException("No installer (deb, rpm, AppImage, msi, dmg) found in $folder")
        File(folder, "SHA256SUMS").writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
