/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** A tool of the JDK at [home], such as `jdeps`, `jlink` or `jpackage`. */
internal fun jdkTool(home: String, name: String): String = java.io.File(home, "bin/$name").path

/**
 * Builds the bundled Java runtime with `jlink` from [modules], after checking that the list covers every module that
 * `jdeps` finds the shipped jars in [payload] using.
 */
abstract class JlinkRuntime @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val payload: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modules: RegularFileProperty

    /** The JDK whose `jdeps` and `jlink` run and whose modules the runtime is made of. */
    @get:Input
    abstract val jdkHome: Property<String>

    @get:OutputDirectory
    abstract val runtime: DirectoryProperty

    @TaskAction
    fun build() {
        val listed = modules.get().asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val home = jdkHome.get()
        // the natives jars hold no code, and jolt-jni ships demo packages jdeps cannot resolve
        val jars = payload.get().asFile
            .listFiles { f -> f.extension == "jar" && !f.name.contains("natives") && !f.name.startsWith("jolt-jni") }!!
            .sortedBy { it.name }.map { it.path }
        val found = ByteArrayOutputStream()
        exec.exec {
            commandLine(listOf(jdkTool(home, "jdeps"), "--print-module-deps", "--ignore-missing-deps", "--multi-release", "21") + jars)
            standardOutput = found
        }
        val needed = found.toString().trim().lineSequence().last().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val missing = needed.filter { it !in listed }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "packaging/modules.txt misses modules the shipped jars use: ${missing.joinToString()}. Add them to the file."
            )
        }
        val out = runtime.get().asFile
        out.deleteRecursively()
        exec.exec {
            commandLine(
                jdkTool(home, "jlink"), "--add-modules", listed.joinToString(","), "--strip-debug", "--no-header-files",
                "--no-man-pages", "--compress", "zip-6", "--output", out.path
            )
        }
    }
}
