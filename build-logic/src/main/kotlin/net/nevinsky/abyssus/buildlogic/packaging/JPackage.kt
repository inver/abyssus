/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.buildlogic.packaging

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * One `jpackage` run. With [type] `app-image` it builds the application from [payload] on [runtime]; any other type
 * builds an installer from the `app-image` folder in [appImage], so every package of an OS holds the same payload.
 */
abstract class JPackage @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    /** The JDK whose `jpackage` runs. */
    @get:Input
    abstract val jdkHome: Property<String>

    /** `app-image`, `deb`, `rpm`, `msi` or `dmg`. */
    @get:Input
    abstract val type: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val vendor: Property<String>

    @get:Input
    abstract val appDescription: Property<String>

    /** The jars and the bundled project (an `app-image` build only). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val payload: DirectoryProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtime: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val mainJar: Property<String>

    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    /** Launcher JVM options (an `app-image` build only). */
    @get:Input
    abstract val javaOptions: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val icon: RegularFileProperty

    /** The `app-image` folder an installer is built from (not an `app-image` build). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appImage: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    /** Options only one OS or package type takes (`--linux-*`, `--win-*`, `--mac-*`). */
    @get:Input
    abstract val extraArgs: ListProperty<String>

    /** The installer's file name when it should not be jpackage's own (`Name-1.0.0.dmg`). */
    @get:Input
    @get:Optional
    abstract val outputName: Property<String>

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun build() {
        val dest = destination.get().asFile
        dest.deleteRecursively()
        val isImage = type.get() == "app-image"
        val cmd = mutableListOf(
            jdkTool(jdkHome.get(), "jpackage"), "--type", type.get(), "--name", appName.get(), "--app-version", appVersion.get(),
            "--vendor", vendor.get(), "--description", appDescription.get(), "--dest", dest.path
        )
        if (isImage) {
            check(payload.isPresent && runtime.isPresent && mainJar.isPresent && mainClass.isPresent) {
                "an app-image build needs payload, runtime, mainJar and mainClass"
            }
            cmd += listOf(
                "--input", payload.get().asFile.path, "--runtime-image", runtime.get().asFile.path,
                "--main-jar", mainJar.get(), "--main-class", mainClass.get()
            )
            javaOptions.get().forEach { cmd += listOf("--java-options", it) }
            if (icon.isPresent) cmd += listOf("--icon", icon.get().asFile.path)
        } else {
            check(appImage.isPresent) { "an installer build needs appImage" }
            cmd += listOf("--app-image", appImage.get().asFile.path)
            if (licenseFile.isPresent) cmd += listOf("--license-file", licenseFile.get().asFile.path)
        }
        cmd += extraArgs.get()
        exec.exec { commandLine(cmd) }
        if (!isImage && outputName.isPresent) {
            val made = dest.listFiles()?.singleOrNull { it.isFile }
                ?: throw GradleException("jpackage should have written exactly one file into $dest")
            made.renameTo(java.io.File(dest, outputName.get()))
        }
    }
}
