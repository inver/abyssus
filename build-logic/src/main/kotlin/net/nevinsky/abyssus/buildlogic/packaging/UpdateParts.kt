/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun sha256(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

/** `*` globs of `packaging/updater.properties`, matched against a jar's file name. */
internal fun globMatches(pattern: String, name: String): Boolean =
    Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) }).matches(name)

/** The jars of [lib] an update cannot replace: those carrying native code. */
internal fun installerOnlyJars(lib: File, patterns: List<String>): List<File> =
    lib.listFiles { f -> f.extension == "jar" }!!.filter { jar -> patterns.any { globMatches(it, jar.name) } }.sortedBy { it.name }

/**
 * SHA-256 over what an update cannot change (design decision 2): the sorted names and hashes of the installer-only jars,
 * the module list (comments and order ignored) with the JDK's version string, and the launcher protocol number. Not a hash
 * of the launcher jar, which changes on every build.
 */
fun installerDigest(installerOnly: List<File>, modulesText: String, jdkVersion: String, protocol: Int): String {
    val modules = modulesText.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.sorted()
    val text = buildString {
        installerOnly.sortedBy { it.name }.forEach { append("jar:").append(it.name).append(':').append(sha256(it)).append('\n') }
        append("modules:").append(modules.joinToString(",")).append('\n')
        append("jdk:").append(jdkVersion).append('\n')
        append("protocol:").append(protocol).append('\n')
    }
    return sha256(text.toByteArray())
}

abstract class WriteInstallerDigest : DefaultTask() {
    /** The distribution's `lib/`. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gameLib: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modules: RegularFileProperty

    @get:Input
    abstract val installerOnlyPatterns: ListProperty<String>

    @get:Input
    abstract val jdkVersion: Property<String>

    @get:Input
    abstract val protocol: Property<Int>

    @get:org.gradle.api.tasks.OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        val digest = installerDigest(
            installerOnlyJars(gameLib.get().asFile, installerOnlyPatterns.get()), modules.get().asFile.readText(), jdkVersion.get(), protocol.get()
        )
        output.get().asFile.also { it.parentFile.mkdirs() }.writeText(digest + "\n")
    }
}

/** One file of the manifest; [part] is the zip it comes in, or [INSTALLER_PART] for a file the installer owns. */
data class ManifestEntry(val path: String, val size: Long, val sha256: String, val part: String)

const val INSTALLER_PART = "installer"

private fun jsonString(text: String): String = buildString {
    append('"')
    for (c in text) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}

/** `manifest.json` (schemaVersion 1, read by `lib-updater`'s `bindManifest`). */
fun renderManifest(
    product: String, version: String, mainClass: String, installerDigest: String,
    parts: List<Triple<String, Long, String>>, files: List<ManifestEntry>,
): String = buildString {
    append("{\n")
    append("  \"schemaVersion\": 1,\n  \"product\": ${jsonString(product)},\n  \"version\": ${jsonString(version)},\n")
    append("  \"mainClass\": ${jsonString(mainClass)},\n  \"installerDigest\": ${jsonString(installerDigest)},\n")
    append("  \"parts\": [\n")
    append(parts.joinToString(",\n") { (n, s, h) -> "    {\"name\": ${jsonString(n)}, \"size\": $s, \"sha256\": ${jsonString(h)}}" })
    append("\n  ],\n  \"files\": [\n")
    append(files.joinToString(",\n") { "    {\"path\": ${jsonString(it.path)}, \"size\": ${it.size}, \"sha256\": ${jsonString(it.sha256)}, \"part\": ${jsonString(it.part)}}" })
    append("\n  ]\n}\n")
}

private fun zipOf(target: File, entries: List<Pair<String, File>>) {
    ZipOutputStream(target.outputStream().buffered()).use { zip ->
        for ((name, file) in entries.sortedBy { it.first }) {
            zip.putNextEntry(ZipEntry(name).apply { time = 0 }) // a fixed time keeps equal content equal bytes
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/**
 * Writes `game-code-<version>.zip` (the game's jars except the installer-only ones), `game-project-<version>.zip` (the
 * bundled project under `project/ControlLine/`) and the unsigned `manifest.json` into [out]. Returns the manifest file.
 * Installer-only jars are listed in the manifest with [INSTALLER_PART], so a complete version folder can be checked.
 */
fun buildUpdateParts(
    gameLib: File, projectDir: File, installerOnlyPatterns: List<String>, version: String, mainClass: String,
    digest: String, out: File,
): File {
    out.mkdirs()
    val installerOnly = installerOnlyJars(gameLib, installerOnlyPatterns).map { it.name }.toSet()
    val jars = gameLib.listFiles { f -> f.extension == "jar" }!!.sortedBy { it.name }
    val codeName = "game-code-$version.zip"
    val projectName = "game-project-$version.zip"
    val projectFiles = projectDir.walkTopDown().filter { it.isFile }.map { "project/ControlLine/" + it.relativeTo(projectDir).invariantSeparatorsPath to it }.toList()
    val codeFiles = jars.filter { it.name !in installerOnly }.map { it.name to it }
    zipOf(File(out, codeName), codeFiles)
    zipOf(File(out, projectName), projectFiles)
    val entries = codeFiles.map { (n, f) -> ManifestEntry(n, f.length(), sha256(f), codeName) } +
        projectFiles.map { (n, f) -> ManifestEntry(n, f.length(), sha256(f), projectName) } +
        jars.filter { it.name in installerOnly }.map { ManifestEntry(it.name, it.length(), sha256(it), INSTALLER_PART) }
    val parts = listOf(codeName, projectName).map { name -> File(out, name).let { Triple(name, it.length(), sha256(it)) } }
    val manifest = File(out, "manifest.json")
    manifest.writeText(renderManifest("control-line", version, mainClass, digest, parts, entries))
    return manifest
}

abstract class BuildUpdateParts : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gameLib: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundledProject: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val digestFile: RegularFileProperty

    @get:Input
    abstract val installerOnlyPatterns: ListProperty<String>

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun build() {
        output.get().asFile.deleteRecursively()
        val manifest = buildUpdateParts(
            gameLib.get().asFile, bundledProject.get().asFile, installerOnlyPatterns.get(), version.get(), mainClass.get(),
            digestFile.get().asFile.readText().trim(), output.get().asFile,
        )
        if (!manifest.isFile) throw GradleException("manifest.json was not written")
    }
}
